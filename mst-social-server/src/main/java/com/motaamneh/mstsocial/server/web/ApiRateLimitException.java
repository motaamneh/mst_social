package com.motaamneh.mstsocial.server.web;

import lombok.Getter;

@Getter
public final class ApiRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public ApiRateLimitException(long retryAfterSeconds) {
        super("The tenant's request rate limit was reached.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
