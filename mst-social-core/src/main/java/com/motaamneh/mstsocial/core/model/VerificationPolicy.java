package com.motaamneh.mstsocial.core.model;

import java.time.Duration;

public record VerificationPolicy(
        Duration requestLifetime,
        int maxFailedAttempts,
        int verifiedAccountLifetimeYears,
        Duration verificationLeaseDuration
) {
    public static final VerificationPolicy DEFAULT = new VerificationPolicy(
            Duration.ofMinutes(15),
            5,
            1,
            Duration.ofMinutes(1)
    );

    public VerificationPolicy {
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
    }
}
