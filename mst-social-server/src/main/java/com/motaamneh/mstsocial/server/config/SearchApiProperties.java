package com.motaamneh.mstsocial.server.config;

import java.util.Objects;

import com.motaamneh.mstsocial.provider.searchapi.SearchApiSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Server configuration; the API key must be supplied outside source control. */
@ConfigurationProperties(prefix = "mst.searchapi")
public final class SearchApiProperties {
    private final String apiKey;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    public SearchApiProperties(
            String apiKey,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("20s") Duration requestTimeout,
            @DefaultValue("1048576") int maxResponseBytes
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SearchAPI API key must be configured");
        }
        if (apiKey.chars().anyMatch(c -> c <= 32 || c >= 127)) {
            throw new IllegalArgumentException("SearchAPI API key contains invalid header characters");
        }
        // Reuse the provider's validation instead of maintaining duplicate timeout rules.
        new SearchApiSettings(connectTimeout, requestTimeout, maxResponseBytes);
        this.apiKey = apiKey;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    public SearchApiSettings toSettings() {
        return new SearchApiSettings(connectTimeout, requestTimeout, maxResponseBytes);
    }

    @Override
    public String toString() {
        return "SearchApiProperties[apiKey=REDACTED, connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout
                + ", maxResponseBytes=" + maxResponseBytes + "]";
    }

    public String apiKey() {
        return apiKey;
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
        if (!(value instanceof SearchApiProperties other)) {
            return false;
        }
        return Objects.equals(apiKey, other.apiKey)
                && Objects.equals(connectTimeout, other.connectTimeout)
                && Objects.equals(requestTimeout, other.requestTimeout)
                && Objects.equals(maxResponseBytes, other.maxResponseBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, connectTimeout, requestTimeout, maxResponseBytes);
    }
}
