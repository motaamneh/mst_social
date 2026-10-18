package com.motaamneh.mstsocial.server.web.dto.response;

import java.util.UUID;

public record TenantIdentityResponse(UUID tenantId, UUID credentialId) {
}
