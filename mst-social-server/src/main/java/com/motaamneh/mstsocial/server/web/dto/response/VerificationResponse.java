package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.VerificationStatus;
import com.motaamneh.mstsocial.core.service.VerificationView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Public status contains no digest, key version, lease, biography, or plaintext code. */
@Schema(description = "Current verification state without the code, digest, bio, or internal attempt lease.",
        example = """
                {
                  "requestId": "00000000-0000-4000-8000-000000000001",
                  "subjectId": "user-123",
                  "platform": "INSTAGRAM",
                  "normalizedHandle": "your_username",
                  "status": "PENDING",
                  "failedAttempts": 0,
                  "maxFailedAttempts": 10,
                  "createdAt": "2026-10-18T12:00:00Z",
                  "expiresAt": "2026-10-18T12:15:00Z",
                  "verifiedAt": null,
                  "canceledAt": null
                }
                """)
public record VerificationResponse(
        @Schema(description = "Verification request UUID.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "00000000-0000-4000-8000-000000000001")
        UUID requestId,
        @Schema(description = "User ID in the consuming application.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "user-123")
        String subjectId,
        @Schema(description = "Social platform.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "INSTAGRAM")
        Platform platform,
        @Schema(description = "Validated normalized requested username.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "your_username")
        String normalizedHandle,
        @Schema(description = "Current state; mismatch 10 locks the request under the default policy.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "PENDING")
        VerificationStatus status,
        @Schema(description = "Number of bio mismatches; provider failures do not increase this count.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "0",
                minimum = "0")
        int failedAttempts,
        @Schema(description = "Mismatch limit; default is 10.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "10",
                minimum = "1")
        int maxFailedAttempts,
        @Schema(description = "Creation time in UTC.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-18T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Verification deadline (default: 15 minutes after creation).",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-18T12:15:00Z")
        Instant expiresAt,
        @Schema(description = "Successful verification time; null on an unverified request.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-18T12:02:00Z",
                types = {"string", "null"})
        Instant verifiedAt,
        @Schema(description = "Cancellation time, or null if not canceled.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        Instant canceledAt
) {
    public static VerificationResponse from(VerificationView view) {
        return new VerificationResponse(view.requestId(), view.subjectId(), view.platform(),
                view.normalizedHandle(), view.status(), view.failedAttempts(), view.maxFailedAttempts(),
                view.createdAt(), view.expiresAt(), view.verifiedAt(), view.canceledAt());
    }
}
