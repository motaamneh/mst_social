package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A temporary verification request. Callers supply time from their application clock.
 * Storage must enforce tenant isolation, atomic completion, and concurrency control.
 */
public final class VerificationRequest {
    private final UUID id;
    private final UUID tenantId;
    private final String subjectId;
    private final Platform platform;
    private final String normalizedHandle;
    private final byte[] codeDigest;
    private final String codeKeyVersion;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final int maxFailedAttempts;
    private VerificationStatus status;
    private int failedAttempts;
    private Instant verifiedAt;
    private Instant canceledAt;
    private long version;

    public VerificationRequest(
            UUID id,
            UUID tenantId,
            String subjectId,
            Platform platform,
            String normalizedHandle,
            byte[] codeDigest,
            String codeKeyVersion,
            Instant createdAt,
            VerificationPolicy policy
    ) {
        Objects.requireNonNull(policy, "policy must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(
                tenantId,
                "tenantId must not be null"
        );
        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.platform = Objects.requireNonNull(
                platform,
                "platform must not be null"
        );
        this.normalizedHandle = requireNonBlank(
                normalizedHandle,
                "normalizedHandle"
        );

        Objects.requireNonNull(codeDigest, "codeDigest must not be null");

        if (codeDigest.length != 32) {
            throw new IllegalArgumentException(
                    "codeDigest must contain 32 bytes"
            );
        }

        this.codeDigest = codeDigest.clone();
        this.codeKeyVersion = requireNonBlank(
                codeKeyVersion,
                "codeKeyVersion"
        );

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        this.expiresAt = createdAt.plus(policy.requestLifetime());
        this.maxFailedAttempts = policy.maxFailedAttempts();

        this.status = VerificationStatus.PENDING;
        this.failedAttempts = 0;
        this.version = 0;
    }

    public void beginVerification(Instant now) {
        requireStatus(VerificationStatus.PENDING);
        requireBeforeExpiry(now);
        changeStatus(VerificationStatus.VERIFYING);
    }

    public void markVerified(Instant now) {
        requireStatus(VerificationStatus.VERIFYING);
        requireBeforeExpiry(now);
        this.verifiedAt = now;
        changeStatus(VerificationStatus.VERIFIED);
    }

    public void recordMismatch(Instant now) {
        requireStatus(VerificationStatus.VERIFYING);
        requireBeforeExpiry(now);
        this.failedAttempts++;
        if (this.failedAttempts >= this.maxFailedAttempts) {
            changeStatus(VerificationStatus.LOCKED);
        } else {
            changeStatus(VerificationStatus.PENDING);
        }
    }

    public void recordProviderFailure(Instant now) {
        requireStatus(VerificationStatus.VERIFYING);
        requireBeforeExpiry(now);
        changeStatus(VerificationStatus.PENDING);
    }

    public void cancel(Instant now) {
        if (status.isTerminal()) {
            throw new IllegalStateException("A terminal verification request cannot be canceled");
        }
        requireBeforeExpiry(now);
        this.canceledAt = now;
        changeStatus(VerificationStatus.CANCELED);
    }

    /** Marks a due, nonterminal request as expired; returns whether its state changed. */
    public boolean expireIfDue(Instant now) {
        requireValidTime(now);
        if (status.isTerminal() || now.isBefore(expiresAt)) {
            return false;
        }
        changeStatus(VerificationStatus.EXPIRED);
        return true;
    }

    /** Checks effective expiration without mutating the request. */
    public boolean isExpired(Instant now) {
        requireValidTime(now);

        if (status == VerificationStatus.EXPIRED) {
            return true;
        }

        return !status.isTerminal() && !now.isBefore(expiresAt);
    }

    private void requireStatus(VerificationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    private void requireBeforeExpiry(Instant now) {
        requireValidTime(now);
        if (!now.isBefore(expiresAt)) {
            throw new IllegalStateException("Verification request has expired");
        }
    }

    private void requireValidTime(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("now must not be before createdAt");
        }
    }

    private void changeStatus(VerificationStatus newStatus) {
        this.status = newStatus;
        this.version++;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
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

    public String getNormalizedHandle() {
        return normalizedHandle;
    }

    public byte[] getCodeDigest() {
        return codeDigest.clone();
    }

    public String getCodeKeyVersion() {
        return codeKeyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public long getVersion() {
        return version;
    }
}
