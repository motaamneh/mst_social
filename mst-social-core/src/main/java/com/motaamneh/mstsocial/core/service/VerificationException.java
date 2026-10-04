package com.motaamneh.mstsocial.core.service;

/** Stable application errors without user input or provider payloads in messages. */
public final class VerificationException extends RuntimeException {
    public enum Code {
        REQUEST_NOT_FOUND, ACCOUNT_NOT_FOUND, DUPLICATE_ACTIVE_REQUEST,
        ACTIVE_REQUEST_LIMIT_REACHED, UNSUPPORTED_PLATFORM
    }

    private final Code code;

    public VerificationException(Code code) {
        super(java.util.Objects.requireNonNull(code, "code must not be null").name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
