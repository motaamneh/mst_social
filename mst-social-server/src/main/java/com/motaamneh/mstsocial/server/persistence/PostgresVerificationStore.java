package com.motaamneh.mstsocial.server.persistence;

import com.motaamneh.mstsocial.core.model.*;
import com.motaamneh.mstsocial.core.port.VerificationStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** JDBC adapter with tenant locks, explicit saves, and commit-before-return semantics. */
@Repository
public class PostgresVerificationStore implements VerificationStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DataSource dataSource;

    public PostgresVerificationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactions.setTimeout(30);
    }

    @Override
    public <T> T inTransaction(UUID tenantId, Function<Transaction, T> work) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(work, "work must not be null");
        // Joining a host transaction could keep its locks open during provider HTTP.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Call VerificationStore outside an existing transaction");
        }
        return transactions.execute(status -> {
            List<UUID> tenants = jdbc.query(
                    "SELECT id FROM tenants WHERE id = :tenant FOR UPDATE",
                    new MapSqlParameterSource("tenant", tenantId),
                    (rs, row) -> rs.getObject("id", UUID.class));
            if (tenants.isEmpty()) {
                throw new IllegalArgumentException("Unknown tenant");
            }
            Session session = new Session(tenantId);
            try {
                return work.apply(session);
            } finally {
                session.open = false;
            }
        });
    }

    private final class Session implements Transaction {
        private final UUID tenantId;
        private final Thread owner = Thread.currentThread();
        private final Map<VerificationRequest, Long> requestVersions = new IdentityHashMap<>();
        private final Map<VerifiedSocialAccount, Long> accountVersions = new IdentityHashMap<>();
        private boolean open = true;

        private Session(UUID tenantId) {
            this.tenantId = tenantId;
        }

        private MapSqlParameterSource params() {
            if (!open || Thread.currentThread() != owner) {
                throw new IllegalStateException("Transaction session is no longer usable");
            }
            return new MapSqlParameterSource("tenant", tenantId);
        }

        private void requireTenant(UUID actual) {
            params();
            if (!tenantId.equals(actual)) {
                throw new IllegalArgumentException("Cross-tenant write is forbidden");
            }
        }

        @Override
        public Optional<VerificationRequest> findRequest(UUID requestId) {
            return one(jdbc.query(
                    "SELECT * FROM verification_requests WHERE tenant_id = :tenant AND id = :id",
                    params().addValue("id", Objects.requireNonNull(requestId)), REQUEST_MAPPER))
                    .map(this::track);
        }

        @Override
        public boolean hasActiveRequest(String subjectId, Platform platform, String handle, Instant now) {
            return count("""
                    SELECT count(*) FROM verification_requests
                    WHERE tenant_id = :tenant AND subject_id = :subject
                      AND platform = :platform AND normalized_handle = :handle
                      AND status IN ('PENDING','VERIFYING') AND expires_at > :now
                    """, time(params().addValue("subject", subjectId)
                    .addValue("platform", platform.name()).addValue("handle", handle), "now", now)) > 0;
        }

        @Override
        public long countActiveRequestsForSubject(String subjectId, Instant now) {
            return count("""
                    SELECT count(*) FROM verification_requests
                    WHERE tenant_id = :tenant AND subject_id = :subject
                      AND status IN ('PENDING','VERIFYING') AND expires_at > :now
                    """, time(params().addValue("subject", subjectId), "now", now));
        }

        @Override
        public long countActiveRequestsForHandle(Platform platform, String handle, Instant now) {
            return count("""
                    SELECT count(*) FROM verification_requests
                    WHERE tenant_id = :tenant AND platform = :platform AND normalized_handle = :handle
                      AND status IN ('PENDING','VERIFYING') AND expires_at > :now
                    """, time(params().addValue("platform", platform.name())
                    .addValue("handle", handle), "now", now));
        }

        @Override
        public void insertRequest(VerificationRequest request) {
            requireTenant(request.getTenantId());
            VerificationRequest.Snapshot s = request.snapshot();
            if (s.version() != 0 || s.status() != VerificationStatus.PENDING || s.failedAttempts() != 0) {
                throw new IllegalArgumentException("Only new requests may be inserted");
            }
            jdbc.update("""
                    INSERT INTO verification_requests (
                        tenant_id, id, subject_id, platform, normalized_handle, code_digest,
                        code_key_version, created_at, expires_at, max_failed_attempts, status,
                        failed_attempts, verified_at, canceled_at, version, verification_lease_id, lease_expires_at
                    ) VALUES (
                        :tenant, :id, :subject, :platform, :handle, :digest, :keyVersion,
                        :createdAt, :expiresAt, :maxAttempts, :status, :attempts,
                        :verifiedAt, :canceledAt, :version, :leaseId, :leaseExpiresAt
                    )
                    """, requestParams(s));
            track(request);
        }

        @Override
        public void saveRequest(VerificationRequest request) {
            requireTenant(request.getTenantId());
            long expected = expectedVersion(requestVersions, request, request.getVersion());
            changed(jdbc.update("""
                    UPDATE verification_requests SET status = :status, failed_attempts = :attempts,
                        verified_at = :verifiedAt, canceled_at = :canceledAt, version = :version,
                        verification_lease_id = :leaseId, lease_expires_at = :leaseExpiresAt
                    WHERE tenant_id = :tenant AND id = :id AND version = :expectedVersion
                    """, requestParams(request.snapshot()).addValue("expectedVersion", expected)));
            track(request);
        }

        @Override
        public Optional<VerifiedSocialAccount> findAccount(UUID accountId) {
            return one(jdbc.query(
                    "SELECT * FROM verified_accounts WHERE tenant_id = :tenant AND id = :id",
                    params().addValue("id", Objects.requireNonNull(accountId)), ACCOUNT_MAPPER))
                    .map(this::track);
        }

        @Override
        public List<VerifiedSocialAccount> findActiveAccounts(
                Platform platform, String canonicalHandle, String providerAccountId, Instant now
        ) {
            List<VerifiedSocialAccount> accounts = jdbc.query("""
                    SELECT * FROM verified_accounts
                    WHERE tenant_id = :tenant AND platform = :platform
                      AND (canonical_handle = :handle OR provider_account_id = :providerId)
                      AND revoked_at IS NULL AND verified_at <= :now AND valid_until > :now
                    """, time(params().addValue("platform", platform.name())
                    .addValue("handle", canonicalHandle)
                    .addValue("providerId", providerAccountId, Types.VARCHAR), "now", now), ACCOUNT_MAPPER);
            accounts.forEach(this::track);
            return List.copyOf(accounts);
        }

        @Override
        public void insertAccount(VerifiedSocialAccount account) {
            requireTenant(account.getTenantId());
            VerifiedSocialAccount.Snapshot s = account.snapshot();
            if (s.version() != 0 || s.revokedAt() != null) {
                throw new IllegalArgumentException("Only new accounts may be inserted");
            }
            jdbc.update("""
                    INSERT INTO verified_accounts (
                        tenant_id, id, subject_id, platform, canonical_handle, provider_account_id,
                        evidence_provider, verified_at, valid_until, revoked_at, version
                    ) VALUES (
                        :tenant, :id, :subject, :platform, :handle, :providerId,
                        :provider, :verifiedAt, :validUntil, :revokedAt, :version
                    )
                    """, accountParams(s));
            track(account);
        }

        @Override
        public void saveAccount(VerifiedSocialAccount account) {
            requireTenant(account.getTenantId());
            long expected = expectedVersion(accountVersions, account, account.getVersion());
            changed(jdbc.update("""
                    UPDATE verified_accounts SET revoked_at = :revokedAt, version = :version
                    WHERE tenant_id = :tenant AND id = :id AND version = :expectedVersion
                    """, accountParams(account.snapshot()).addValue("expectedVersion", expected)));
            track(account);
        }

        @Override
        public void linkVerifiedRequest(UUID requestId, UUID accountId) {
            // An existing link is immutable. Duplicate calls fail instead of replacing it.
            changed(jdbc.update("""
                    INSERT INTO verification_account_links (tenant_id, request_id, account_id)
                    SELECT r.tenant_id, r.id, a.id
                    FROM verification_requests r JOIN verified_accounts a
                      ON a.tenant_id = r.tenant_id AND a.subject_id = r.subject_id AND a.platform = r.platform
                    WHERE r.tenant_id = :tenant AND r.id = :requestId
                      AND a.id = :accountId AND r.status = 'VERIFIED'
                    """, params().addValue("requestId", Objects.requireNonNull(requestId))
                    .addValue("accountId", Objects.requireNonNull(accountId))));
        }

        @Override
        public Optional<VerifiedSocialAccount> findAccountForRequest(UUID requestId) {
            return one(jdbc.query("""
                    SELECT a.* FROM verified_accounts a JOIN verification_account_links l
                      ON a.tenant_id = l.tenant_id AND a.id = l.account_id
                    WHERE l.tenant_id = :tenant AND l.request_id = :requestId
                    """, params().addValue("requestId", Objects.requireNonNull(requestId)), ACCOUNT_MAPPER))
                    .map(this::track);
        }

        @Override
        public void appendAudit(VerificationAuditEvent event) {
            requireTenant(event.tenantId());
            jdbc.update("""
                    INSERT INTO verification_audit_events (tenant_id, resource_id, event_type, occurred_at)
                    VALUES (:tenant, :resourceId, :eventType, :occurredAt)
                    """, time(params().addValue("resourceId", event.resourceId())
                    .addValue("eventType", event.type().name()), "occurredAt", event.occurredAt()));
        }

        private long count(String sql, MapSqlParameterSource values) {
            return Objects.requireNonNull(jdbc.queryForObject(sql, values, Long.class));
        }

        private VerificationRequest track(VerificationRequest request) {
            requestVersions.put(request, request.getVersion());
            return request;
        }

        private VerifiedSocialAccount track(VerifiedSocialAccount account) {
            accountVersions.put(account, account.getVersion());
            return account;
        }

        private MapSqlParameterSource requestParams(VerificationRequest.Snapshot s) {
            MapSqlParameterSource p = params().addValue("id", s.id()).addValue("subject", s.subjectId())
                    .addValue("platform", s.platform().name()).addValue("handle", s.normalizedHandle())
                    .addValue("digest", s.codeDigest()).addValue("keyVersion", s.codeKeyVersion())
                    .addValue("maxAttempts", s.maxFailedAttempts()).addValue("status", s.status().name())
                    .addValue("attempts", s.failedAttempts()).addValue("version", s.version())
                    .addValue("leaseId", s.verificationLeaseId(), Types.OTHER);
            time(p, "createdAt", s.createdAt());
            time(p, "expiresAt", s.expiresAt());
            time(p, "verifiedAt", s.verifiedAt());
            time(p, "canceledAt", s.canceledAt());
            return time(p, "leaseExpiresAt", s.leaseExpiresAt());
        }

        private MapSqlParameterSource accountParams(VerifiedSocialAccount.Snapshot s) {
            MapSqlParameterSource p = params().addValue("id", s.id()).addValue("subject", s.subjectId())
                    .addValue("platform", s.platform().name()).addValue("handle", s.canonicalHandle())
                    .addValue("providerId", s.providerAccountId(), Types.VARCHAR)
                    .addValue("provider", s.evidenceProvider()).addValue("version", s.version());
            time(p, "verifiedAt", s.verifiedAt());
            time(p, "validUntil", s.validUntil());
            return time(p, "revokedAt", s.revokedAt());
        }
    }

    private static <T> long expectedVersion(Map<T, Long> tracked, T entity, long current) {
        Long expected = tracked.get(entity);
        if (expected == null || current < expected) {
            throw new IllegalArgumentException("Save requires an entity loaded or inserted in this transaction");
        }
        return expected;
    }

    private static void changed(int count) {
        if (count != 1) {
            throw new IllegalStateException("Storage write rejected: missing, incompatible, or stale entity");
        }
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Expected a unique stored entity");
        }
        return rows.stream().findFirst();
    }

    private static MapSqlParameterSource time(MapSqlParameterSource p, String name, Instant value) {
        return p.addValue(name, value == null ? null : value.atOffset(ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        OffsetDateTime value = rs.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static final RowMapper<VerificationRequest> REQUEST_MAPPER = (rs, row) ->
            VerificationRequest.restore(new VerificationRequest.Snapshot(
                    rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getString("subject_id"), Platform.valueOf(rs.getString("platform")),
                    rs.getString("normalized_handle"), rs.getBytes("code_digest"), rs.getString("code_key_version"),
                    instant(rs, "created_at"), instant(rs, "expires_at"), rs.getInt("max_failed_attempts"),
                    VerificationStatus.valueOf(rs.getString("status")), rs.getInt("failed_attempts"),
                    instant(rs, "verified_at"), instant(rs, "canceled_at"), rs.getLong("version"),
                    rs.getObject("verification_lease_id", UUID.class), instant(rs, "lease_expires_at")));

    private static final RowMapper<VerifiedSocialAccount> ACCOUNT_MAPPER = (rs, row) ->
            VerifiedSocialAccount.restore(new VerifiedSocialAccount.Snapshot(
                    rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getString("subject_id"), Platform.valueOf(rs.getString("platform")),
                    rs.getString("canonical_handle"), rs.getString("provider_account_id"),
                    rs.getString("evidence_provider"), instant(rs, "verified_at"), instant(rs, "valid_until"),
                    instant(rs, "revoked_at"), rs.getLong("version")));
}
