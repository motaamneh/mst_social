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


    /** A detached persistence value. It does not perform lifecycle transitions. */
    public record Snapshot(
            UUID id,
            UUID tenantId,
            String subjectId,
            Platform platform,
            String canonicalHandle,
            String providerAccountId,
            String evidenceProvider,
            Instant verifiedAt,
            Instant validUntil,
            Instant revokedAt,
            long version
    ) {
        public Snapshot {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            requireNonBlank(subjectId, "subjectId");
            Objects.requireNonNull(platform, "platform must not be null");
            requireNonBlank(canonicalHandle, "canonicalHandle");
            if (providerAccountId != null) {
                requireNonBlank(providerAccountId, "providerAccountId");
            }
            requireNonBlank(evidenceProvider, "evidenceProvider");
            Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
            Objects.requireNonNull(validUntil, "validUntil must not be null");
            if (!validUntil.isAfter(verifiedAt) || version < 0
                    || (revokedAt != null && revokedAt.isBefore(verifiedAt))) {
                throw new IllegalArgumentException("Invalid saved account state");
            }
        }

        @Override
        public String toString() {
            return "VerifiedSocialAccount.Snapshot[redacted]";
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(id, tenantId, subjectId, platform, canonicalHandle, providerAccountId, evidenceProvider, verifiedAt, validUntil, revokedAt, version);
    }

    /** Restores exact saved state, including historical deadlines and version. */
    public static VerifiedSocialAccount restore(Snapshot snapshot) {
        return new VerifiedSocialAccount(Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    private VerifiedSocialAccount(Snapshot snapshot) {
        this.id = snapshot.id();
        this.tenantId = snapshot.tenantId();
        this.subjectId = snapshot.subjectId();
        this.platform = snapshot.platform();
        this.canonicalHandle = snapshot.canonicalHandle();
        this.providerAccountId = snapshot.providerAccountId();
        this.evidenceProvider = snapshot.evidenceProvider();
        this.verifiedAt = snapshot.verifiedAt();
        this.validUntil = snapshot.validUntil();
        this.revokedAt = snapshot.revokedAt();
        this.version = snapshot.version();
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
