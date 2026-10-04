package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable public status: no digest, key version, lease ID, or biography. */
public record VerificationView(
        UUID requestId, String subjectId, Platform platform, String normalizedHandle,
        VerificationStatus status, int failedAttempts, int maxFailedAttempts,
        Instant createdAt, Instant expiresAt, Instant verifiedAt, Instant canceledAt
) {
    public static VerificationView from(VerificationRequest request) {
        return new VerificationView(request.getId(), request.getSubjectId(), request.getPlatform(),
                request.getNormalizedHandle(), request.getStatus(), request.getFailedAttempts(),
                request.getMaxFailedAttempts(), request.getCreatedAt(), request.getExpiresAt(),
                request.getVerifiedAt(), request.getCanceledAt());
    }
}
