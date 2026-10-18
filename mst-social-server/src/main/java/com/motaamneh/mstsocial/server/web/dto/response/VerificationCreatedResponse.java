package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.service.VerificationCreated;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "New verification. The code appears only in this response; save it securely until it is placed in the bio.")
public record VerificationCreatedResponse(
        @Schema(description = "Verification request UUID.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "00000000-0000-4000-8000-000000000001")
        UUID requestId,
        @Schema(description = "Exact verification marker returned once. Example is illustrative, not a usable verification code.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "mst-ig-EXAMPLE_ONLY")
        String code,
        @Schema(description = "Verification deadline (default: 15 minutes after creation).",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-18T12:15:00Z")
        Instant expiresAt,
        @Schema(description = "Instructions for the user.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Add this exact code to your social profile bio, then submit a verification attempt before expiry.")
        String instructions
) {
    public static VerificationCreatedResponse from(VerificationCreated created) {
        return new VerificationCreatedResponse(created.requestId(), created.code(), created.expiresAt(),
                "Add this exact code to your social profile bio, then submit a verification attempt before expiry.");
    }

    @Override
    public String toString() {
        return "VerificationCreatedResponse[requestId=" + requestId + ", code=REDACTED]";
    }
}
