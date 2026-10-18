package com.motaamneh.mstsocial.provider.searchapi;

import java.time.Duration;
import java.util.Objects;

public final class SearchApiSettings {
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    public static final SearchApiSettings DEFAULT = new SearchApiSettings(
            Duration.ofSeconds(5), Duration.ofSeconds(20), 1024 * 1024
    );
    public SearchApiSettings(Duration connectTimeout, Duration requestTimeout, int maxResponseBytes) {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(requestTimeout, "requestTimeout");
        if(maxResponseBytes<=0){
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }
    private static void requirePositive( Duration duration, String fieldName ) {
        Objects.requireNonNull(duration, fieldName + " must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int maxResponseBytes() {
        return maxResponseBytes;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof SearchApiSettings other)) {
            return false;
        }
        return Objects.equals(connectTimeout, other.connectTimeout)
                && Objects.equals(requestTimeout, other.requestTimeout)
                && Objects.equals(maxResponseBytes, other.maxResponseBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectTimeout, requestTimeout, maxResponseBytes);
    }

    @Override
    public String toString() {
        return "SearchApiSettings[connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout + ", maxResponseBytes=" + maxResponseBytes + "]";
    }
}
