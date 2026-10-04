package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal transactional audit metadata; never contains a code, handle, or biography. */
public record VerificationAuditEvent(
        UUID tenantId, UUID resourceId, Type type, Instant occurredAt
) {
    public enum Type {
        REQUEST_CREATED, ATTEMPT_STARTED, REQUEST_EXPIRED, REQUEST_CANCELED,
        CODE_MISMATCH, PROVIDER_FAILED, ACCOUNT_CONFLICT, REQUEST_VERIFIED, ACCOUNT_REVOKED
    }

    public VerificationAuditEvent {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
