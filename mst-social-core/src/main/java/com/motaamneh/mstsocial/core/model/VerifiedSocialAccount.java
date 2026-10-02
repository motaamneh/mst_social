package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/** A verified account association with a fixed validity period and optional revocation. */
public final class VerifiedSocialAccount {

    private final UUID id;
    private final UUID tenantId;
    private final String subjectId;
    private final Platform platform;
    private final String canonicalHandle;
    private final String providerAccountId;
    private final String evidenceProvider;
    private final Instant verifiedAt;
    private final Instant validUntil;

    private Instant revokedAt;
    private long version;

    public VerifiedSocialAccount(
            UUID id,
            UUID tenantId,
            String subjectId,
            Platform platform,
            String canonicalHandle,
            String providerAccountId,
            String evidenceProvider,
            Instant verifiedAt,
            VerificationPolicy policy
    ) {
        Objects.requireNonNull(policy, "policy must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        this.canonicalHandle = requireNonBlank(canonicalHandle, "canonicalHandle");
        this.providerAccountId = providerAccountId == null
                ? null : requireNonBlank(providerAccountId, "providerAccountId");
        this.evidenceProvider = requireNonBlank(evidenceProvider, "evidenceProvider");
        this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        this.validUntil = verifiedAt.atZone(ZoneOffset.UTC)
                .plusYears(policy.verifiedAccountLifetimeYears())
                .toInstant();
    }

    /** Validity includes verifiedAt and excludes validUntil. Revocation ends it immediately. */
    public boolean isActive(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return revokedAt == null && !now.isBefore(verifiedAt) && now.isBefore(validUntil);
    }

    /** Repeated revocation preserves the original timestamp and version. */
    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(verifiedAt)) {
            throw new IllegalArgumentException("now must not be before verifiedAt");
        }
        if (revokedAt == null) {
            this.revokedAt = now;
            this.version++;
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getCanonicalHandle() {
        return canonicalHandle;
    }

    /** Returns null when the evidence provider does not supply a stable account ID. */
    public String getProviderAccountId() {
        return providerAccountId;
    }

    public String getEvidenceProvider() {
        return evidenceProvider;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    /** Returns null until the account association is revoked. */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    public long getVersion() {
        return version;
    }
}
