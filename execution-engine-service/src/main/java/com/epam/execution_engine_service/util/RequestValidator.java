package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.gateway.exception.ValidationException;
import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import org.springframework.stereotype.Component;

/**
 * Validates ExecutionRequest for business logic constraints
 * Note: Field-level validation (@NotBlank, @Size) is handled by Spring's @Valid annotation
 * This class focuses on custom business logic validation (SRS Section 2.2)
 */
@Component
public class RequestValidator {
    
    /**
     * Validates ExecutionRequest for business logic constraints
     * Also performs manual validation for fields with @NotBlank and @NotNull annotations
     * @param request The request to validate
     * @throws ValidationException if validation fails
     */
    public void validate(ExecutionRequest request) {
        if (request == null) {
            throw new ValidationException("ExecutionRequest cannot be null");
        }
        
        // Validate required fields (handles @NotBlank and @NotNull)
        String problemId = request.getProblemId();
        if (problemId == null || problemId.trim().isEmpty()) {
            throw new ValidationException("problemId cannot be blank");
        }
        
        String language = request.getLanguage();
        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language cannot be blank");
        }
        
        String sourceCode = request.getSourceCode();
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new ValidationException("sourceCode cannot be blank");
        }
        
        // Validate mode is either RUN or SUBMIT (custom business logic validation)
        String mode = request.getMode();
        if (mode == null) {
            throw new ValidationException("mode cannot be null");
        }
        String upperMode = mode.toUpperCase();
        if (!upperMode.equals("RUN") && !upperMode.equals("SUBMIT")) {
            throw new ValidationException("mode must be either 'RUN' or 'SUBMIT'");
        }
    }
}
