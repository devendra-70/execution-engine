package com.epam.execution_engine_service.exception;

import com.epam.execution_engine_service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for validation and authentication errors.
 *
 * <p>Provides consistent error response formatting across all validation failures,
 * authentication errors, and access control violations.
 *
 * <p>All error responses include:
 * <ul>
 *   <li>timestamp: ISO-8601 timestamp of error occurrence</li>
 *   <li>status: HTTP status code</li>
 *   <li>error: Error category</li>
 *   <li>message: Human-readable explanation</li>
 *   <li>fieldErrors: List of field-level validation errors (400 only)</li>
 * </ul>
 *
 * @author Execution Engine Team
 */
@ControllerAdvice
@Slf4j
public class ValidationExceptionHandler {

    /**
     * Handles JSR-380 bean validation failures.
     *
     * <p>When a request fails JSR-380 validation (e.g., @NotBlank, @ValidMode),
     * Spring throws MethodArgumentNotValidException. This handler converts it
     * to a 400 Bad Request response with field-level error details.
     *
     * @param ex the validation exception thrown by Spring
     * @param request the HTTP request
     * @return 400 Bad Request with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldErrorDetail> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Validation Failed")
                .message("Request payload validation failed. See fieldErrors for details.")
                .fieldErrors(fieldErrors)
                .build();

        log.warn("Validation failed for request to {}: fields={}",
                request.getRequestURI(),
                fieldErrors.stream().map(f -> f.getField()).collect(Collectors.toList()));


        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles Spring Security authentication exceptions.
     *
     * <p>When a request cannot be authenticated (e.g., missing JWT token, invalid signature),
     * Spring Security throws AuthenticationException. This handler converts it to a
     * 401 Unauthorized response.
     *
     * @param ex the authentication exception thrown by Spring Security
     * @param request the HTTP request
     * @return 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(401)
                .error("Unauthorized")
                .message("Authentication failed. Provide a valid JWT bearer token.")
                .fieldErrors(Collections.emptyList())
                .build();

        log.warn("Authentication failed for request to {}. Cause: {}",
                request.getRequestURI(),
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handles Spring Security access control violations.
     *
     * <p>When an authenticated user lacks permission for a resource
     * (e.g., @PreAuthorize fails), Spring Security throws AccessDeniedException.
     * This handler converts it to a 403 Forbidden response.
     *
     * @param ex the access denied exception thrown by Spring Security
     * @param request the HTTP request
     * @return 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(403)
                .error("Forbidden")
                .message("Access denied. You do not have permission to access this resource.")
                .fieldErrors(Collections.emptyList())
                .build();

        log.warn("Access denied for request to {}. User: {}",
                request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "unknown");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Generic handler for unexpected exceptions.
     *
     * <p>Catches any unhandled exceptions and returns a 500 Internal Server Error
     * without exposing sensitive implementation details.
     *
     * @param ex the unexpected exception
     * @param request the HTTP request
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .fieldErrors(Collections.emptyList())
                .build();

        log.error("Unexpected exception for request to {}", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
