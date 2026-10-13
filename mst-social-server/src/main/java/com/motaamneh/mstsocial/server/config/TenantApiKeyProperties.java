package com.motaamneh.mstsocial.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** A dedicated HMAC key, independent of SearchAPI credentials and verification-code keys. */
@ConfigurationProperties(prefix = "mst.tenant-api-keys")
public record TenantApiKeyProperties(String hashKey) {
    public TenantApiKeyProperties {
        if (hashKey == null || !hashKey.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Tenant API-key hash key must contain 64 hexadecimal characters");
        }
    }

    @Override
    public String toString() {
        return "TenantApiKeyProperties[hashKey=REDACTED]";
    }
}
