package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.Platform;
import java.util.Objects;

/** The tenant is supplied separately by the authenticated application. */
public final class CreateVerificationCommand {
    private final String subjectId;
    private final Platform platform;
    private final String handle;

    public CreateVerificationCommand(String subjectId, Platform platform, String handle) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        this.subjectId = subjectId;
        this.platform = platform;
        this.handle = handle;
    }

    public String subjectId() {
        return subjectId;
    }

    public Platform platform() {
        return platform;
    }

    public String handle() {
        return handle;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof CreateVerificationCommand other)) {
            return false;
        }
        return Objects.equals(subjectId, other.subjectId)
                && Objects.equals(platform, other.platform)
                && Objects.equals(handle, other.handle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, platform, handle);
    }

    @Override
    public String toString() {
        return "CreateVerificationCommand[subjectId=" + subjectId + ", platform=" + platform + ", handle=" + handle + "]";
    }
}
