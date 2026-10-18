package com.motaamneh.mstsocial.server.web.dto.request;

import com.motaamneh.mstsocial.core.model.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HTTP input only. The authenticated principal supplies the tenant. */
@Getter
@Setter
@NoArgsConstructor
public class CreateVerificationRequest {
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
    private String subjectId;

    @NotNull
    private Platform platform;

    @NotBlank
    @Size(max = 256)
    private String handle;
}
