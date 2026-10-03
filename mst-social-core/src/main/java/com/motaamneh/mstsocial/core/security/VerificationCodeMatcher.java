package com.motaamneh.mstsocial.core.security;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*  Just to make sure that opsec is opsecing hahahahaha */
public class VerificationCodeMatcher {
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}\\p{M}_-])"
                    + "mst_[0-9a-f]{32}"
                    + "(?![\\p{L}\\p{N}\\p{M}_-])"
    );

    private final VerificationCodeHasher hasher;

    public VerificationCodeMatcher(VerificationCodeHasher hasher) {
        this.hasher = Objects.requireNonNull(
                hasher,
                "hasher must not be null"
        );
    }

    public boolean matches(
            String biography,
            byte[] expectedDigest,
            String keyVersion
    ) {
        Objects.requireNonNull(biography, "biography must not be null");
        Objects.requireNonNull(expectedDigest, "expectedDigest must not be null");
        if (expectedDigest.length != 32) {
            throw new IllegalArgumentException("expectedDigest must contain 32 bytes");
        }

        hasher.requireKeyVersion(keyVersion);
        byte[] digestToMatch = expectedDigest.clone();
        Matcher matcher = CODE_PATTERN.matcher(biography);

        while (matcher.find()) {
            String candidate = matcher.group();
            byte[] candidateDigest = hasher.hash(candidate, keyVersion);
            if (MessageDigest.isEqual(digestToMatch, candidateDigest)) {
                return true;
            }
        }

        return false;
    }
}
