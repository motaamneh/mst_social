package com.motaamneh.mstsocial.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

/** Registered only inside Spring Security's filter chain, never as a servlet filter bean. */
@Slf4j
public final class TenantApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String SCHEME = "Bearer ";
    private static final RequestMatcher API_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**")
    );
    private final AuthenticationManager authenticationManager;

    public TenantApiKeyAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !API_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String credential = readCredential(request);
        if (credential == null) {
            log.atWarn().addKeyValue("event", "authentication.failed")
                    .addKeyValue("reason", "MISSING_OR_INVALID_AUTHORIZATION_HEADER")
                    .log("Tenant authentication rejected");
            SecurityErrorResponse.UNAUTHORIZED.writeTo(response);
            return;
        }

        var candidate = UsernamePasswordAuthenticationToken.unauthenticated("tenant-api-key", credential);
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(candidate);
        } catch (AuthenticationServiceException exception) {
            SecurityErrorResponse.AUTHENTICATION_UNAVAILABLE.writeTo(response);
            return;
        } catch (AuthenticationException exception) {
            SecurityErrorResponse.UNAUTHORIZED.writeTo(response);
            return;
        } finally {
            candidate.eraseCredentials();
        }

        var context = SecurityContextHolder.createEmptyContext();
        TenantPrincipal principal = (TenantPrincipal) authenticated.getPrincipal();
        MDC.put("tenantId", principal.tenantId().toString());
        MDC.put("credentialId", principal.credentialId().toString());
        log.atDebug().addKeyValue("event", "authentication.succeeded").log("Tenant authenticated");
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        try {
            // Downstream failures must not be mistaken for authentication failures.
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String readCredential(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) {
            return null;
        }
        String header = headers.nextElement();
        if (headers.hasMoreElements()
                || header.length() != SCHEME.length() + TenantApiKeyCodec.CREDENTIAL_LENGTH
                || !header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            return null;
        }
        return header.substring(SCHEME.length());
    }
}
