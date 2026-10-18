package com.motaamneh.mstsocial.server.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A validation failure without the rejected input value.")
public record FieldErrorResponse(
        @Schema(description = "Invalid request field.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "subjectId")
        String field,
        @Schema(description = "Validation explanation.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "must not be blank")
        String message
) {
}
