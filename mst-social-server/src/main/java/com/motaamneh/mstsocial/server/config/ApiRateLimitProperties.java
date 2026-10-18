package com.motaamneh.mstsocial.server.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Positive per-tenant limits in a fixed window starting at its first request. */
@Getter
@ConfigurationProperties(prefix = "mst.api.rate-limits")
public final class ApiRateLimitProperties {
    private final int createPerMinute;
    private final int verifyPerMinute;
    private final int readPerMinute;
    private final int mutationPerMinute;

    public ApiRateLimitProperties(
            @DefaultValue("10") int createPerMinute,
            @DefaultValue("10") int verifyPerMinute,
            @DefaultValue("120") int readPerMinute,
            @DefaultValue("30") int mutationPerMinute
    ) {
        if (createPerMinute <= 0 || verifyPerMinute <= 0 || readPerMinute <= 0 || mutationPerMinute <= 0) {
            throw new IllegalArgumentException("API rate limits must be positive");
        }
        this.createPerMinute = createPerMinute;
        this.verifyPerMinute = verifyPerMinute;
        this.readPerMinute = readPerMinute;
        this.mutationPerMinute = mutationPerMinute;
    }
}
