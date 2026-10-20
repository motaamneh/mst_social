package com.motaamneh.mstsocial.server.observability;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.ProfileFetchResult;
import com.motaamneh.mstsocial.core.port.ProfileProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Server-side decorator: keeps logging dependencies out of the core and provider libraries. */
@Slf4j
public final class LoggingProfileProvider implements ProfileProvider {
    private final ProfileProvider delegate;

    public LoggingProfileProvider(ProfileProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public boolean supports(Platform platform) {
        return delegate.supports(platform);
    }

    @Override
    public ProfileFetchResult fetchProfile(Platform platform, String normalizedHandle) {
        long startedAt = System.nanoTime();
        log.atDebug().addKeyValue("event", "provider.fetch.started")
                .addKeyValue("provider", "searchapi").addKeyValue("platform", platform)
                .log("Profile fetch started");
        try {
            ProfileFetchResult result = Objects.requireNonNull(delegate.fetchProfile(platform, normalizedHandle));
            var event = result instanceof ProfileFetchResult.Failed ? log.atWarn() : log.atInfo();
            event.addKeyValue("event", "provider.fetch.completed").addKeyValue("provider", "searchapi")
                    .addKeyValue("platform", platform)
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            if (result instanceof ProfileFetchResult.Failed failure) {
                event.addKeyValue("outcome", "FAILED").addKeyValue("reason", failure.reason())
                        .addKeyValue("retryable", failure.retryable())
                        .addKeyValue("retryAfter", failure.retryAfter());
            } else {
                event.addKeyValue("outcome", "FOUND");
            }
            event.log("Profile fetch completed");
            return result;
        } catch (RuntimeException exception) {
            SafeExceptionDetails.addTo(log.atError(), exception)
                    .addKeyValue("event", "provider.fetch.failed").addKeyValue("provider", "searchapi")
                    .addKeyValue("platform", platform)
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                    .log("Unexpected profile fetch failure");
            throw exception;
        }
    }
}
