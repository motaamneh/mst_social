package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Profile evidence returned by a provider. The biography may be empty.
 * providerAccountId and sourceObservedAt may be null when unavailable.
 * fetchedAt records when the fetch completed; sourceObservedAt records when
 * the source observed the profile, if the provider supplies that information.
 */
public final class ProfileObservation {
    private final String canonicalHandle;
    private final String biography;
    private final String providerAccountId;
    private final String evidenceProvider;
    private final Instant fetchedAt;
    private final Instant sourceObservedAt;

    public ProfileObservation(
            String canonicalHandle,
            String biography,
            String providerAccountId,
            String evidenceProvider,
            Instant fetchedAt,
            Instant sourceObservedAt
    ) {
        requireNonBlank(canonicalHandle, "canonicalHandle");
        Objects.requireNonNull(biography, "biography must not be null");
        if (providerAccountId != null) {
            requireNonBlank(providerAccountId, "providerAccountId");
        }
        requireNonBlank(evidenceProvider, "evidenceProvider");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        this.canonicalHandle = canonicalHandle;
        this.biography = biography;
        this.providerAccountId = providerAccountId;
        this.evidenceProvider = evidenceProvider;
        this.fetchedAt = fetchedAt;
        this.sourceObservedAt = sourceObservedAt;
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

    public String canonicalHandle() {
        return canonicalHandle;
    }

    public String biography() {
        return biography;
    }

    public String providerAccountId() {
        return providerAccountId;
    }

    public String evidenceProvider() {
        return evidenceProvider;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public Instant sourceObservedAt() {
        return sourceObservedAt;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof ProfileObservation other)) {
            return false;
        }
        return Objects.equals(canonicalHandle, other.canonicalHandle)
                && Objects.equals(biography, other.biography)
                && Objects.equals(providerAccountId, other.providerAccountId)
                && Objects.equals(evidenceProvider, other.evidenceProvider)
                && Objects.equals(fetchedAt, other.fetchedAt)
                && Objects.equals(sourceObservedAt, other.sourceObservedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalHandle, biography, providerAccountId, evidenceProvider, fetchedAt, sourceObservedAt);
    }
}
