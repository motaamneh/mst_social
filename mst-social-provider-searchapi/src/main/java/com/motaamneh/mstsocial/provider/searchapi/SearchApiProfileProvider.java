package com.motaamneh.mstsocial.provider.searchapi;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.ProfileFailureReason;
import com.motaamneh.mstsocial.core.model.ProfileFetchResult;
import com.motaamneh.mstsocial.core.port.ProfileProvider;
import com.motaamneh.mstsocial.core.validation.HandleNormalizer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Reusable provider; close when the owning application shuts down. No automatic retries. */
public final class SearchApiProfileProvider implements ProfileProvider, AutoCloseable {
    private final HttpClient client;
    private final SearchApiSettings settings;
    private final SearchApiProfileParser parser;
    private final HandleNormalizer normalizer;
    private final Clock clock;
    private final String apiKey;
    public SearchApiProfileProvider(
            String apiKey,
            SearchApiSettings settings,
            SearchApiProfileParser parser,
            HandleNormalizer normalizer,
            Clock clock
    ){
        if(apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (apiKey.chars().anyMatch(c -> c <= 32 || c >= 127)) {
            throw new IllegalArgumentException("apiKey contains invalid header characters");
        }
        this.apiKey= apiKey;
        this.settings = Objects.requireNonNull(
                settings, "settings must not be null"
        );
        this.parser = Objects.requireNonNull(
                parser, "parser must not be null"
        );
        this.normalizer = Objects.requireNonNull(
                normalizer, "normalizer must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock, "clock must not be null"
        );

        this.client = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

    }


    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM || platform == Platform.TIKTOK;
    }

    private static String engineFor(Platform platform) {
        return switch (platform){
            case INSTAGRAM -> "instagram_profile";
            case TIKTOK -> "tiktok_profile";
            case X, FACEBOOK -> throw new UnsupportedOperationException("UNSUPPORTED_PLATFORM");
        };
    }

    private HttpRequest buildRequest(
            Platform platform,
            String normalizedHandle
    ){
        String encodedHandle = URLEncoder.encode(normalizedHandle, StandardCharsets.UTF_8);
        URI uri = URI.create(
                "https://www.searchapi.io/api/v1/search"
                        + "?engine=" + engineFor(platform)
                        + "&username=" + encodedHandle
        );
        return HttpRequest.newBuilder(uri)
                .timeout(settings.requestTimeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private static ProfileFetchResult.Failed failure(
            ProfileFailureReason reason,
            boolean retryable
    ) {
        return new ProfileFetchResult.Failed(reason, retryable, null);
    }

    @Override
    public ProfileFetchResult fetchProfile(Platform platform, String normalizedHandle) {
        Objects.requireNonNull(platform, "platform must not be null");
        if (!supports(platform)) {
            return failure(ProfileFailureReason.UNSUPPORTED_PLATFORM, false);
        }
        String handle = normalizer.normalize(platform, normalizedHandle);
        HttpRequest request = buildRequest(platform, handle);
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber(settings.maxResponseBytes());
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> pending =
                client.sendAsync(request, info -> subscriber);
        try {
            long remaining = timeoutNanos(settings.requestTimeout()) - (System.nanoTime() - started);
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            // The subscriber completes only after the entire bounded body arrives.
            HttpResponse<byte[]> response = pending.get(remaining, TimeUnit.NANOSECONDS);
            return handleResponse(response, clock.instant());
        } catch (TimeoutException exception) {
            return failure(ProfileFailureReason.PROVIDER_TIMEOUT, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(ProfileFailureReason.PROVIDER_ERROR, false);
        } catch (CancellationException exception) {
            return failure(ProfileFailureReason.PROVIDER_ERROR, false);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof LimitedBodySubscriber.ResponseTooLargeException) {
                    return failure(ProfileFailureReason.INVALID_PROVIDER_RESPONSE, false);
                }
                if (cause instanceof HttpTimeoutException) {
                    return failure(ProfileFailureReason.PROVIDER_TIMEOUT, true);
                }
                if (cause == cause.getCause()) {
                    break;
                }
                cause = cause.getCause();
            }
            return failure(ProfileFailureReason.PROVIDER_ERROR, true);
        } finally {
            // Also handles timeout/interruption before onSubscribe is called.
            subscriber.cancel();
            pending.cancel(true);
        }
    }

    private ProfileFetchResult handleResponse(HttpResponse<byte[]> response, Instant fetchedAt) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return failure(ProfileFailureReason.PROVIDER_AUTHENTICATION_FAILED, false);
        }
        if (status == 429) {
            return new ProfileFetchResult.Failed(ProfileFailureReason.PROVIDER_RATE_LIMITED,
                    true, retryAfter(response, fetchedAt));
        }
        if (status == 408 || status == 504) {
            return failure(ProfileFailureReason.PROVIDER_TIMEOUT, true);
        }
        if (status >= 500 && status <= 599) {
            return new ProfileFetchResult.Failed(ProfileFailureReason.PROVIDER_ERROR,
                    true, retryAfter(response, fetchedAt));
        }
        if (status != 200) {
            // A generic HTTP 404 is not proof that the social profile is missing.
            return failure(ProfileFailureReason.PROVIDER_ERROR, false);
        }
        return parser.parse(response.body(), fetchedAt);
    }

    private static Duration retryAfter(HttpResponse<?> response, Instant now) {
        String value = response.headers().firstValue("Retry-After").orElse("").strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            try {
                long seconds = Long.parseLong(value);
                return seconds > 0 ? Duration.ofSeconds(seconds) : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        try {
            Instant deadline = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return deadline.isAfter(now) ? Duration.between(now, deadline) : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
