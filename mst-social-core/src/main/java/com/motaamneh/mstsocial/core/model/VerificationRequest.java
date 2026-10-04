package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.time.Duration;
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
    private UUID verificationLeaseId;
    private Instant leaseExpiresAt;

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

    /** Claims a pending request, or replaces an expired worker lease. */
    public void beginVerification(UUID leaseId, Duration leaseDuration, Instant now) {
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (leaseId.equals(verificationLeaseId)) {
            throw new IllegalArgumentException("A replacement lease must have a new ID");
        }
        requireBeforeExpiry(now);
        if (status != VerificationStatus.PENDING
                && (status != VerificationStatus.VERIFYING || now.isBefore(leaseExpiresAt))) {
            throw new IllegalStateException("Verification request cannot be claimed");
        }
        Instant deadline = now.plus(leaseDuration);
        this.verificationLeaseId = leaseId;
        this.leaseExpiresAt = deadline.isBefore(expiresAt) ? deadline : expiresAt;
        changeStatus(VerificationStatus.VERIFYING);
    }

    public boolean ownsLease(UUID leaseId, Instant now) {
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        requireValidTime(now);
        return status == VerificationStatus.VERIFYING
                && leaseId.equals(verificationLeaseId)
                && now.isBefore(leaseExpiresAt) && now.isBefore(expiresAt);
    }

    private void requireLease(UUID leaseId, Instant now) {
        if (!ownsLease(leaseId, now)) {
            throw new IllegalStateException("Verification lease is no longer owned");
        }
    }

    public void markVerified(UUID leaseId, Instant now) {
        requireLease(leaseId, now);
        this.verifiedAt = now;
        changeStatus(VerificationStatus.VERIFIED);
    }

    public void recordMismatch(UUID leaseId, Instant now) {
        requireLease(leaseId, now);
        this.failedAttempts++;
        if (this.failedAttempts >= this.maxFailedAttempts) {
            changeStatus(VerificationStatus.LOCKED);
        } else {
            changeStatus(VerificationStatus.PENDING);
        }
    }

    public void recordProviderFailure(UUID leaseId, Instant now) {
        requireLease(leaseId, now);
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
        if (newStatus != VerificationStatus.VERIFYING) {
            this.verificationLeaseId = null;
            this.leaseExpiresAt = null;
        }
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

    public UUID getVerificationLeaseId() {
        return verificationLeaseId;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }
}
