package com.motaamneh.mstsocial.core.service;

import java.util.Objects;

/** Active means PENDING or VERIFYING, with an unexpired request deadline. */
public final class VerificationLimits {
    private final int maxActivePerSubject;
    private final int maxActivePerHandle;

    public static final VerificationLimits DEFAULT = new VerificationLimits(3, 3);

    public VerificationLimits(int maxActivePerSubject, int maxActivePerHandle) {
        if (maxActivePerSubject <= 0 || maxActivePerHandle <= 0) {
            throw new IllegalArgumentException("Active request limits must be positive");
        }
        this.maxActivePerSubject = maxActivePerSubject;
        this.maxActivePerHandle = maxActivePerHandle;
    }

    public int maxActivePerSubject() {
        return maxActivePerSubject;
    }

    public int maxActivePerHandle() {
        return maxActivePerHandle;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationLimits other)) {
            return false;
        }
        return Objects.equals(maxActivePerSubject, other.maxActivePerSubject)
                && Objects.equals(maxActivePerHandle, other.maxActivePerHandle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxActivePerSubject, maxActivePerHandle);
    }

    @Override
    public String toString() {
        return "VerificationLimits[maxActivePerSubject=" + maxActivePerSubject + ", maxActivePerHandle=" + maxActivePerHandle + "]";
    }
}
