package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.ProfileFetchResult;
import java.util.Objects;

/** account exists only for success; providerFailure exists only for PROVIDER_FAILURE. */
public final class VerificationResult {
    private final VerificationOutcome outcome;
    private final VerificationView verification;
    private final VerifiedAccountView account;
    private final ProfileFetchResult.Failed providerFailure;

    public VerificationResult(
            VerificationOutcome outcome,
            VerificationView verification,
            VerifiedAccountView account,
            ProfileFetchResult.Failed providerFailure
    ) {
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
        this.outcome = outcome;
        this.verification = verification;
        this.account = account;
        this.providerFailure = providerFailure;
    }

    public VerificationOutcome outcome() {
        return outcome;
    }

    public VerificationView verification() {
        return verification;
    }

    public VerifiedAccountView account() {
        return account;
    }

    public ProfileFetchResult.Failed providerFailure() {
        return providerFailure;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationResult other)) {
            return false;
        }
        return Objects.equals(outcome, other.outcome)
                && Objects.equals(verification, other.verification)
                && Objects.equals(account, other.account)
                && Objects.equals(providerFailure, other.providerFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, verification, account, providerFailure);
    }

    @Override
    public String toString() {
        return "VerificationResult[outcome=" + outcome + ", verification=" + verification + ", account=" + account + ", providerFailure=" + providerFailure + "]";
    }
}
