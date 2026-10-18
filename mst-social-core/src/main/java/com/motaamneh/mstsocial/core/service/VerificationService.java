package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.*;
import com.motaamneh.mstsocial.core.port.ProfileProvider;
import com.motaamneh.mstsocial.core.port.VerificationStore;
import com.motaamneh.mstsocial.core.security.VerificationCodeGenerator;
import com.motaamneh.mstsocial.core.security.VerificationCodeHasher;
import com.motaamneh.mstsocial.core.security.VerificationCodeMatcher;
import com.motaamneh.mstsocial.core.validation.HandleNormalizer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.motaamneh.mstsocial.core.model.VerificationAuditEvent.Type.*;
import static com.motaamneh.mstsocial.core.service.VerificationException.Code.*;

/**
 * Framework-independent use cases. The host authenticates the tenant and authorizes
 * the subject/resource before calling this service. No method accepts a client bio.
 * Provider and store implementations must be safe for concurrent use.
 */
public final class VerificationService {
    private final VerificationStore store;
    private final ProfileProvider provider;
    private final VerificationCodeGenerator generator;
    private final VerificationCodeHasher hasher;
    private final VerificationCodeMatcher matcher;
    private final HandleNormalizer normalizer;
    private final VerificationPolicy policy;
    private final VerificationLimits limits;
    private final Clock clock;
    private final String activeKeyVersion;

