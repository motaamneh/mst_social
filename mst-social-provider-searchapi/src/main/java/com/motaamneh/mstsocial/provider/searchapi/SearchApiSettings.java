package com.motaamneh.mstsocial.provider.searchapi;

import java.time.Duration;
import java.util.Objects;

public record SearchApiSettings(
        Duration connectTimeout, Duration requestTimeout, int maxResponseBytes
) {
    public static final SearchApiSettings DEFAULT = new SearchApiSettings(
            Duration.ofSeconds(5), Duration.ofSeconds(20), 1024 * 1024
    );
    public SearchApiSettings {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(requestTimeout, "requestTimeout");
        if(maxResponseBytes<=0){
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }

    }
    private static void requirePositive( Duration duration, String fieldName ) {
        Objects.requireNonNull(duration, fieldName + " must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
