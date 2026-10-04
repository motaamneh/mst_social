package com.motaamneh.mstsocial.core.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Plaintext code is returned only from creation. Never log or persist this response. */
public record VerificationCreated(UUID requestId, String code, Instant expiresAt) {
    public VerificationCreated {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    @Override
    public String toString() {
        return "VerificationCreated[requestId=" + requestId + ", code=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
