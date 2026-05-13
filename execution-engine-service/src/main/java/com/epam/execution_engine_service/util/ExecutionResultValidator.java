package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ExecutionResultValidator — Validates execution result events before persistence
 * Created for coverage improvement feature (EPMICMPCOD-352 Loop Run 2)
 * 
 * Validates:
 * - Not null
 * - Has valid executionId
 * - Has valid userId
 * - Has valid verdict
 */
@Slf4j
@Component
public class ExecutionResultValidator {

    /**
     * Validate execution result event
     * @param event ExecutionResultEvent to validate
     * @return true if valid, false otherwise
     * @throws IllegalArgumentException if validation fails with details
     */
    public boolean validate(ExecutionResultEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("ExecutionResultEvent cannot be null");
        }
        
        if (event.getExecutionId() == null) {
            throw new IllegalArgumentException("ExecutionResultEvent executionId cannot be null");
        }
        
        if (event.getUserId() == null || event.getUserId() <= 0) {
            throw new IllegalArgumentException("ExecutionResultEvent userId must be positive");
        }
        
        if (event.getVerdict() == null || event.getVerdict().isBlank()) {
            throw new IllegalArgumentException("ExecutionResultEvent verdict cannot be blank");
        }
        
        log.debug("ExecutionResultEvent validated successfully: executionId={}, userId={}, verdict={}", 
                event.getExecutionId(), event.getUserId(), event.getVerdict());
        return true;
    }

    /**
     * Check if verdict is valid (PASSED, FAILED, RUNTIME_ERROR, COMPILE_ERROR, PARTIAL_SUCCESS, UNKNOWN)
     * @param verdict Verdict string
     * @return true if verdict is one of the valid values
     */
    public boolean isValidVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return false;
        }
        
        return verdict.equals("PASSED") ||
               verdict.equals("FAILED") ||
               verdict.equals("RUNTIME_ERROR") ||
               verdict.equals("COMPILE_ERROR") ||
               verdict.equals("PARTIAL_SUCCESS") ||
               verdict.equals("UNKNOWN");
    }

    /**
     * Get verdict category: SUCCESS, ERROR, UNKNOWN
     * @param verdict Verdict string
     * @return Category or "UNKNOWN" if not recognized
     */
    public String getVerdictCategory(String verdict) {
        if (verdict == null) {
            return "UNKNOWN";
        }
        
        return switch (verdict) {
            case "PASSED", "PARTIAL_SUCCESS" -> "SUCCESS";
            case "FAILED", "RUNTIME_ERROR", "COMPILE_ERROR" -> "ERROR";
            default -> "UNKNOWN";
        };
    }
}
