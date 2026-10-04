package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.Platform;
import java.util.Objects;

/** The tenant is supplied separately by the authenticated application. */
public record CreateVerificationCommand(String subjectId, Platform platform, String handle) {
    public CreateVerificationCommand {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
    }
}
