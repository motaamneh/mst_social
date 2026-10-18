package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.service.VerificationOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful verification, including the associated account. Failed attempts use ApiErrorResponse.",
        example = """
                {
                  "outcome": "VERIFIED",
                  "verification": {
                    "requestId": "00000000-0000-4000-8000-000000000001",
                    "subjectId": "user-123",
                    "platform": "INSTAGRAM",
                    "normalizedHandle": "your_username",
                    "status": "VERIFIED",
                    "failedAttempts": 0,
                    "maxFailedAttempts": 10,
                    "createdAt": "2026-10-18T12:00:00Z",
                    "expiresAt": "2026-10-18T12:15:00Z",
                    "verifiedAt": "2026-10-18T12:02:00Z",
                    "canceledAt": null
                  },
                  "account": {
                    "accountId": "00000000-0000-4000-8000-000000000002",
                    "subjectId": "user-123",
                    "platform": "INSTAGRAM",
                    "canonicalHandle": "your_username",
                    "providerAccountId": null,
                    "evidenceProvider": "searchapi",
                    "verifiedAt": "2026-10-18T12:02:00Z",
                    "validUntil": "2027-10-18T12:02:00Z",
                    "revokedAt": null,
                    "active": true
                  }
                }
                """)
public record VerificationAttemptResponse(
        @Schema(description = "Successful outcomes only; failures use the problem response.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "VERIFIED",
                implementation = String.class,
                allowableValues = {"VERIFIED", "ALREADY_VERIFIED"})
        VerificationOutcome outcome,
        @Schema(description = "Current verification state.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        VerificationResponse verification,
        @Schema(description = "Associated social identity. Check active even for ALREADY_VERIFIED.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        VerifiedAccountResponse account
) {
}
