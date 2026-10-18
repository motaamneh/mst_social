package com.motaamneh.mstsocial.core.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Plaintext code is returned only from creation. Never log or persist this response. */
public final class VerificationCreated {
    private final UUID requestId;
    private final String code;
    private final Instant expiresAt;

    public VerificationCreated(UUID requestId, String code, Instant expiresAt) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.requestId = requestId;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "VerificationCreated[requestId=" + requestId + ", code=REDACTED, expiresAt=" + expiresAt + "]";
    }

    public UUID requestId() {
        return requestId;
    }

    public String code() {
        return code;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationCreated other)) {
            return false;
        }
        return Objects.equals(requestId, other.requestId)
                && Objects.equals(code, other.code)
                && Objects.equals(expiresAt, other.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, code, expiresAt);
    }
}
