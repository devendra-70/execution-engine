package com.epam.execution_engine_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Error response DTO for validation and exception handling.
 *
 * <p>Provides a consistent error envelope across all validation failures,
 * authentication errors, and access control violations.
 *
 * @author Execution Engine Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /**
     * Timestamp when the error occurred (ISO-8601 format).
     */
    private Instant timestamp;

    /**
     * HTTP status code (400, 401, 403, etc.).
     */
    private int status;

    /**
     * Error category (e.g., "Validation Failed", "Unauthorized", "Forbidden").
     */
    private String error;

    /**
     * Human-readable error message.
     */
    private String message;

    /**
     * Field-level validation errors (populated for 400 Bad Request).
     * Empty list for non-validation errors.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FieldErrorDetail> fieldErrors;

    /**
     * Represents a single field validation error.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorDetail {
        private String field;
        private String message;
    }
}
