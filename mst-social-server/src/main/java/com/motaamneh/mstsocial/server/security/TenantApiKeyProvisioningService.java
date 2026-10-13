package com.motaamneh.mstsocial.server.security;

import com.motaamneh.mstsocial.server.persistence.TenantApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Local administrator operations. This service is not exposed through an HTTP controller. */
@Service
public final class TenantApiKeyProvisioningService {
    private final TenantApiKeyRepository repository;
    private final TenantApiKeyCodec codec;
    private final Clock clock;

    public TenantApiKeyProvisioningService(TenantApiKeyRepository repository, TenantApiKeyCodec codec, Clock clock) {
        this.repository = repository;
        this.codec = codec;
        this.clock = clock;
    }

    public TenantApiKeyCodec.GeneratedCredential create(UUID tenantId, String name, Instant expiresAt) {
        requireNoTransaction();
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiry = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MICROS);
        var generated = codec.generate(tenantId, name, createdAt, expiry);
        // One autocommitted statement: return the plaintext only after the insert succeeds.
        repository.insert(generated.metadata());
        return generated;
    }

    public boolean revoke(UUID tenantId, UUID credentialId) {
        requireNoTransaction();
        return repository.revoke(tenantId, credentialId, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Credential provisioning must run outside an existing transaction");
        }
    }
}
