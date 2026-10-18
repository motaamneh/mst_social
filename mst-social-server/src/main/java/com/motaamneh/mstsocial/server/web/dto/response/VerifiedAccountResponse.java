package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.service.VerifiedAccountView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Historical account association; active reflects expiry and revocation at the time of the response.",
        example = """
                {
                  "accountId": "00000000-0000-4000-8000-000000000002",
                  "subjectId": "user-123",
                  "platform": "INSTAGRAM",
                  "canonicalHandle": "your_username",
                  "providerAccountId": null,
                  "evidenceProvider": "searchapi",
                  "verifiedAt": "2026-10-18T12:02:00Z",
                  "validUntil": "2027-10-18T12:02:00Z",
                  "revokedAt": null,
                  "active": true
                }
                """)
public record VerifiedAccountResponse(
        @Schema(description = "Verified association UUID; use this for social-identity endpoints.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "00000000-0000-4000-8000-000000000002")
        UUID accountId,
        @Schema(description = "User ID in the consuming application.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "user-123")
        String subjectId,
        @Schema(description = "Social platform.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "INSTAGRAM")
        Platform platform,
        @Schema(description = "Username confirmed by the provider.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "your_username")
        String canonicalHandle,
        @Schema(description = "Stable platform account ID if supplied; currently may be null. Handles can change.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String providerAccountId,
        @Schema(description = "Provider used to retrieve profile evidence.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "searchapi")
        String evidenceProvider,
        @Schema(description = "Time this account association was verified.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-18T12:02:00Z")
        Instant verifiedAt,
        @Schema(description = "Association expiry; default is one calendar year after verification.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2027-10-18T12:02:00Z")
        Instant validUntil,
        @Schema(description = "Revocation time, or null if not revoked.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        Instant revokedAt,
        @Schema(description = "True only while the association is valid and not revoked.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        boolean active
) {
    public static VerifiedAccountResponse from(VerifiedAccountView view) {
        return new VerifiedAccountResponse(view.accountId(), view.subjectId(), view.platform(),
                view.canonicalHandle(), view.providerAccountId(), view.evidenceProvider(),
                view.verifiedAt(), view.validUntil(), view.revokedAt(), view.active());
    }
}
