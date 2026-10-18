package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.service.VerificationService;
import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import com.motaamneh.mstsocial.server.web.dto.response.VerifiedAccountResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

import static com.motaamneh.mstsocial.server.web.ApiRateLimiter.Operation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/social-identities", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerifiedAccountController {
    private final VerificationService verificationService;
    private final ApiRateLimiter rateLimiter;

    @GetMapping("/{id}")
    public ResponseEntity<VerifiedAccountResponse> get(
            @AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id
    ) {
        rateLimiter.check(principal.tenantId(), READ);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(VerifiedAccountResponse.from(verificationService.getVerifiedAccount(principal.tenantId(), id)));
    }

    /** Revoke the association while preserving its historical account and request links. */
    @DeleteMapping("/{id}")
    public ResponseEntity<VerifiedAccountResponse> revoke(
            @AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), MUTATE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(VerifiedAccountResponse.from(verificationService.revokeVerifiedAccount(principal.tenantId(), id)));
    }
}
