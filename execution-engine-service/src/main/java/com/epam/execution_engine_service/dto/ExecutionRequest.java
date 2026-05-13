package com.epam.execution_engine_service.dto;

import com.epam.execution_engine_service.validation.ValidMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for POST /api/executions endpoint.
 *
 * <p>Represents a code submission request with all required fields validated
 * at the gateway layer before any downstream processing.
 *
 * @author Execution Engine Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    /**
     * Unique identifier for the coding problem (e.g., "two-sum").
     * Required, non-blank.
     */
    @NotBlank(message = "problemId is required")
    private String problemId;

    /**
     * Language/runtime tag for the submission (e.g., "java21", "python3").
     * Required, non-blank.
     */
    @NotBlank(message = "language is required")
    private String language;

    /**
     * Execution mode: either RUN (preview) or SUBMIT (score and persist).
     * Required, non-blank, validated against ExecutionMode enum values.
     */
    @NotBlank(message = "mode is required")
    @ValidMode(message = "mode must be RUN or SUBMIT")
    private String mode;

    /**
     * Full source code submitted by the user.
     * Required, non-blank, limited to reasonable max length (100KB).
     */
    @NotBlank(message = "sourceCode is required")
    @Size(min = 1, max = 100000, message = "sourceCode must be between 1 and 100000 characters")
    private String sourceCode;
}
