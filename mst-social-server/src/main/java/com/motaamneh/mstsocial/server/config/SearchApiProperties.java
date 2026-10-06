package com.motaamneh.mstsocial.server.config;

import com.motaamneh.mstsocial.provider.searchapi.SearchApiSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Server configuration; the API key must be supplied outside source control. */
@ConfigurationProperties(prefix = "mst.searchapi")
public record SearchApiProperties(
        String apiKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration requestTimeout,
        @DefaultValue("1048576") int maxResponseBytes
) {
    public SearchApiProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SearchAPI API key must be configured");
        }
        if (apiKey.chars().anyMatch(c -> c <= 32 || c >= 127)) {
            throw new IllegalArgumentException("SearchAPI API key contains invalid header characters");
        }
        // Reuse the provider's validation instead of maintaining duplicate timeout rules.
        new SearchApiSettings(connectTimeout, requestTimeout, maxResponseBytes);
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
}
