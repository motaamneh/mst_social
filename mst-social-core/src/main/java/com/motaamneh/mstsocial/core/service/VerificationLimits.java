package com.motaamneh.mstsocial.core.service;

/** Active means PENDING or VERIFYING, with an unexpired request deadline. */
public record VerificationLimits(int maxActivePerSubject, int maxActivePerHandle) {
    public static final VerificationLimits DEFAULT = new VerificationLimits(3, 3);

    public VerificationLimits {
        if (maxActivePerSubject <= 0 || maxActivePerHandle <= 0) {
            throw new IllegalArgumentException("Active request limits must be positive");
        }
    }
}
