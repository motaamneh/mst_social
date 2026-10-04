package com.motaamneh.mstsocial.core.port;

import com.motaamneh.mstsocial.core.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Persistence boundary. An adapter MUST serialize transactions within each tenant
 * (for example, lock the tenant row first), commit all writes and audit events
 * together, and roll everything back if the callback or commit fails.
 *
 * Every query and mutation is implicitly scoped to the supplied tenant. Reject
 * cross-tenant inserts/updates. Enforce account identity uniqueness and request
 * account-link uniqueness in storage, including across processes.
 *
 * Returned entities are transaction-local copies; mutable state must never leak
 * into committed storage before commit. Explicit save methods persist changes.
 * Do not automatically replay callbacks. Never run provider calls inside them.
 * The callback result is returned only after a successful commit.
 */
public interface VerificationStore {
    <T> T inTransaction(UUID tenantId, Function<Transaction, T> work);

    interface Transaction {
        Optional<VerificationRequest> findRequest(UUID requestId);

        /** Exclude terminal requests and requests whose expiresAt is <= now. */
        boolean hasActiveRequest(String subjectId, Platform platform, String handle, Instant now);

        long countActiveRequestsForSubject(String subjectId, Instant now);

        long countActiveRequestsForHandle(Platform platform, String handle, Instant now);

        void insertRequest(VerificationRequest request);

        void saveRequest(VerificationRequest request);

        Optional<VerifiedSocialAccount> findAccount(UUID accountId);

        /**
         * Return ALL active accounts matching the platform and either the handle
         * OR a non-null stable platform account ID. Account IDs must be platform
         * identifiers, not provider-specific opaque IDs. Do not return inactive links.
         */
        List<VerifiedSocialAccount> findActiveAccounts(
                Platform platform, String canonicalHandle, String providerAccountId, Instant now);

        void insertAccount(VerifiedSocialAccount account);

        void saveAccount(VerifiedSocialAccount account);

        /** Immutable request-to-account association; one account per verified request. */
        void linkVerifiedRequest(UUID requestId, UUID accountId);

        Optional<VerifiedSocialAccount> findAccountForRequest(UUID requestId);

        void appendAudit(VerificationAuditEvent event);
    }
}
