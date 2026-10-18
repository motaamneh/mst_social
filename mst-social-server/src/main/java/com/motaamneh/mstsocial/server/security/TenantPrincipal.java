package com.motaamneh.mstsocial.server.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/** The tenant comes from the validated database credential, never from a request parameter. */
public final class TenantPrincipal implements Principal {
    private final UUID tenantId;
    private final UUID credentialId;

    public TenantPrincipal(UUID tenantId, UUID credentialId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        this.tenantId = tenantId;
        this.credentialId = credentialId;
    }

    @Override
    public String getName() {
        return tenantId.toString();
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID credentialId() {
        return credentialId;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof TenantPrincipal other)) {
            return false;
        }
        return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(credentialId, other.credentialId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, credentialId);
    }

    @Override
    public String toString() {
        return "TenantPrincipal[tenantId=" + tenantId + ", credentialId=" + credentialId + "]";
    }
}
