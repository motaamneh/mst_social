package com.motaamneh.mstsocial.core.model;

import java.time.Duration;
import java.util.Objects;

/** Learned sealed for the first time I've been working for more than a year as a Java Software Engineer and just knew about sealed interface :)*/
public sealed interface ProfileFetchResult
        permits ProfileFetchResult.Found,
        ProfileFetchResult.Failed {
    static final class Found implements ProfileFetchResult {
        private final ProfileObservation observation;

        public Found(ProfileObservation observation) {
            Objects.requireNonNull(observation, "observation must not be null");
            this.observation = observation;
        }

        public ProfileObservation observation() {
            return observation;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Found other)) {
                return false;
            }
            return Objects.equals(observation, other.observation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observation);
        }

        @Override
        public String toString() {
            return "Found[observation=" + observation + "]";
        }
    }

    /**
     * An expected fetch failure. retryAfter is optional and, when supplied,
     * must be positive and refer to a retryable failure.
     * retryable does not automatically trigger a retry.
     */
    static final class Failed implements ProfileFetchResult {
        private final ProfileFailureReason reason;
        private final boolean retryable;
        private final Duration retryAfter;

        public Failed(ProfileFailureReason reason, boolean retryable, Duration retryAfter) {
            Objects.requireNonNull(reason, "reason must not be null");
            if (retryAfter != null) {
                if (retryAfter.isNegative() || retryAfter.isZero()) {
                    throw new IllegalArgumentException("retryAfter must be positive");
                }
                if (!retryable) {
                    throw new IllegalArgumentException("retryAfter requires a retryable failure");
                }
            }
            this.reason = reason;
            this.retryable = retryable;
            this.retryAfter = retryAfter;
        }

        public ProfileFailureReason reason() {
            return reason;
        }

        public boolean retryable() {
            return retryable;
        }

        public Duration retryAfter() {
            return retryAfter;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Failed other)) {
                return false;
            }
            return Objects.equals(reason, other.reason)
                    && Objects.equals(retryable, other.retryable)
                    && Objects.equals(retryAfter, other.retryAfter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason, retryable, retryAfter);
        }

        @Override
        public String toString() {
            return "Failed[reason=" + reason + ", retryable=" + retryable + ", retryAfter=" + retryAfter + "]";
        }
    }
}
