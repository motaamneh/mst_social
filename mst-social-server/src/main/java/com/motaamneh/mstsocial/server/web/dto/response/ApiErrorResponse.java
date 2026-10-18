package com.motaamneh.mstsocial.server.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

/** Problem response with stable codes and curated details, never raw exception messages. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Safe problem details. Optional fields are omitted when absent; security errors may contain only type, title, status, and code.")
public record ApiErrorResponse(
        @Schema(description = "Problem type URI.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "about:blank")
        URI type,
        @Schema(description = "HTTP status title.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Bad Request")
        String title,
        @Schema(description = "HTTP error status.", minimum = "400", maximum = "599",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "400")
        int status,
        @Schema(description = "Stable machine-readable error code.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "VALIDATION_FAILED")
        String code,
        @Schema(description = "Safe explanation; omitted by security responses.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                example = "One or more request fields are invalid.")
        String detail,
        @Schema(description = "Present for request field validation errors.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<FieldErrorResponse> errors,
        @Schema(description = "Current verification state.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        VerificationResponse verification,
        @Schema(description = "Present when profile evidence could not be fetched or used.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        ProviderFailureResponse providerFailure
) {
    public ApiErrorResponse {
        errors = errors == null ? null : List.copyOf(errors);
    }

    public static ApiErrorResponse of(HttpStatus status, String code, String detail) {
        return new ApiErrorResponse(URI.create("about:blank"), status.getReasonPhrase(),
                status.value(), code, detail, null, null, null);
    }
}
