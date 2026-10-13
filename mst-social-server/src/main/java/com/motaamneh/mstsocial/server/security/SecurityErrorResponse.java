package com.motaamneh.mstsocial.server.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Fixed public errors shared by the filter and Spring Security's exception handlers. */
public enum SecurityErrorResponse {
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    AUTHENTICATION_UNAVAILABLE(503, "Service Unavailable");

    private final int status;
    private final String title;

    SecurityErrorResponse(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public void writeTo(HttpServletResponse response) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        if (this == UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"mst-social\"");
        }
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"code\":\"" + name() + "\"}");
    }
}
