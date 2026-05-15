package com.epam.execution_engine_service;

import com.epam.execution_engine_service.dto.ErrorResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
     * Handle JSR-380 bean validation failures.
     * Returns HTTP 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
       
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
 
        log.warn("Validation failed: fields={}",
                fieldErrors.stream().map(ErrorResponse.FieldErrorDetail::getField).collect(Collectors.toList()));
 
        return ResponseEntity.badRequest().body(response);
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
