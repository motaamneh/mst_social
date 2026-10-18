package com.motaamneh.mstsocial.server.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** A dedicated HMAC key, independent of SearchAPI credentials and verification-code keys. */
@ConfigurationProperties(prefix = "mst.tenant-api-keys")
public final class TenantApiKeyProperties {
    private final String hashKey;

    public TenantApiKeyProperties(String hashKey) {
        if (hashKey == null || !hashKey.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Tenant API-key hash key must contain 64 hexadecimal characters");
        }
        this.hashKey = hashKey;
    }

    @Override
    public String toString() {
        return "TenantApiKeyProperties[hashKey=REDACTED]";
    }

    public String hashKey() {
        return hashKey;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof TenantApiKeyProperties other)) {
            return false;
        }
        return Objects.equals(hashKey, other.hashKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashKey);
    }
}
