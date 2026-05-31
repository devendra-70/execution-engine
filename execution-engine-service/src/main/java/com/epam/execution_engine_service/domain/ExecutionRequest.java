package com.epam.execution_engine_service.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ExecutionRequest {
    @NotNull
    private Long problemId;

    @NotBlank
    private String language;

    /** Only "run" or "submit" are valid values (case-insensitive). */
    @NotBlank
    @Pattern(
        regexp = "run|submit",
        flags  = Pattern.Flag.CASE_INSENSITIVE,
        message = "mode must be 'run' or 'submit'"
    )
    private String mode;

    @NotBlank
    private String sourceCode;
}

