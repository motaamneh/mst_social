package com.motaamneh.mstsocial.server.security;

import com.motaamneh.mstsocial.server.persistence.TenantApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import com.motaamneh.mstsocial.server.observability.SafeExceptionDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;

@Service
@Slf4j
public final class TenantAuthenticationService implements AuthenticationManager {
    private static final byte[] UNKNOWN_KEY_DIGEST = new byte[32];
    private final TenantApiKeyRepository repository;
    private final TenantApiKeyCodec codec;
    private final Clock clock;

    public TenantAuthenticationService(TenantApiKeyRepository repository, TenantApiKeyCodec codec, Clock clock) {
        this.repository = repository;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication.getCredentials() instanceof String credential)) {
            logRejected("MALFORMED_CREDENTIAL");
            throw invalidCredential();
        }
        var parsed = codec.parse(credential).orElseThrow(() -> {
            logRejected("MALFORMED_CREDENTIAL");
            return invalidCredential();
        });
        TenantApiKey stored;
        try {
            stored = repository.findById(parsed.id()).orElse(null);
        } catch (DataAccessException exception) {
            SafeExceptionDetails.addTo(log.atError(), exception).addKeyValue("event", "authentication.failed")
                    .addKeyValue("reason", "STORAGE_UNAVAILABLE")
                    .log("Credential lookup failed");
            // Do not convert a database outage into an incorrect "bad key" response.
            throw new AuthenticationServiceException("Credential lookup is unavailable");
        }
        boolean matches = MessageDigest.isEqual(
                stored == null ? UNKNOWN_KEY_DIGEST : stored.getSecretDigest(), parsed.digest()
        );
        var now = clock.instant();
        if (!matches || stored == null || !stored.isActive(now)) {
            String reason;
            if (stored == null) {
                reason = "UNKNOWN_CREDENTIAL";
            } else if (!matches) {
                reason = "SECRET_MISMATCH";
            } else if (stored.getRevokedAt() != null) {
                reason = "REVOKED_CREDENTIAL";
            } else if (stored.getCreatedAt().isAfter(now)) {
                reason = "NOT_YET_ACTIVE";
            } else {
                reason = "EXPIRED_CREDENTIAL";
            }
            logRejected(reason);
            throw invalidCredential();
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                new TenantPrincipal(stored.getTenantId(), stored.getId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT"))
        );
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Invalid tenant API key");
    }

    private static void logRejected(String reason) {
        // These distinctions are for server operators only; the public response remains generic.
        log.atWarn().addKeyValue("event", "authentication.failed").addKeyValue("reason", reason)
                .log("Tenant authentication rejected");
    }
}
