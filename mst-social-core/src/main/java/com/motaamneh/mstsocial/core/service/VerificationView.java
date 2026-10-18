package com.motaamneh.mstsocial.core.service;

import java.util.Objects;

import com.motaamneh.mstsocial.core.model.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable public status: no digest, key version, lease ID, or biography. */
public final class VerificationView {
    private final UUID requestId;
    private final String subjectId;
    private final Platform platform;
    private final String normalizedHandle;
    private final VerificationStatus status;
    private final int failedAttempts;
    private final int maxFailedAttempts;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant verifiedAt;
    private final Instant canceledAt;

    public VerificationView(
            UUID requestId,
            String subjectId,
            Platform platform,
            String normalizedHandle,
            VerificationStatus status,
            int failedAttempts,
            int maxFailedAttempts,
            Instant createdAt,
            Instant expiresAt,
            Instant verifiedAt,
            Instant canceledAt
    ) {
        this.requestId = requestId;
        this.subjectId = subjectId;
        this.platform = platform;
        this.normalizedHandle = normalizedHandle;
        this.status = status;
        this.failedAttempts = failedAttempts;
        this.maxFailedAttempts = maxFailedAttempts;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.verifiedAt = verifiedAt;
        this.canceledAt = canceledAt;
    }

    public static VerificationView from(VerificationRequest request) {
        return new VerificationView(request.getId(), request.getSubjectId(), request.getPlatform(),
                request.getNormalizedHandle(), request.getStatus(), request.getFailedAttempts(),
                request.getMaxFailedAttempts(), request.getCreatedAt(), request.getExpiresAt(),
                request.getVerifiedAt(), request.getCanceledAt());
    }

    public UUID requestId() {
        return requestId;
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

    public VerificationStatus status() {
        return status;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public Instant canceledAt() {
        return canceledAt;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationView other)) {
            return false;
        }
        return Objects.equals(requestId, other.requestId)
                && Objects.equals(subjectId, other.subjectId)
                && Objects.equals(platform, other.platform)
                && Objects.equals(normalizedHandle, other.normalizedHandle)
                && Objects.equals(status, other.status)
                && Objects.equals(failedAttempts, other.failedAttempts)
                && Objects.equals(maxFailedAttempts, other.maxFailedAttempts)
                && Objects.equals(createdAt, other.createdAt)
                && Objects.equals(expiresAt, other.expiresAt)
                && Objects.equals(verifiedAt, other.verifiedAt)
                && Objects.equals(canceledAt, other.canceledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, subjectId, platform, normalizedHandle, status, failedAttempts, maxFailedAttempts, createdAt, expiresAt, verifiedAt, canceledAt);
    }

    @Override
    public String toString() {
        return "VerificationView[requestId=" + requestId + ", subjectId=" + subjectId + ", platform=" + platform + ", normalizedHandle=" + normalizedHandle + ", status=" + status + ", failedAttempts=" + failedAttempts + ", maxFailedAttempts=" + maxFailedAttempts + ", createdAt=" + createdAt + ", expiresAt=" + expiresAt + ", verifiedAt=" + verifiedAt + ", canceledAt=" + canceledAt + "]";
    }
}
