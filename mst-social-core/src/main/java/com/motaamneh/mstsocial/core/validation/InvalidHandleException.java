package com.motaamneh.mstsocial.core.validation;

/** Distinguishes invalid user handles from unrelated programming errors. */
public final class InvalidHandleException extends IllegalArgumentException {
    public InvalidHandleException(String message) {
        super(message);
    }
}
