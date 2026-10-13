package com.motaamneh.mstsocial.server.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/** The tenant comes from the validated database credential, never from a request parameter. */
public record TenantPrincipal(UUID tenantId, UUID credentialId) implements Principal {
    public TenantPrincipal {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(credentialId, "credentialId must not be null");
    }

    @Override
    public String getName() {
        return tenantId.toString();
    }
}
