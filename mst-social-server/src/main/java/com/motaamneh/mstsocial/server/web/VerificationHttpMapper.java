package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.model.ProfileFailureReason;
import com.motaamneh.mstsocial.core.service.VerificationResult;
import com.motaamneh.mstsocial.server.web.dto.response.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Duration;

@Component
public final class VerificationHttpMapper {
    public ResponseEntity<?> toResponse(VerificationResult result) {
        VerificationResponse verification = VerificationResponse.from(result.verification());
        if (result.account() != null) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new VerificationAttemptResponse(result.outcome(), verification,
                            VerifiedAccountResponse.from(result.account())));
        }

        HttpStatus status = switch (result.outcome()) {
            case IN_PROGRESS, STALE_ATTEMPT, ACCOUNT_ALREADY_LINKED, CANCELED, LOCKED -> HttpStatus.CONFLICT;
            case CODE_NOT_FOUND -> HttpStatus.UNPROCESSABLE_CONTENT;
            case EXPIRED -> HttpStatus.GONE;
            case PROVIDER_FAILURE -> providerStatus(result.providerFailure().reason());
            case VERIFIED, ALREADY_VERIFIED -> throw new IllegalStateException("Successful result requires an account");
        };
        String detail = switch (result.outcome()) {
            case IN_PROGRESS -> "A verification attempt is already running. Read the status before trying again.";
            case STALE_ATTEMPT -> "This attempt no longer owns the request. Read its current status.";
            case CODE_NOT_FOUND -> "The verification code was not found in the profile bio.";
            case ACCOUNT_ALREADY_LINKED -> "The social account has a conflicting active link in this application.";
            case EXPIRED -> "This verification request has expired. Create a new request.";
            case CANCELED -> "This verification request was canceled.";
            case LOCKED -> "This verification request has reached its failed-attempt limit.";
            case PROVIDER_FAILURE -> "The profile provider could not supply usable profile evidence.";
            case VERIFIED, ALREADY_VERIFIED -> throw new IllegalStateException("Unexpected successful outcome");
        };
        ProviderFailureResponse failure = null;
        Long retryAfter = null;
        if (result.providerFailure() != null) {
            var source = result.providerFailure();
            retryAfter = source.retryAfter() == null ? null : retryAfterSeconds(source.retryAfter());
            failure = new ProviderFailureResponse(source.reason(), source.retryable(), retryAfter);
        }
        var builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore());
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, retryAfter.toString());
        }
        return builder.body(new ApiErrorResponse(URI.create("about:blank"), status.getReasonPhrase(),
                status.value(), result.outcome().name(), detail, null, verification, failure));
    }

    private static HttpStatus providerStatus(ProfileFailureReason reason) {
        return switch (reason) {
            case PROFILE_NOT_FOUND, PROFILE_UNAVAILABLE, UNSUPPORTED_PLATFORM -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case PROVIDER_AUTHENTICATION_FAILED, INVALID_PROVIDER_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_RATE_LIMITED, PROVIDER_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static long retryAfterSeconds(Duration duration) {
        // Ceiling seconds, bounded to one day, without overflowing a very large duration.
        if (duration.getSeconds() >= 86_400) {
            return 86_400;
        }
        return Math.max(1, duration.getSeconds() + (duration.getNano() == 0 ? 0 : 1));
    }
}
