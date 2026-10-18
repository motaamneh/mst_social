package com.motaamneh.mstsocial.server.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.util.List;

/** Problem response with stable codes and curated details, never raw exception messages. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        URI type, String title, int status, String code, String detail,
        List<FieldErrorResponse> errors,
        VerificationResponse verification,
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
