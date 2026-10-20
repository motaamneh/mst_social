package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.core.service.CreateVerificationCommand;
import com.motaamneh.mstsocial.core.service.VerificationService;
import com.motaamneh.mstsocial.server.security.TenantPrincipal;
import com.motaamneh.mstsocial.server.web.dto.request.CreateVerificationRequest;
import com.motaamneh.mstsocial.server.web.dto.response.ApiErrorResponse;
import com.motaamneh.mstsocial.server.web.dto.response.VerificationAttemptResponse;
import com.motaamneh.mstsocial.server.web.dto.response.VerificationCreatedResponse;
import com.motaamneh.mstsocial.server.web.dto.response.VerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static com.motaamneh.mstsocial.server.web.ApiRateLimiter.Operation.*;

@Tag(name = "Verifications", description = "Create, inspect, verify, and cancel bio verification requests.")
@ApiResponse(responseCode = "default", description = "Safe problem response",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiErrorResponse.class)))
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(value = "/api/v1/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerificationController {
    private final VerificationService verificationService;
    private final VerificationHttpMapper httpMapper;
    private final ApiRateLimiter rateLimiter;

    @Operation(operationId = "createVerification", summary = "Create a verification and return its code once",
            description = "Does not call SearchAPI. Save the returned code and place it in the profile bio before expiry "
                    + "(default: 15 minutes). GET cannot recover the code. Duplicate active requests return 409. Default "
                    + "active limits: three per subject and three per platform/handle. Idempotency-Key is unsupported.")
    @ApiResponse(responseCode = "201", description = "Created; plaintext code is returned only here",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerificationCreatedResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificationCreatedResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Valid @RequestBody CreateVerificationRequest input,
            HttpServletRequest request
    ) {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        rateLimiter.check(principal.tenantId(), CREATE);
        var created = verificationService.createVerification(principal.tenantId(),
                new CreateVerificationCommand(input.getSubjectId(), input.getPlatform(), input.getHandle()));
        log.atInfo().addKeyValue("event", "verification.created")
                .addKeyValue("verificationId", created.requestId()).addKeyValue("platform", input.getPlatform())
                .addKeyValue("expiresAt", created.expiresAt()).log("Verification created");
        URI location = URI.create(request.getContextPath() + "/api/v1/verifications/" + created.requestId());
        return ResponseEntity.created(location).cacheControl(CacheControl.noStore())
                .body(VerificationCreatedResponse.from(created));
    }

    @Operation(operationId = "getVerification", summary = "Read the current verification status",
            description = "Does not call SearchAPI. Expired and terminal requests remain readable. The plaintext code is never "
                    + "returned. A missing resource and one belonging to another tenant both return 404.")
    @ApiResponse(responseCode = "200", description = "Current verification status",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerificationResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<VerificationResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Parameter(description = "Resource UUID within the authenticated tenant",
                    example = "00000000-0000-4000-8000-000000000001") @PathVariable UUID id
    ) {
        rateLimiter.check(principal.tenantId(), READ);
        var verification = verificationService.getVerification(principal.tenantId(), id);
        log.atDebug().addKeyValue("event", "verification.read").addKeyValue("verificationId", id)
                .addKeyValue("state", verification.status()).addKeyValue("failedAttempts", verification.failedAttempts())
                .log("Verification status read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VerificationResponse.from(verification));
    }

    @Operation(operationId = "verifyProfile", summary = "Verify the code against the public profile bio",
            description = "Send no body (not even {}). One eligible attempt calls SearchAPI once and may consume provider "
                    + "usage; there is no automatic retry. Private profiles are allowed if their username and bio are "
                    + "available. Mismatches 1–9 leave PENDING; mismatch 10 returns LOCKED (409). Provider failures do not "
                    + "consume mismatch attempts. Success lasts one calendar year by default. An already verified request "
                    + "returns its original association without a provider call or extending validity; check "
                    + "account.active. Idempotency-Key is unsupported.")
    @ApiResponse(responseCode = "200", description = "VERIFIED or ALREADY_VERIFIED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerificationAttemptResponse.class)))
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verify(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Parameter(description = "Resource UUID within the authenticated tenant",
                    example = "00000000-0000-4000-8000-000000000001") @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), VERIFY);
        log.atDebug().addKeyValue("event", "verification.started").addKeyValue("verificationId", id)
                .log("Verification attempt started");
        return httpMapper.toResponse(verificationService.verify(principal.tenantId(), id));
    }

    /** Cancel the request; terminal requests are returned unchanged by the core. */
    @Operation(operationId = "cancelVerification", summary = "Cancel a pending or in-progress verification",
            description = "Send no body. Terminal requests are returned unchanged. Canceling does not revoke an account "
                    + "association; use DELETE /api/v1/social-identities/{id} for that. Idempotency-Key is unsupported.")
    @ApiResponse(responseCode = "200", description = "Current verification status after cancellation",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerificationResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<VerificationResponse> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal TenantPrincipal principal,
            @Parameter(description = "Resource UUID within the authenticated tenant",
                    example = "00000000-0000-4000-8000-000000000001") @PathVariable UUID id,
            HttpServletRequest request
    ) throws IOException {
        ApiRequestChecks.requireNoIdempotencyKey(request);
        ApiRequestChecks.requireEmptyBody(request);
        rateLimiter.check(principal.tenantId(), MUTATE);
        var verification = verificationService.cancelVerification(principal.tenantId(), id);
        log.atInfo().addKeyValue("event", "verification.cancel.completed").addKeyValue("verificationId", id)
                .addKeyValue("state", verification.status()).log("Cancellation processed");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VerificationResponse.from(verification));
    }
}
