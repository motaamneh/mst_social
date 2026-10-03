package com.motaamneh.mstsocial.core.validation;

import com.motaamneh.mstsocial.core.model.Platform;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validates the application's supported handle format before normalizing case. */
public final class HandleNormalizer {
    private static final int MAX_INPUT_LENGTH = 256;
    private static final int INSTAGRAM_MAX_LENGTH = 30;
    private static final int TIKTOK_MAX_LENGTH = 24;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[A-Za-z0-9._]+");

    /**
     * Removes surrounding whitespace and one optional leading {@code @}.
     * Accepts ASCII handles only and preserves periods and underscores.
     *
     * @throws NullPointerException if platform or input is null
     * @throws IllegalArgumentException if the input violates the accepted handle format
     * @throws UnsupportedOperationException if the platform has no normalization policy
     */
    public String normalize(Platform platform, String input) {
        Objects.requireNonNull(platform, "platform must not be null");
        int maxLength = switch (platform) {
            case INSTAGRAM -> INSTAGRAM_MAX_LENGTH;
            case TIKTOK -> TIKTOK_MAX_LENGTH;
            case X, FACEBOOK -> throw new UnsupportedOperationException("UNSUPPORTED_PLATFORM");
        };

        Objects.requireNonNull(input, "input must not be null");
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Handle input is too long");
        }
        // Check before stripping so tabs, newlines, and other controls are never hidden.
        if (input.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Handle must not contain control characters");
        }

        String handle = input.strip();
        if (handle.startsWith("@")) {
            handle = handle.substring(1);
        }
        if (handle.isEmpty() || handle.length() > maxLength) {
            throw new IllegalArgumentException("Handle length must be between 1 and " + maxLength);
        }

        // Validate before lowercasing to avoid turning a Unicode lookalike into valid ASCII.
        if (!ALLOWED_CHARACTERS.matcher(handle).matches()) {
            throw new IllegalArgumentException(
                    "Handle must contain only ASCII letters, digits, periods, and underscores"
            );
        }

        switch (platform) {
            case INSTAGRAM -> {
                if (handle.startsWith(".") || handle.endsWith(".") || handle.contains("..")) {
                    throw new IllegalArgumentException(
                            "Instagram handle must not start or end with a period or contain consecutive periods"
                    );
                }
            }
            case TIKTOK -> {
                if (handle.endsWith(".")) {
                    throw new IllegalArgumentException("TikTok handle must not end with a period");
                }
            }
            case X, FACEBOOK -> throw new UnsupportedOperationException("UNSUPPORTED_PLATFORM");
        }

        return handle.toLowerCase(Locale.ROOT);
    }
}
