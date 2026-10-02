package com.motaamneh.mstsocial.core.model;

public enum VerificationStatus {
    PENDING,
    VERIFYING,
    VERIFIED,
    EXPIRED,
    CANCELED,
    LOCKED;

    public boolean isTerminal() {
        return switch (this) {
            case VERIFIED, EXPIRED, CANCELED, LOCKED -> true;
            default -> false;
        };
    }
}
