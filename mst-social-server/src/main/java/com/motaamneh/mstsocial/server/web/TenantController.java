package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import com.motaamneh.mstsocial.server.web.dto.response.ApiErrorResponse;
import com.motaamneh.mstsocial.server.web.dto.response.TenantIdentityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tenant", description = "Identify the application authenticated by a tenant API key.")
@ApiResponse(responseCode = "default", description = "Safe problem response",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiErrorResponse.class)))
@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {
    @Operation(operationId = "getTenantIdentity", summary = "Read the authenticated tenant and credential IDs",
            description = "Use this first to check your tenant API key. Tenant identity is derived from the Bearer credential; it is never supplied by the caller.")
    @ApiResponse(responseCode = "200", description = "Authenticated tenant identity",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantIdentityResponse.class)))
    @GetMapping
    public ResponseEntity<TenantIdentityResponse> currentTenant(@Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TenantIdentityResponse(principal.tenantId(), principal.credentialId()));
    }
}
