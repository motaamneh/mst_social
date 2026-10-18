package com.motaamneh.mstsocial.server.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.motaamneh.mstsocial.core.model.ProfileFailureReason;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderFailureResponse(ProfileFailureReason reason, boolean retryable, Long retryAfterSeconds) {
}
