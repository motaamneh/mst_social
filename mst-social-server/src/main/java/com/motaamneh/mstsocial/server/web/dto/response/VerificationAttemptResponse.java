package com.motaamneh.mstsocial.server.web.dto.response;

import com.motaamneh.mstsocial.core.service.VerificationOutcome;

public record VerificationAttemptResponse(
        VerificationOutcome outcome,
        VerificationResponse verification,
        VerifiedAccountResponse account
) {
}
