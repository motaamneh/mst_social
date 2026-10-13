package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {
    @GetMapping
    public ResponseEntity<TenantIdentity> currentTenant(@AuthenticationPrincipal TenantPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TenantIdentity(principal.tenantId(), principal.credentialId()));
    }

    public record TenantIdentity(UUID tenantId, UUID credentialId) {
    }
}
