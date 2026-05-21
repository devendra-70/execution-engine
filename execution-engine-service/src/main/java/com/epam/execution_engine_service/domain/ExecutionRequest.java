package com.epam.execution_engine_service.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request payload for submitting a code execution")
public class ExecutionRequest {

    @NotNull
    @Schema(description = "ID of the problem to execute against", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long problemId;

    @NotBlank
    @Schema(description = "Programming language of the source code", example = "JAVA", allowableValues = {"JAVA", "PYTHON", "JAVASCRIPT"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String language;

    @NotBlank
    @Schema(description = "Execution mode", example = "run", allowableValues = {"run", "submit"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String mode;

    @NotBlank
    @Schema(description = "Source code to execute", example = "public class Solution { public static void main(String[] args) { System.out.println(\"Hello\"); } }", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceCode;
}

