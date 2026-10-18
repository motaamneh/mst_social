package com.motaamneh.mstsocial.core.model;

import java.util.Objects;

import java.time.Duration;

public final class VerificationPolicy {
    private final Duration requestLifetime;
    private final int maxFailedAttempts;
    private final int verifiedAccountLifetimeYears;
    private final Duration verificationLeaseDuration;

    public static final VerificationPolicy DEFAULT = new VerificationPolicy(
            Duration.ofMinutes(15),
            10,
            1,
            Duration.ofMinutes(1)
    );

    public VerificationPolicy(
            Duration requestLifetime,
            int maxFailedAttempts,
            int verifiedAccountLifetimeYears,
            Duration verificationLeaseDuration
    ) {
        if (requestLifetime == null || requestLifetime.isNegative() || requestLifetime.isZero()) {
            throw new IllegalArgumentException("requestLifetime must be a positive duration");
        }
        if (maxFailedAttempts <= 0) {
            throw new IllegalArgumentException("maxFailedAttempts must be a positive integer");
        }
        if (verifiedAccountLifetimeYears <= 0) {
            throw new IllegalArgumentException("verifiedAccountLifetimeYears must be a positive integer");
        }
        if (verificationLeaseDuration == null
                || verificationLeaseDuration.isNegative()
                || verificationLeaseDuration.isZero()) {
            throw new IllegalArgumentException("verificationLeaseDuration must be a positive duration");
        }
        this.requestLifetime = requestLifetime;
        this.maxFailedAttempts = maxFailedAttempts;
        this.verifiedAccountLifetimeYears = verifiedAccountLifetimeYears;
        this.verificationLeaseDuration = verificationLeaseDuration;
    }

    public Duration requestLifetime() {
        return requestLifetime;
    }

    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    public int verifiedAccountLifetimeYears() {
        return verifiedAccountLifetimeYears;
    }

    public Duration verificationLeaseDuration() {
        return verificationLeaseDuration;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationPolicy other)) {
            return false;
        }
        return Objects.equals(requestLifetime, other.requestLifetime)
                && Objects.equals(maxFailedAttempts, other.maxFailedAttempts)
                && Objects.equals(verifiedAccountLifetimeYears, other.verifiedAccountLifetimeYears)
                && Objects.equals(verificationLeaseDuration, other.verificationLeaseDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestLifetime, maxFailedAttempts, verifiedAccountLifetimeYears, verificationLeaseDuration);
    }

    @Override
    public String toString() {
        return "VerificationPolicy[requestLifetime=" + requestLifetime + ", maxFailedAttempts=" + maxFailedAttempts + ", verifiedAccountLifetimeYears=" + verifiedAccountLifetimeYears + ", verificationLeaseDuration=" + verificationLeaseDuration + "]";
    }
}
