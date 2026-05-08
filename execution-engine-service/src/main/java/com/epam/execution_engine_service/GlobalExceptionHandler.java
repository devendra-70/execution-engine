package com.epam.execution_engine_service;

import com.epam.execution_engine_service.gateway.exception.AuthenticationException;
import com.epam.execution_engine_service.gateway.exception.ExecutionRegistrationException;
import com.epam.execution_engine_service.gateway.exception.RateLimitException;
import com.epam.execution_engine_service.gateway.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler
 * Handles all exceptions across the application
 * Returns structured error responses with appropriate HTTP status codes
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle ValidationException
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        log.warn("Validation error: {}", ex.getMessage());
        
        return ResponseEntity.badRequest().body(buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                ex.getMessage()
        ));
    }
    
    /**
     * Handle AuthenticationException
     * Returns HTTP 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        log.warn("Authentication error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication Error",
                ex.getMessage()
        ));
    }
    
    /**
     * Handle RateLimitException
     * Returns HTTP 429 Too Many Requests
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(
            RateLimitException ex, WebRequest request) {
        
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(buildErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Rate Limit Exceeded",
                ex.getMessage()
        ));
    }
    
    /**
     * Handle ExecutionRegistrationException
     * Returns HTTP 500 Internal Server Error
     */
    @ExceptionHandler(ExecutionRegistrationException.class)
    public ResponseEntity<Map<String, Object>> handleExecutionRegistrationException(
            ExecutionRegistrationException ex, WebRequest request) {
        
        log.error("Execution registration failed: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Execution Registration Error",
                ex.getMessage()
        ));
    }
    
    /**
     * Handle generic exceptions
     * Returns HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later."
        ));
    }
    
    /**
     * Build standardized error response
     * Timestamp is returned as ISO-8601 Instant per SRS Section 3.4
     */
    private Map<String, Object> buildErrorResponse(int status, String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("error", error);
        response.put("message", message);
        response.put("timestamp", Instant.now());
        return response;
    }
}
