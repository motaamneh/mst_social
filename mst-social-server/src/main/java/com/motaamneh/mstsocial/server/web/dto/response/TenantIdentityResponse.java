package com.motaamneh.mstsocial.server.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Identity derived from the authenticated tenant credential.")
public record TenantIdentityResponse(
        @Schema(description = "Application tenant UUID; inferred from the credential.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "00000000-0000-4000-8000-000000000010")
        UUID tenantId,
        @Schema(description = "UUID of the API key used for this call.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "00000000-0000-4000-8000-000000000020")
        UUID credentialId
) {
}
