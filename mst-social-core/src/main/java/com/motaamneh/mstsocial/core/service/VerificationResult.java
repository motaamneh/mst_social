package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.ProfileFetchResult;
import java.util.Objects;

/** account exists only for success; providerFailure exists only for PROVIDER_FAILURE. */
public record VerificationResult(
        VerificationOutcome outcome,
        VerificationView verification,
        VerifiedAccountView account,
        ProfileFetchResult.Failed providerFailure
) {
    public VerificationResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(verification, "verification must not be null");
        boolean success = outcome == VerificationOutcome.VERIFIED
                || outcome == VerificationOutcome.ALREADY_VERIFIED;
        if (success != (account != null)) {
            throw new IllegalArgumentException("Only successful results require an account");
        }
        if ((outcome == VerificationOutcome.PROVIDER_FAILURE) != (providerFailure != null)) {
            throw new IllegalArgumentException("Only provider failures require failure details");
        }
    }
}
