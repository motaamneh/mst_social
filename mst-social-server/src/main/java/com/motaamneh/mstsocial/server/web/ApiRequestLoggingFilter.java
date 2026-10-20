package com.motaamneh.mstsocial.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Registered before tenant authentication, only in the security chain. */
@Slf4j
public final class ApiRequestLoggingFilter extends OncePerRequestFilter {
    private static final RequestMatcher API_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**"));
    private static final Map<String, RequestMatcher> ROUTES = Map.ofEntries(
            route("/api/v1/tenant"),
            route("/api/v1/verifications"),
            route("/api/v1/verifications/{id}"),
            route("/api/v1/verifications/{id}/verify"),
            route("/api/v1/social-identities/{id}"));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !API_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String route = ROUTES.entrySet().stream()
                .filter(entry -> entry.getValue().matches(request))
                .map(Map.Entry::getKey).findFirst().orElse("UNMATCHED_API_PATH");
        String method = switch (request.getMethod()) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> request.getMethod();
            default -> "OTHER";
        };
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        boolean completed = false;
        try {
            String httpRequestId = UUID.randomUUID().toString();
            // Generate our own ID; a caller-controlled header must not become trusted log content.
            MDC.put("httpRequestId", httpRequestId);
            MDC.remove("tenantId");
            MDC.remove("credentialId");
            response.setHeader("X-Request-Id", httpRequestId);

            log.atDebug().addKeyValue("event", "http.request.started")
                    .addKeyValue("method", method).addKeyValue("route", route).log("API request started");
            chain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                int status = completed || response.isCommitted() ? response.getStatus() : 500;
                var event = status >= 500 ? log.atError() : status >= 400 ? log.atWarn() : log.atInfo();
                event.addKeyValue("event", "http.request.completed")
                        .addKeyValue("method", method).addKeyValue("route", route)
                        .addKeyValue("status", status).addKeyValue("completedNormally", completed)
                        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                        .log("API request completed");
            } finally {
                // Servlet worker threads are reused. Never let one tenant's context leak to the next request.
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }

    private static Map.Entry<String, RequestMatcher> route(String pattern) {
        return Map.entry(pattern, PathPatternRequestMatcher.withDefaults().matcher(pattern));
    }
}
