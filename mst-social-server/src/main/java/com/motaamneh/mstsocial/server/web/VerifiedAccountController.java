package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.service.VerificationService;
import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import com.motaamneh.mstsocial.server.web.dto.response.ApiErrorResponse;
import com.motaamneh.mstsocial.server.web.dto.response.VerifiedAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

import static com.motaamneh.mstsocial.server.web.ApiRateLimiter.Operation.*;

@Tag(name = "Social identities", description = "Read and revoke verified account associations.")
@ApiResponse(responseCode = "default", description = "Safe problem response",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiErrorResponse.class)))
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(value = "/api/v1/social-identities", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerifiedAccountController {
    private final VerificationService verificationService;
    private final ApiRateLimiter rateLimiter;

    @Operation(operationId = "getVerifiedAccount", summary = "Read a verified social account association",
            description = "Returns expired and revoked associations too. Check active before treating an association as "
                    + "currently verified. Missing resources and resources belonging to another tenant both return 404.")
    @ApiResponse(responseCode = "200", description = "Account association with its current activity flag",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerifiedAccountResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<VerifiedAccountResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Parameter(description = "Resource UUID within the authenticated tenant",
                    example = "00000000-0000-4000-8000-000000000001") @PathVariable UUID id
    ) {
        rateLimiter.check(principal.tenantId(), READ);
        var account = verificationService.getVerifiedAccount(principal.tenantId(), id);
        log.atDebug().addKeyValue("event", "account.read").addKeyValue("accountId", account.accountId())
                .addKeyValue("active", account.active()).log("Account association read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VerifiedAccountResponse.from(account));
    }

    /** Revoke the association while preserving its historical account and request links. */
    @Operation(operationId = "revokeVerifiedAccount", summary = "Revoke a social account association",
            description = "Send no body. Preserves history and request links. Repeated revocation preserves the original "
                    + "revocation timestamp. Idempotency-Key is unsupported.")
    @ApiResponse(responseCode = "200", description = "Revoked association; active is false",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerifiedAccountResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<VerifiedAccountResponse> revoke(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Parameter(description = "Resource UUID within the authenticated tenant",
                    example = "00000000-0000-4000-8000-000000000001") @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), MUTATE);
        var account = verificationService.revokeVerifiedAccount(principal.tenantId(), id);
        log.atInfo().addKeyValue("event", "account.revoke.completed").addKeyValue("accountId", account.accountId())
                .addKeyValue("active", account.active()).addKeyValue("revokedAt", account.revokedAt())
                .log("Account revocation processed");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VerifiedAccountResponse.from(account));
    }
}
