package com.motaamneh.mstsocial.core.security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;

/*  sudo install opsec  lol */
public class VerificationCodeHasher {
    private static final String ALGORITHM = "HmacSHA256";

    private final Map<String, SecretKey> keys;

    public VerificationCodeHasher(Map<String, SecretKey> keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }

        this.keys = Map.copyOf(keys);
        for (String keyVersion : this.keys.keySet()) {
            requireNonBlank(keyVersion, "keyVersion");
        }
    }

    public byte[] hash(String code, String keyVersion) {
        requireNonBlank(code, "code");
        requireKeyVersion(keyVersion);
        SecretKey key = keys.get(keyVersion);

        try {
            // Mac is mutable, so each call uses a separate instance.
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        } catch (InvalidKeyException exception) {
            throw new IllegalStateException("Configured HMAC key is invalid", exception);
        }
    }

    public void requireKeyVersion(String keyVersion) {
        requireNonBlank(keyVersion, "keyVersion");
        if (!keys.containsKey(keyVersion)) {
            throw new IllegalArgumentException("Unknown key version");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
