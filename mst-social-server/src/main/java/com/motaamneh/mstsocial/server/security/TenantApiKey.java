package com.motaamneh.mstsocial.server.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable stored credential metadata. Contains a digest, never the plaintext secret. */
@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class TenantApiKey {
    @ToString.Include
    private final UUID id;
    private final UUID tenantId;
    private final String name;
    @Getter(AccessLevel.NONE)
    private final byte[] secretDigest;
    private final Instant createdAt;
    //Null when no expiration is configured
    private final Instant expiresAt;
    private final Instant revokedAt;

    public TenantApiKey(
            UUID id,
            UUID tenantId,
            String name,
            byte[] secretDigest,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;

        Objects.requireNonNull(secretDigest, "secretDigest must not be null");
        if (secretDigest.length != 32) {
            throw new IllegalArgumentException("secretDigest must contain 32 bytes");
        }
        this.secretDigest = secretDigest.clone();

        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public boolean isActive(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return revokedAt == null
                && !now.isBefore(createdAt)
                && (expiresAt == null || now.isBefore(expiresAt));
    }

    public byte[] getSecretDigest() {
        return secretDigest.clone();
    }
}
