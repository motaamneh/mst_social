package com.motaamneh.mstsocial.server.security;

import com.motaamneh.mstsocial.server.persistence.TenantApiKeyRepository;
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
            throw invalidCredential();
        }
        var parsed = codec.parse(credential).orElseThrow(TenantAuthenticationService::invalidCredential);
        TenantApiKey stored;
        try {
            stored = repository.findById(parsed.id()).orElse(null);
        } catch (DataAccessException exception) {
            // Do not convert a database outage into an incorrect "bad key" response.
            throw new AuthenticationServiceException("Credential lookup is unavailable");
        }
        boolean matches = MessageDigest.isEqual(
                stored == null ? UNKNOWN_KEY_DIGEST : stored.getSecretDigest(), parsed.digest()
        );
        if (!matches || stored == null || !stored.isActive(clock.instant())) {
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
}
