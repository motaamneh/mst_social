package com.motaamneh.mstsocial.core.service;

import com.motaamneh.mstsocial.core.model.*;
import java.time.Instant;
import java.util.UUID;

/** active is evaluated at the time of the service call. */
public record VerifiedAccountView(
        UUID accountId, String subjectId, Platform platform, String canonicalHandle,
        String providerAccountId, String evidenceProvider, Instant verifiedAt,
        Instant validUntil, Instant revokedAt, boolean active
) {
    public static VerifiedAccountView from(VerifiedSocialAccount account, Instant now) {
        return new VerifiedAccountView(account.getId(), account.getSubjectId(), account.getPlatform(),
                account.getCanonicalHandle(), account.getProviderAccountId(), account.getEvidenceProvider(),
                account.getVerifiedAt(), account.getValidUntil(), account.getRevokedAt(), account.isActive(now));
    }
}
