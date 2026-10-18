package com.motaamneh.mstsocial.server.web;

import lombok.Getter;

/** Explicit HTTP boundary failure; messages are application-defined constants. */
@Getter
public final class ApiRequestException extends RuntimeException {
    private final String code;

    public ApiRequestException(String code, String detail) {
        super(detail);
        this.code = code;
    }
}
