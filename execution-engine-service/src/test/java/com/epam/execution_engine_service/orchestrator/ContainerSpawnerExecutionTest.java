package com.epam.execution_engine_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime Isolation Tests - Container Result Wrapper
 * 
 * Verifies ContainerExecutionResult wrapper class behavior and all result types.
 * Target: ~8 tests covering execution results, timeouts, and error cases
 */
@DisplayName("Runtime Isolation Tests - Container Execution Results")
class ContainerSpawnerExecutionTest {

    // ========== Result Wrapper - Success Tests ==========

    @Test
    @DisplayName("ContainerExecutionResult_success_isSuccess")
    void ContainerExecutionResult_success_isSuccess() {
        var result = ContainerSpawner.ContainerExecutionResult.success("output", 0);
        
        assertTrue(result.isSuccess(), "Success result should return true for isSuccess()");
    }

    @Test
    @DisplayName("ContainerExecutionResult_success_exitCodeZero")
    void ContainerExecutionResult_success_exitCodeZero() {
        var result = ContainerSpawner.ContainerExecutionResult.success("output", 0);
        
        assertEquals(0, result.getExitCode(), "Success result should have exit code 0");
    }

    @Test
    @DisplayName("ContainerExecutionResult_success_noTimeout")
    void ContainerExecutionResult_success_noTimeout() {
        var result = ContainerSpawner.ContainerExecutionResult.success("output", 0);
        
        assertFalse(result.isTimeout(), "Success result should not be timeout");
    }

    @Test
    @DisplayName("ContainerExecutionResult_success_preservesOutput")
    void ContainerExecutionResult_success_preservesOutput() {
        String output = "test output";
        var result = ContainerSpawner.ContainerExecutionResult.success(output, 0);
        
        assertEquals(output, result.getOutput(), "Success result should preserve output");
    }

    // ========== Result Wrapper - Timeout Tests ==========

    @Test
    @DisplayName("ContainerExecutionResult_timeout_isTimeout")
    void ContainerExecutionResult_timeout_isTimeout() {
        var result = ContainerSpawner.ContainerExecutionResult.timeout("output", 3);
        
        assertTrue(result.isTimeout(), "Timeout result should return true for isTimeout()");
    }

    @Test
    @DisplayName("ContainerExecutionResult_timeout_notSuccess")
    void ContainerExecutionResult_timeout_notSuccess() {
        var result = ContainerSpawner.ContainerExecutionResult.timeout("output", 3);
        
        assertFalse(result.isSuccess(), "Timeout result should return false for isSuccess()");
    }

    @Test
    @DisplayName("ContainerExecutionResult_timeout_hasErrorMessage")
    void ContainerExecutionResult_timeout_hasErrorMessage() {
        var result = ContainerSpawner.ContainerExecutionResult.timeout("output", 3);
        
        String errorMsg = result.getError();
        assertTrue(errorMsg.contains("timeout"), "Timeout result should mention timeout in error");
        assertTrue(errorMsg.contains("3"), "Timeout result should include timeout seconds");
    }

    // ========== Result Wrapper - Error Tests ==========

    @Test
    @DisplayName("ContainerExecutionResult_error_notSuccess")
    void ContainerExecutionResult_error_notSuccess() {
        var result = ContainerSpawner.ContainerExecutionResult.error("Connection failed");
        
        assertFalse(result.isSuccess(), "Error result should return false for isSuccess()");
    }

    @Test
    @DisplayName("ContainerExecutionResult_error_notTimeout")
    void ContainerExecutionResult_error_notTimeout() {
        var result = ContainerSpawner.ContainerExecutionResult.error("Connection failed");
        
        assertFalse(result.isTimeout(), "Error result should not be timeout");
    }

    @Test
    @DisplayName("ContainerExecutionResult_error_hasErrorMessage")
    void ContainerExecutionResult_error_hasErrorMessage() {
        String errorMsg = "Connection failed";
        var result = ContainerSpawner.ContainerExecutionResult.error(errorMsg);
        
        assertEquals(errorMsg, result.getError(), "Error result should preserve error message");
    }

    @Test
    @DisplayName("ContainerExecutionResult_error_negativeExitCode")
    void ContainerExecutionResult_error_negativeExitCode() {
        var result = ContainerSpawner.ContainerExecutionResult.error("Error");
        
        assertEquals(-1, result.getExitCode(), "Error result should have exit code -1");
    }

    // ========== Result Wrapper - State Verification Tests ==========

    @Test
    @DisplayName("ContainerExecutionResult_successState_allFieldsCorrect")
    void ContainerExecutionResult_successState_allFieldsCorrect() {
        var result = ContainerSpawner.ContainerExecutionResult.success("test output", 42);
        
        assertAll(
            () -> assertTrue(result.isSuccess(), "Should be success"),
            () -> assertFalse(result.isTimeout(), "Should not be timeout"),
            () -> assertEquals(42, result.getExitCode(), "Exit code should be 42"),
            () -> assertEquals("test output", result.getOutput(), "Output should match"),
            () -> assertNull(result.getError(), "Error should be null")
        );
    }

    @Test
    @DisplayName("ContainerExecutionResult_errorState_allFieldsCorrect")
    void ContainerExecutionResult_errorState_allFieldsCorrect() {
        var result = ContainerSpawner.ContainerExecutionResult.error("test error");
        
        assertAll(
            () -> assertFalse(result.isSuccess(), "Should not be success"),
            () -> assertFalse(result.isTimeout(), "Should not be timeout"),
            () -> assertEquals(-1, result.getExitCode(), "Exit code should be -1"),
            () -> assertEquals("test error", result.getError(), "Error message should match")
        );
    }

    @Test
    @DisplayName("ContainerExecutionResult_timeoutState_allFieldsCorrect")
    void ContainerExecutionResult_timeoutState_allFieldsCorrect() {
        var result = ContainerSpawner.ContainerExecutionResult.timeout("partial output", 5);
        
        assertAll(
            () -> assertFalse(result.isSuccess(), "Should not be success"),
            () -> assertTrue(result.isTimeout(), "Should be timeout"),
            () -> assertEquals(-1, result.getExitCode(), "Exit code should be -1"),
            () -> assertEquals("partial output", result.getOutput(), "Output should match")
        );
    }

    @Test
    @DisplayName("ContainerExecutionResult_toString_noException")
    void ContainerExecutionResult_toString_noException() {
        var result = ContainerSpawner.ContainerExecutionResult.success("output", 0);
        
        assertDoesNotThrow(() -> {
            String str = result.toString();
            assertNotNull(str, "toString() should not return null");
            assertTrue(str.contains("ContainerExecutionResult"), "toString() should include class name");
        });
    }
}
