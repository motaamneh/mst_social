package com.motaamneh.mstsocial.server.security;

import com.motaamneh.mstsocial.server.config.TenantApiKeyProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Encodes a public credential ID and a random secret; only the secret digest is stored. */
@Component
public final class TenantApiKeyCodec {
    private static final String PREFIX = "mst_api_";
    public static final int CREDENTIAL_LENGTH = 109;
    private static final Pattern FORMAT = Pattern.compile(
            "mst_api_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.([0-9a-f]{64})"
    );
    private final SecureRandom random;
    private final SecretKey hashKey;

    public TenantApiKeyCodec(SecureRandom random, TenantApiKeyProperties properties) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        byte[] decoded = HexFormat.of().parseHex(properties.hashKey());
        try {
            this.hashKey = new SecretKeySpec(decoded, "HmacSHA256");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public GeneratedCredential generate(UUID tenantId, String name, Instant createdAt, Instant expiresAt) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        try {
            UUID id = UUID.randomUUID();
            TenantApiKey metadata = new TenantApiKey(
                    id, tenantId, name, digest(secret), createdAt, expiresAt, null
            );
            return new GeneratedCredential(metadata, PREFIX + id + "." + HexFormat.of().formatHex(secret));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    Optional<ParsedCredential> parse(String credential) {
        if (credential == null || credential.length() != CREDENTIAL_LENGTH) {
            return Optional.empty();
        }
        var matcher = FORMAT.matcher(credential);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        byte[] secret = HexFormat.of().parseHex(matcher.group(2));
        try {
            return Optional.of(new ParsedCredential(UUID.fromString(matcher.group(1)), digest(secret)));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private byte[] digest(byte[] secret) {
        try {
            // Mac is mutable: create one per call so concurrent requests do not share state.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            return mac.doFinal(secret);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    /** Returned only during provisioning. Never log or serialize this object. */
    public static final class GeneratedCredential {
        private final TenantApiKey metadata;
        private final String credential;

        public GeneratedCredential(TenantApiKey metadata, String credential) {
            this.metadata = metadata;
            this.credential = credential;
        }

        @Override
        public String toString() {
            return "GeneratedCredential[id=" + metadata.getId() + ", credential=[REDACTED]]";
        }

        public TenantApiKey metadata() {
            return metadata;
        }

        public String credential() {
            return credential;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof GeneratedCredential other)) {
                return false;
            }
            return Objects.equals(metadata, other.metadata)
                    && Objects.equals(credential, other.credential);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metadata, credential);
        }
    }

    static final class ParsedCredential {
        private final UUID id;
        private final byte[] digest;

        ParsedCredential(UUID id, byte[] digest) {
            digest = digest.clone();
            this.id = id;
            this.digest = digest;
        }

        public byte[] digest() {
            return digest.clone();
        }

        @Override
        public String toString() {
            return "ParsedCredential[id=" + id + ", digest=[REDACTED]]";
        }

        public UUID id() {
            return id;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof ParsedCredential other)) {
                return false;
            }
            return Objects.equals(id, other.id)
                    && Objects.equals(digest, other.digest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, digest);
        }
    }
}
