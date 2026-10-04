package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Profile evidence returned by a provider. The biography may be empty.
 * providerAccountId and sourceObservedAt may be null when unavailable.
 * fetchedAt records when the fetch completed; sourceObservedAt records when
 * the source observed the profile, if the provider supplies that information.
 */
public record ProfileObservation(
        String canonicalHandle,
        String biography,
        String providerAccountId,
        String evidenceProvider,
        Instant fetchedAt,
        Instant sourceObservedAt
) {
    public ProfileObservation {
        requireNonBlank(canonicalHandle, "canonicalHandle");
        Objects.requireNonNull(biography, "biography must not be null");
        if (providerAccountId != null) {
            requireNonBlank(providerAccountId, "providerAccountId");
        }
        requireNonBlank(evidenceProvider, "evidenceProvider");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "ProfileObservation[profile data redacted]";
    }
}
