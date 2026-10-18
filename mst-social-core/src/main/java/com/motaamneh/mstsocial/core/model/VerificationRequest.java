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


    /** A detached persistence value. It does not perform lifecycle transitions. */
    public static final class Snapshot {
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
        private final VerificationStatus status;
        private final int failedAttempts;
        private final Instant verifiedAt;
        private final Instant canceledAt;
        private final long version;
        private final UUID verificationLeaseId;
        private final Instant leaseExpiresAt;

        public Snapshot(
                UUID id,
                UUID tenantId,
                String subjectId,
                Platform platform,
                String normalizedHandle,
                byte[] codeDigest,
                String codeKeyVersion,
                Instant createdAt,
                Instant expiresAt,
                int maxFailedAttempts,
                VerificationStatus status,
                int failedAttempts,
                Instant verifiedAt,
                Instant canceledAt,
                long version,
                UUID verificationLeaseId,
                Instant leaseExpiresAt
        ) {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            requireNonBlank(subjectId, "subjectId");
            Objects.requireNonNull(platform, "platform must not be null");
            requireNonBlank(normalizedHandle, "normalizedHandle");
            requireNonBlank(codeKeyVersion, "codeKeyVersion");
            Objects.requireNonNull(codeDigest, "codeDigest must not be null");
            if (codeDigest.length != 32) {
                throw new IllegalArgumentException("codeDigest must contain 32 bytes");
            }
            codeDigest = codeDigest.clone();
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            Objects.requireNonNull(status, "status must not be null");
            if (!expiresAt.isAfter(createdAt) || maxFailedAttempts <= 0 || version < 0
                    || failedAttempts < 0 || failedAttempts > maxFailedAttempts) {
                throw new IllegalArgumentException("Invalid saved request bounds");
            }
            if ((status == VerificationStatus.LOCKED) != (failedAttempts == maxFailedAttempts)) {
                throw new IllegalArgumentException("Attempt count does not match saved status");
            }
            if ((status == VerificationStatus.VERIFIED) != (verifiedAt != null)
                    || (status == VerificationStatus.CANCELED) != (canceledAt != null)) {
                throw new IllegalArgumentException("Saved terminal timestamps do not match status");
            }
            if ((verifiedAt != null && (verifiedAt.isBefore(createdAt) || !verifiedAt.isBefore(expiresAt)))
                    || (canceledAt != null && (canceledAt.isBefore(createdAt) || !canceledAt.isBefore(expiresAt)))) {
                throw new IllegalArgumentException("Saved event time is outside request lifetime");
            }
            if (status == VerificationStatus.VERIFYING) {
                if (verificationLeaseId == null || leaseExpiresAt == null
                        || !leaseExpiresAt.isAfter(createdAt) || leaseExpiresAt.isAfter(expiresAt)) {
                    throw new IllegalArgumentException("Saved verifying request requires a valid lease");
                }
            } else if (verificationLeaseId != null || leaseExpiresAt != null) {
                throw new IllegalArgumentException("Only verifying requests may retain a lease");
            }
            this.id = id;
            this.tenantId = tenantId;
            this.subjectId = subjectId;
            this.platform = platform;
            this.normalizedHandle = normalizedHandle;
            this.codeDigest = codeDigest;
            this.codeKeyVersion = codeKeyVersion;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.maxFailedAttempts = maxFailedAttempts;
            this.status = status;
            this.failedAttempts = failedAttempts;
            this.verifiedAt = verifiedAt;
            this.canceledAt = canceledAt;
            this.version = version;
            this.verificationLeaseId = verificationLeaseId;
            this.leaseExpiresAt = leaseExpiresAt;
        }

        public byte[] codeDigest() {
            return codeDigest.clone();
        }

        @Override
        public String toString() {
            return "VerificationRequest.Snapshot[redacted]";
        }

        public UUID id() {
            return id;
        }

        public UUID tenantId() {
            return tenantId;
        }

        public String subjectId() {
            return subjectId;
        }

        public Platform platform() {
            return platform;
        }

        public String normalizedHandle() {
            return normalizedHandle;
        }

        public String codeKeyVersion() {
            return codeKeyVersion;
        }

        public Instant createdAt() {
            return createdAt;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public int maxFailedAttempts() {
            return maxFailedAttempts;
        }

        public VerificationStatus status() {
            return status;
        }

        public int failedAttempts() {
            return failedAttempts;
        }

        public Instant verifiedAt() {
            return verifiedAt;
        }

        public Instant canceledAt() {
            return canceledAt;
        }

        public long version() {
            return version;
        }

        public UUID verificationLeaseId() {
            return verificationLeaseId;
        }

        public Instant leaseExpiresAt() {
            return leaseExpiresAt;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Snapshot other)) {
                return false;
            }
            return Objects.equals(id, other.id)
                    && Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(subjectId, other.subjectId)
                    && Objects.equals(platform, other.platform)
                    && Objects.equals(normalizedHandle, other.normalizedHandle)
                    && Objects.equals(codeDigest, other.codeDigest)
                    && Objects.equals(codeKeyVersion, other.codeKeyVersion)
                    && Objects.equals(createdAt, other.createdAt)
                    && Objects.equals(expiresAt, other.expiresAt)
                    && Objects.equals(maxFailedAttempts, other.maxFailedAttempts)
                    && Objects.equals(status, other.status)
                    && Objects.equals(failedAttempts, other.failedAttempts)
                    && Objects.equals(verifiedAt, other.verifiedAt)
                    && Objects.equals(canceledAt, other.canceledAt)
                    && Objects.equals(version, other.version)
                    && Objects.equals(verificationLeaseId, other.verificationLeaseId)
                    && Objects.equals(leaseExpiresAt, other.leaseExpiresAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, tenantId, subjectId, platform, normalizedHandle, codeDigest, codeKeyVersion, createdAt, expiresAt, maxFailedAttempts, status, failedAttempts, verifiedAt, canceledAt, version, verificationLeaseId, leaseExpiresAt);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(id, tenantId, subjectId, platform, normalizedHandle, codeDigest, codeKeyVersion, createdAt, expiresAt, maxFailedAttempts, status, failedAttempts, verifiedAt, canceledAt, version, verificationLeaseId, leaseExpiresAt);
    }

    /** Restores exact saved state, including historical deadlines and version. */
    public static VerificationRequest restore(Snapshot snapshot) {
        return new VerificationRequest(Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    private VerificationRequest(Snapshot snapshot) {
        this.id = snapshot.id();
        this.tenantId = snapshot.tenantId();
        this.subjectId = snapshot.subjectId();
        this.platform = snapshot.platform();
        this.normalizedHandle = snapshot.normalizedHandle();
        this.codeDigest = snapshot.codeDigest();
        this.codeKeyVersion = snapshot.codeKeyVersion();
        this.createdAt = snapshot.createdAt();
        this.expiresAt = snapshot.expiresAt();
        this.maxFailedAttempts = snapshot.maxFailedAttempts();
        this.status = snapshot.status();
        this.failedAttempts = snapshot.failedAttempts();
        this.verifiedAt = snapshot.verifiedAt();
        this.canceledAt = snapshot.canceledAt();
        this.version = snapshot.version();
        this.verificationLeaseId = snapshot.verificationLeaseId();
        this.leaseExpiresAt = snapshot.leaseExpiresAt();
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
