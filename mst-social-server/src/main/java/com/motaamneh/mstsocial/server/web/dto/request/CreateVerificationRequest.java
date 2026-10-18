package com.motaamneh.mstsocial.server.web.dto.request;

import com.motaamneh.mstsocial.core.model.Platform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HTTP input only. The authenticated principal supplies the tenant. */
@Schema(description = "Create a verification for a user in your application. Tenant identity comes from the API key.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
@Getter
@Setter
@NoArgsConstructor
public class CreateVerificationRequest {
    @NotBlank
    @Size(min = 1, max = 128)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*", message = "must not contain control characters")
    @Schema(description = "Your application user ID; nonblank and without ASCII control characters.",
            example = "user-123", minLength = 1)
    private String subjectId;

    @NotNull
    @Schema(description = "INSTAGRAM and TIKTOK are supported. X and FACEBOOK return 422; unknown enum values return 400.",
            example = "INSTAGRAM")
    private Platform platform;

    @NotBlank
    @Size(min = 1, max = 256)
    @Schema(description = "Social username, optionally prefixed by @. Platform validation follows normalization.",
            example = "@your_username", minLength = 1)
    private String handle;
}
