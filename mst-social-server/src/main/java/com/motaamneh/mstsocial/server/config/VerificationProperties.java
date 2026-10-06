package com.motaamneh.mstsocial.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** Versioned HMAC secrets, supplied as 32 bytes encoded in hexadecimal. */
@ConfigurationProperties(prefix = "mst.verification")
public record VerificationProperties(
        String activeKeyVersion,
        Map<String, String> keys
) {
    public VerificationProperties {
        if (activeKeyVersion == null || activeKeyVersion.isBlank()) {
            throw new IllegalArgumentException("activeKeyVersion must not be blank");
        }
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("At least one verification key must be configured");
        }
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            String version = entry.getKey();
            String encodedKey = entry.getValue();
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("Verification key versions must not be blank");
            }
            if (encodedKey == null || !encodedKey.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException(
                        "Verification keys must contain 64 hexadecimal characters"
                );
            }
        }
        keys = Map.copyOf(keys);
        if (!keys.containsKey(activeKeyVersion)) {
            throw new IllegalArgumentException("activeKeyVersion must reference a configured key");
        }
    }

    @Override
    public String toString() {
        return "VerificationProperties[keys=REDACTED]";
    }
}