    public VerificationService(
            VerificationStore store, ProfileProvider provider,
            VerificationCodeGenerator generator, VerificationCodeHasher hasher,
            HandleNormalizer normalizer, VerificationPolicy policy,
            VerificationLimits limits, Clock clock, String activeKeyVersion
    ) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.matcher = new VerificationCodeMatcher(hasher);
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        hasher.requireKeyVersion(activeKeyVersion);
        this.activeKeyVersion = activeKeyVersion;
    }

    public VerificationCreated createVerification(UUID tenantId, CreateVerificationCommand command) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (!provider.supports(command.platform())) {
            throw new VerificationException(UNSUPPORTED_PLATFORM);
        }
        String handle;
        try {
            handle = normalizer.normalize(command.platform(), command.handle());
        } catch (UnsupportedOperationException exception) {
            throw new VerificationException(UNSUPPORTED_PLATFORM);
        }
        String code = generator.generate();
        byte[] digest = hasher.hash(code, activeKeyVersion);
        return store.inTransaction(tenantId, tx -> {
            Instant now = clock.instant();
            if (tx.hasActiveRequest(command.subjectId(), command.platform(), handle, now)) {
                throw new VerificationException(DUPLICATE_ACTIVE_REQUEST);
            }
            if (tx.countActiveRequestsForSubject(command.subjectId(), now) >= limits.maxActivePerSubject()
                    || tx.countActiveRequestsForHandle(command.platform(), handle, now)
                    >= limits.maxActivePerHandle()) {
                throw new VerificationException(ACTIVE_REQUEST_LIMIT_REACHED);
            }
            VerificationRequest request = new VerificationRequest(
                    UUID.randomUUID(), tenantId, command.subjectId(), command.platform(), handle,
                    digest, activeKeyVersion, now, policy);
            tx.insertRequest(request);
            audit(tx, tenantId, request.getId(), REQUEST_CREATED, now);
            return new VerificationCreated(request.getId(), code, request.getExpiresAt());
        });
    }

    public VerificationView getVerification(UUID tenantId, UUID requestId) {
        requireIds(tenantId, requestId);
        return store.inTransaction(tenantId, tx -> {
            VerificationRequest request = request(tx, requestId);
            expire(tx, request, clock.instant());
            return VerificationView.from(request);
        });
    }

    /** Terminal requests are returned unchanged, making cancellation idempotent. */
    public VerificationView cancelVerification(UUID tenantId, UUID requestId) {
        requireIds(tenantId, requestId);
        return store.inTransaction(tenantId, tx -> {
            Instant now = clock.instant();
            VerificationRequest request = request(tx, requestId);
            expire(tx, request, now);
            if (!request.getStatus().isTerminal()) {
                request.cancel(now);
                tx.saveRequest(request);
                audit(tx, tenantId, requestId, REQUEST_CANCELED, now);
            }
            return VerificationView.from(request);
        });
    }

    public VerifiedAccountView getVerifiedAccount(UUID tenantId, UUID accountId) {
        requireIds(tenantId, accountId);
        return store.inTransaction(tenantId, tx ->
                VerifiedAccountView.from(account(tx, accountId), clock.instant()));
    }

    public VerifiedAccountView revokeVerifiedAccount(UUID tenantId, UUID accountId) {
        requireIds(tenantId, accountId);
        return store.inTransaction(tenantId, tx -> {
            Instant now = clock.instant();
            VerifiedSocialAccount account = account(tx, accountId);
            if (account.getRevokedAt() == null) {
                account.revoke(now);
                tx.saveAccount(account);
                audit(tx, tenantId, accountId, ACCOUNT_REVOKED, now);
            }
            return VerifiedAccountView.from(account, now);
        });
    }

    public VerificationResult verify(UUID tenantId, UUID requestId) {
        requireIds(tenantId, requestId);
        // Transaction 1: claim the request, then release the database lock.
        Claim claim = store.inTransaction(tenantId, tx -> claim(tx, requestId));
        if (claim.result() != null) {
            return claim.result();
        }
        Attempt attempt = claim.attempt();

        // The potentially slow provider call runs with NO storage transaction open.
        ProfileFetchResult fetched = fetch(attempt);
        Evaluation evaluation = evaluate(attempt, fetched);

        // Transaction 2: recheck deadlines and ownership before any state change.
        return store.inTransaction(tenantId, tx -> complete(tx, attempt, evaluation));
    }

    private Claim claim(VerificationStore.Transaction tx, UUID requestId) {
        Instant now = clock.instant();
        VerificationRequest request = request(tx, requestId);
        expire(tx, request, now);
        VerificationResult terminal = terminalResult(tx, request, now);
        if (terminal != null) {
            return new Claim(null, terminal);
        }
        if (request.getStatus() == VerificationStatus.VERIFYING
                && now.isBefore(request.getLeaseExpiresAt())) {
            return new Claim(null, result(VerificationOutcome.IN_PROGRESS, request));
        }
        hasher.requireKeyVersion(request.getCodeKeyVersion());
        UUID leaseId = UUID.randomUUID();
        request.beginVerification(leaseId, policy.verificationLeaseDuration(), now);
        tx.saveRequest(request);
        audit(tx, request.getTenantId(), requestId, ATTEMPT_STARTED, now);
        return new Claim(new Attempt(requestId, leaseId, request.getPlatform(),
                request.getNormalizedHandle(), request.getCodeDigest(),
                request.getCodeKeyVersion(), request.getCreatedAt()), null);
    }

    private ProfileFetchResult fetch(Attempt attempt) {
        try {
            if (!provider.supports(attempt.platform())) {
                return failure(ProfileFailureReason.UNSUPPORTED_PLATFORM);
            }
            ProfileFetchResult fetched = provider.fetchProfile(attempt.platform(), attempt.handle());
            return fetched == null ? failure(ProfileFailureReason.INVALID_PROVIDER_RESPONSE) : fetched;
        } catch (RuntimeException exception) {
            // Expected failures should already be mapped by the adapter. Never expose
            // an unexpected exception's message, which may contain credentials or bios.
            return failure(ProfileFailureReason.PROVIDER_ERROR);
        }
    }

    private Evaluation evaluate(Attempt attempt, ProfileFetchResult fetched) {
        if (fetched instanceof ProfileFetchResult.Failed failed) {
            return new Evaluation(null, false, failed);
        }
        ProfileObservation observation = ((ProfileFetchResult.Found) fetched).observation();
        String handle;
        try {
            handle = normalizer.normalize(attempt.platform(), observation.canonicalHandle());
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            return invalidObservation();
        }
        if (!attempt.handle().equals(handle)) {
            return invalidObservation();
        }
        // Do not accept evidence explicitly dated before this request or in the future.
        Instant now = clock.instant();
        if (observation.fetchedAt().isBefore(attempt.createdAt())
                || observation.fetchedAt().isAfter(now)
                || (observation.sourceObservedAt() != null
                && (observation.sourceObservedAt().isBefore(attempt.createdAt())
                || observation.sourceObservedAt().isAfter(observation.fetchedAt())))) {
            return invalidObservation();
        }
        boolean matches = matcher.matches(observation.biography(), attempt.digest(), attempt.keyVersion());
        // Biography stays outside storage and does not cross the completion boundary.
        return new Evaluation(new Evidence(handle, observation.providerAccountId(),
                observation.evidenceProvider()), matches, null);
    }

    private VerificationResult complete(
            VerificationStore.Transaction tx, Attempt attempt, Evaluation evaluation
    ) {
        Instant now = clock.instant();
        VerificationRequest request = request(tx, attempt.requestId());
        expire(tx, request, now);
        VerificationResult terminal = terminalResult(tx, request, now);
        if (terminal != null) {
            return terminal;
        }
        if (!request.ownsLease(attempt.leaseId(), now)) {
            return result(VerificationOutcome.STALE_ATTEMPT, request);
        }
        if (evaluation.failure() != null) {
            request.recordProviderFailure(attempt.leaseId(), now);
            tx.saveRequest(request);
            audit(tx, request.getTenantId(), request.getId(), PROVIDER_FAILED, now);
            return new VerificationResult(VerificationOutcome.PROVIDER_FAILURE,
                    VerificationView.from(request), null, evaluation.failure());
        }
        if (!evaluation.matches()) {
            request.recordMismatch(attempt.leaseId(), now);
            tx.saveRequest(request);
            audit(tx, request.getTenantId(), request.getId(), CODE_MISMATCH, now);
            return result(request.getStatus() == VerificationStatus.LOCKED
                    ? VerificationOutcome.LOCKED : VerificationOutcome.CODE_NOT_FOUND, request);
        }

        Evidence evidence = evaluation.evidence();
        List<VerifiedSocialAccount> existing = tx.findActiveAccounts(
                request.getPlatform(), evidence.handle(), evidence.providerAccountId(), now);
        // Conservative identity policy: never merge two existing links or replace
        // an active stable identity because a username was reassigned.
        if (existing.size() > 1 || (existing.size() == 1
                && (!existing.getFirst().getSubjectId().equals(request.getSubjectId())
                || conflictingIdentity(existing.getFirst(), evidence)))) {
            request.recordProviderFailure(attempt.leaseId(), now);
            tx.saveRequest(request);
            audit(tx, request.getTenantId(), request.getId(), ACCOUNT_CONFLICT, now);
            return result(VerificationOutcome.ACCOUNT_ALREADY_LINKED, request);
        }
        VerifiedSocialAccount account;
        if (existing.isEmpty()) {
            account = new VerifiedSocialAccount(UUID.randomUUID(), request.getTenantId(),
                    request.getSubjectId(), request.getPlatform(), evidence.handle(),
                    evidence.providerAccountId(), evidence.provider(), now, policy);
            tx.insertAccount(account);
        } else {
            // Reuse the same subject's active association without extending validity.
            account = existing.getFirst();
        }
        request.markVerified(attempt.leaseId(), now);
        tx.saveRequest(request);
        tx.linkVerifiedRequest(request.getId(), account.getId());
        audit(tx, request.getTenantId(), request.getId(), REQUEST_VERIFIED, now);
        return new VerificationResult(VerificationOutcome.VERIFIED, VerificationView.from(request),
                VerifiedAccountView.from(account, now), null);
    }

    private static boolean conflictingIdentity(VerifiedSocialAccount account, Evidence evidence) {
        return account.getProviderAccountId() != null && evidence.providerAccountId() != null
                && !account.getProviderAccountId().equals(evidence.providerAccountId());
    }

    private static VerificationResult terminalResult(
            VerificationStore.Transaction tx, VerificationRequest request, Instant now
    ) {
        return switch (request.getStatus()) {
            case VERIFIED -> new VerificationResult(VerificationOutcome.ALREADY_VERIFIED,
                    VerificationView.from(request),
                    VerifiedAccountView.from(tx.findAccountForRequest(request.getId())
                            .orElseThrow(() -> new IllegalStateException("Verified request has no account")), now),
                    null);
            case EXPIRED -> result(VerificationOutcome.EXPIRED, request);
            case CANCELED -> result(VerificationOutcome.CANCELED, request);
            case LOCKED -> result(VerificationOutcome.LOCKED, request);
            case PENDING, VERIFYING -> null;
        };
    }

    private static void expire(VerificationStore.Transaction tx, VerificationRequest request, Instant now) {
        if (request.expireIfDue(now)) {
            tx.saveRequest(request);
            audit(tx, request.getTenantId(), request.getId(), REQUEST_EXPIRED, now);
        }
    }

    private static void audit(VerificationStore.Transaction tx, UUID tenantId, UUID resourceId,
                              VerificationAuditEvent.Type type, Instant now) {
        tx.appendAudit(new VerificationAuditEvent(tenantId, resourceId, type, now));
    }

    private static VerificationRequest request(VerificationStore.Transaction tx, UUID id) {
        return tx.findRequest(id).orElseThrow(() -> new VerificationException(REQUEST_NOT_FOUND));
    }

    private static VerifiedSocialAccount account(VerificationStore.Transaction tx, UUID id) {
        return tx.findAccount(id).orElseThrow(() -> new VerificationException(ACCOUNT_NOT_FOUND));
    }

    private static VerificationResult result(VerificationOutcome outcome, VerificationRequest request) {
        return new VerificationResult(outcome, VerificationView.from(request), null, null);
    }

    private static ProfileFetchResult.Failed failure(ProfileFailureReason reason) {
        return new ProfileFetchResult.Failed(reason, false, null);
    }

    private static Evaluation invalidObservation() {
        return new Evaluation(null, false, failure(ProfileFailureReason.INVALID_PROVIDER_RESPONSE));
    }

    private static void requireIds(UUID tenantId, UUID resourceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
    }

    private static final class Claim {
        private final Attempt attempt;
        private final VerificationResult result;

        private Claim(Attempt attempt, VerificationResult result) {
            this.attempt = attempt;
            this.result = result;
        }

        public Attempt attempt() {
            return attempt;
        }

        public VerificationResult result() {
            return result;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Claim other)) {
                return false;
            }
            return Objects.equals(attempt, other.attempt)
                    && Objects.equals(result, other.result);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attempt, result);
        }

        @Override
        public String toString() {
            return "Claim[attempt=" + attempt + ", result=" + result + "]";
        }
    }

    private static final class Attempt {
        private final UUID requestId;
        private final UUID leaseId;
        private final Platform platform;
        private final String handle;
        private final byte[] digest;
        private final String keyVersion;
        private final Instant createdAt;

        private Attempt(
                UUID requestId,
                UUID leaseId,
                Platform platform,
                String handle,
                byte[] digest,
                String keyVersion,
                Instant createdAt
        ) {
            this.requestId = requestId;
            this.leaseId = leaseId;
            this.platform = platform;
            this.handle = handle;
            this.digest = digest;
            this.keyVersion = keyVersion;
            this.createdAt = createdAt;
        }

        @Override
        public String toString() {
            return "Attempt[redacted]";
        }

        public UUID requestId() {
            return requestId;
        }

        public UUID leaseId() {
            return leaseId;
        }

        public Platform platform() {
            return platform;
        }

        public String handle() {
            return handle;
        }

        public byte[] digest() {
            return digest;
        }

        public String keyVersion() {
            return keyVersion;
        }

        public Instant createdAt() {
            return createdAt;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Attempt other)) {
                return false;
            }
            return Objects.equals(requestId, other.requestId)
                    && Objects.equals(leaseId, other.leaseId)
                    && Objects.equals(platform, other.platform)
                    && Objects.equals(handle, other.handle)
                    && Objects.equals(digest, other.digest)
                    && Objects.equals(keyVersion, other.keyVersion)
                    && Objects.equals(createdAt, other.createdAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, leaseId, platform, handle, digest, keyVersion, createdAt);
        }
    }

    private static final class Evidence {
        private final String handle;
        private final String providerAccountId;
        private final String provider;

        private Evidence(String handle, String providerAccountId, String provider) {
            this.handle = handle;
            this.providerAccountId = providerAccountId;
            this.provider = provider;
        }

        public String handle() {
            return handle;
        }

        public String providerAccountId() {
            return providerAccountId;
        }

        public String provider() {
            return provider;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Evidence other)) {
                return false;
            }
            return Objects.equals(handle, other.handle)
                    && Objects.equals(providerAccountId, other.providerAccountId)
                    && Objects.equals(provider, other.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hash(handle, providerAccountId, provider);
        }

        @Override
        public String toString() {
            return "Evidence[handle=" + handle + ", providerAccountId=" + providerAccountId + ", provider=" + provider + "]";
        }
    }

    private static final class Evaluation {
        private final Evidence evidence;
        private final boolean matches;
        private final ProfileFetchResult.Failed failure;

        private Evaluation(Evidence evidence, boolean matches, ProfileFetchResult.Failed failure) {
            this.evidence = evidence;
            this.matches = matches;
            this.failure = failure;
        }

        public Evidence evidence() {
            return evidence;
        }

        public boolean matches() {
            return matches;
        }

        public ProfileFetchResult.Failed failure() {
            return failure;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Evaluation other)) {
                return false;
            }
            return Objects.equals(evidence, other.evidence)
                    && Objects.equals(matches, other.matches)
                    && Objects.equals(failure, other.failure);
        }

        @Override
        public int hashCode() {
            return Objects.hash(evidence, matches, failure);
        }

        @Override
        public String toString() {
            return "Evaluation[evidence=" + evidence + ", matches=" + matches + ", failure=" + failure + "]";
        }
    }
}
