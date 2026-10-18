package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.VerificationStatus;
import com.motaamneh.mstsocial.core.service.VerificationView;
import java.time.Instant;
import java.util.UUID;

/** Public status contains no digest, key version, lease, biography, or plaintext code. */
public record VerificationResponse(
        UUID requestId, String subjectId, Platform platform, String normalizedHandle,
        VerificationStatus status, int failedAttempts, int maxFailedAttempts,
        Instant createdAt, Instant expiresAt, Instant verifiedAt, Instant canceledAt
) {
    public static VerificationResponse from(VerificationView view) {
        return new VerificationResponse(view.requestId(), view.subjectId(), view.platform(),
                view.normalizedHandle(), view.status(), view.failedAttempts(), view.maxFailedAttempts(),
                view.createdAt(), view.expiresAt(), view.verifiedAt(), view.canceledAt());
    }
}
