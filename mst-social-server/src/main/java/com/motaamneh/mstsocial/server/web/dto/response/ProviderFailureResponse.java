package com.motaamneh.mstsocial.server.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.motaamneh.mstsocial.core.model.ProfileFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Provider failure details. A provider failure does not increment the bio mismatch count.")
public record ProviderFailureResponse(
        @Schema(description = "Reason provider evidence was unavailable.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "PROVIDER_TIMEOUT")
        ProfileFailureReason reason,
        @Schema(description = "Whether the provider failure can be retried while the request remains eligible.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        boolean retryable,
        @Schema(description = "Optional provider-supplied delay in seconds (1\u201386400).",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                example = "30",
                minimum = "1",
                maximum = "86400")
        Long retryAfterSeconds
) {
}
