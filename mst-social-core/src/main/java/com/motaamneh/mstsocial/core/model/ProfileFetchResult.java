package com.motaamneh.mstsocial.core.model;

import java.time.Duration;
import java.util.Objects;

/** Learned sealed for the first time I've been working for more than a year as a Java Software Engineer and just knew about sealed interface :)*/
public sealed interface ProfileFetchResult
        permits ProfileFetchResult.Found,
        ProfileFetchResult.Failed {
    record Found(
            ProfileObservation observation
    ) implements ProfileFetchResult {
        public Found {
            Objects.requireNonNull(observation, "observation must not be null");
        }
    }

    /**
     * An expected fetch failure. retryAfter is optional and, when supplied,
     * must be positive and refer to a retryable failure.
     * retryable does not automatically trigger a retry.
     */
    record Failed(
            ProfileFailureReason reason,
            boolean retryable,
            Duration retryAfter
    ) implements ProfileFetchResult {
        public Failed {
            Objects.requireNonNull(reason, "reason must not be null");
            if (retryAfter != null) {
                if (retryAfter.isNegative() || retryAfter.isZero()) {
                    throw new IllegalArgumentException("retryAfter must be positive");
                }
                if (!retryable) {
                    throw new IllegalArgumentException("retryAfter requires a retryable failure");
                }
            }
        }
    }
}
