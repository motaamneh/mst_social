package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.service.CreateVerificationCommand;
import com.motaamneh.mstsocial.core.service.VerificationService;
import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import com.motaamneh.mstsocial.server.web.dto.request.CreateVerificationRequest;
import com.motaamneh.mstsocial.server.web.dto.response.VerificationCreatedResponse;
import com.motaamneh.mstsocial.server.web.dto.response.VerificationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static com.motaamneh.mstsocial.server.web.ApiRateLimiter.Operation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerificationController {
    private final VerificationService verificationService;
    private final VerificationHttpMapper httpMapper;
    private final ApiRateLimiter rateLimiter;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificationCreatedResponse> create(
            @AuthenticationPrincipal TenantPrincipal principal,
            @Valid @RequestBody CreateVerificationRequest input,
            HttpServletRequest request
    ) {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        rateLimiter.check(principal.tenantId(), CREATE);
        var created = verificationService.createVerification(principal.tenantId(),
                new CreateVerificationCommand(input.getSubjectId(), input.getPlatform(), input.getHandle()));
        URI location = URI.create(request.getContextPath() + "/api/v1/verifications/" + created.requestId());
        return ResponseEntity.created(location).cacheControl(CacheControl.noStore())
                .body(VerificationCreatedResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VerificationResponse> get(
            @AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id
    ) {
        rateLimiter.check(principal.tenantId(), READ);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(VerificationResponse.from(verificationService.getVerification(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verify(
            @AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), VERIFY);
        return httpMapper.toResponse(verificationService.verify(principal.tenantId(), id));
    }

    /** Cancel the request; terminal requests are returned unchanged by the core. */
    @DeleteMapping("/{id}")
    public ResponseEntity<VerificationResponse> cancel(
            @AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), MUTATE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(VerificationResponse.from(verificationService.cancelVerification(principal.tenantId(), id)));
    }
}
