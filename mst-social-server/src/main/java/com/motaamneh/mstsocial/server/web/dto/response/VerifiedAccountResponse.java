package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.service.VerifiedAccountView;
import java.time.Instant;
import java.util.UUID;

public record VerifiedAccountResponse(
        UUID accountId, String subjectId, Platform platform, String canonicalHandle,
        String providerAccountId, String evidenceProvider,
        Instant verifiedAt, Instant validUntil, Instant revokedAt, boolean active
) {
    public static VerifiedAccountResponse from(VerifiedAccountView view) {
        return new VerifiedAccountResponse(view.accountId(), view.subjectId(), view.platform(),
                view.canonicalHandle(), view.providerAccountId(), view.evidenceProvider(),
                view.verifiedAt(), view.validUntil(), view.revokedAt(), view.active());
    }
}
