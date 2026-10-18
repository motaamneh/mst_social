package com.motaamneh.mstsocial.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal transactional audit metadata; never contains a code, handle, or biography. */
public final class VerificationAuditEvent {
    private final UUID tenantId;
    private final UUID resourceId;
    private final Type type;
    private final Instant occurredAt;

    public enum Type {
        REQUEST_CREATED, ATTEMPT_STARTED, REQUEST_EXPIRED, REQUEST_CANCELED,
        CODE_MISMATCH, PROVIDER_FAILED, ACCOUNT_CONFLICT, REQUEST_VERIFIED, ACCOUNT_REVOKED
    }

    public VerificationAuditEvent(UUID tenantId, UUID resourceId, Type type, Instant occurredAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.tenantId = tenantId;
        this.resourceId = resourceId;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public Type type() {
        return type;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof VerificationAuditEvent other)) {
            return false;
        }
        return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(resourceId, other.resourceId)
                && Objects.equals(type, other.type)
                && Objects.equals(occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, resourceId, type, occurredAt);
    }

    @Override
    public String toString() {
        return "VerificationAuditEvent[tenantId=" + tenantId + ", resourceId=" + resourceId + ", type=" + type + ", occurredAt=" + occurredAt + "]";
    }
}
