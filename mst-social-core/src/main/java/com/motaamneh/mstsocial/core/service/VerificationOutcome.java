package com.motaamneh.mstsocial.core.service;

public enum VerificationOutcome {
    VERIFIED,
    ALREADY_VERIFIED,
    IN_PROGRESS,
    STALE_ATTEMPT,
    CODE_NOT_FOUND,
    PROVIDER_FAILURE,
    ACCOUNT_ALREADY_LINKED,
    EXPIRED,
    CANCELED,
    LOCKED
}
