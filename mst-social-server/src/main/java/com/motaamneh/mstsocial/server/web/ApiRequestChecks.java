package com.motaamneh.mstsocial.server.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

final class ApiRequestChecks {
    private ApiRequestChecks() {
    }

    static void requireEmptyBody(HttpServletRequest request) throws IOException {
        // Inspect only one byte: an unsupported body must not be buffered or parsed.
        if (request.getInputStream().read() != -1) {
            throw new ApiRequestException("UNEXPECTED_BODY", "This operation requires an empty request body.");
        }
    }

    static void requireNoIdempotencyKey(HttpServletRequest request) {
        if (request.getHeader("Idempotency-Key") != null) {
            throw new ApiRequestException("IDEMPOTENCY_NOT_SUPPORTED",
                    "Idempotency-Key replay is not supported by this API version.");
        }
    }
}
