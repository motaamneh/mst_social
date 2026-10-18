package com.motaamneh.mstsocial.core.service;

import java.util.Objects;

import com.motaamneh.mstsocial.core.model.*;
import java.time.Instant;
import java.util.UUID;

/** active is evaluated at the time of the service call. */
public final class VerifiedAccountView {
    private final UUID accountId;
    private final String subjectId;
    private final Platform platform;
    private final String canonicalHandle;
    private final String providerAccountId;
    private final String evidenceProvider;
    private final Instant verifiedAt;
    private final Instant validUntil;
    private final Instant revokedAt;
    private final boolean active;

    public VerifiedAccountView(
            UUID accountId,
            String subjectId,
            Platform platform,
            String canonicalHandle,
            String providerAccountId,
            String evidenceProvider,
            Instant verifiedAt,
            Instant validUntil,
            Instant revokedAt,
            boolean active
    ) {
        this.accountId = accountId;
        this.subjectId = subjectId;
        this.platform = platform;
        this.canonicalHandle = canonicalHandle;
        this.providerAccountId = providerAccountId;
        this.evidenceProvider = evidenceProvider;
        this.verifiedAt = verifiedAt;
        this.validUntil = validUntil;
        this.revokedAt = revokedAt;
        this.active = active;
    }

    public static VerifiedAccountView from(VerifiedSocialAccount account, Instant now) {
        return new VerifiedAccountView(account.getId(), account.getSubjectId(), account.getPlatform(),
                account.getCanonicalHandle(), account.getProviderAccountId(), account.getEvidenceProvider(),
                account.getVerifiedAt(), account.getValidUntil(), account.getRevokedAt(), account.isActive(now));
    }

    public UUID accountId() {
        return accountId;
    }

    public String subjectId() {
        return subjectId;
    }

    public Platform platform() {
        return platform;
    }

    public String canonicalHandle() {
        return canonicalHandle;
    }

    public String providerAccountId() {
        return providerAccountId;
    }

    public String evidenceProvider() {
        return evidenceProvider;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public Instant validUntil() {
        return validUntil;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerifiedAccountView other)) {
            return false;
        }
        return Objects.equals(accountId, other.accountId)
                && Objects.equals(subjectId, other.subjectId)
                && Objects.equals(platform, other.platform)
                && Objects.equals(canonicalHandle, other.canonicalHandle)
                && Objects.equals(providerAccountId, other.providerAccountId)
                && Objects.equals(evidenceProvider, other.evidenceProvider)
                && Objects.equals(verifiedAt, other.verifiedAt)
                && Objects.equals(validUntil, other.validUntil)
                && Objects.equals(revokedAt, other.revokedAt)
                && Objects.equals(active, other.active);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, subjectId, platform, canonicalHandle, providerAccountId, evidenceProvider, verifiedAt, validUntil, revokedAt, active);
    }

    @Override
    public String toString() {
        return "VerifiedAccountView[accountId=" + accountId + ", subjectId=" + subjectId + ", platform=" + platform + ", canonicalHandle=" + canonicalHandle + ", providerAccountId=" + providerAccountId + ", evidenceProvider=" + evidenceProvider + ", verifiedAt=" + verifiedAt + ", validUntil=" + validUntil + ", revokedAt=" + revokedAt + ", active=" + active + "]";
    }
}
