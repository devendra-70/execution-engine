package com.epam.execution_engine_service.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExecutionRequest {
    @NotNull
    private Long problemId;

    @NotBlank
    private String language;

    @NotBlank
    private String mode; // "run" or "submit"

    @NotBlank
    private String sourceCode;
}

