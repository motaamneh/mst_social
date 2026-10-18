package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.service.VerificationCreated;
import java.time.Instant;
import java.util.UUID;

public record VerificationCreatedResponse(UUID requestId, String code, Instant expiresAt, String instructions) {
    public static VerificationCreatedResponse from(VerificationCreated created) {
        return new VerificationCreatedResponse(created.requestId(), created.code(), created.expiresAt(),
                "Add this exact code to your social profile bio, then submit a verification attempt before expiry.");
    }

    @Override
    public String toString() {
        return "VerificationCreatedResponse[requestId=" + requestId + ", code=REDACTED]";
    }
}
