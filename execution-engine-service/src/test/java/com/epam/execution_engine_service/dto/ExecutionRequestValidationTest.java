package com.epam.execution_engine_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExecutionRequest DTO validation.
 *
 * <p>Tests that JSR-380 validation annotations work correctly on the ExecutionRequest DTO.
 *
 * @author Execution Engine Team
 */
@SpringBootTest
@DisplayName("ExecutionRequest DTO Validation Tests")
class ExecutionRequestValidationTest {

    @Autowired
    private Validator validator;

    @Test
    @DisplayName("Valid ExecutionRequest passes validation")
    void testValidRequest() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "SUBMIT",
                "class Solution { public int[] twoSum(int[] nums, int target) { ... } }"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no validation violations");
    }

    @Test
    @DisplayName("Missing problemId fails validation")
    void testMissingProblemId() {
        ExecutionRequest request = new ExecutionRequest(
                null,
                "java21",
                "RUN",
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("problemId")));
    }

    @Test
    @DisplayName("Blank problemId fails validation")
    void testBlankProblemId() {
        ExecutionRequest request = new ExecutionRequest(
                "   ",
                "java21",
                "RUN",
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("problemId")));
    }

    @Test
    @DisplayName("Missing language fails validation")
    void testMissingLanguage() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                null,
                "RUN",
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("language")));
    }

    @Test
    @DisplayName("Missing mode fails validation")
    void testMissingMode() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                null,
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("mode")));
    }

    @Test
    @DisplayName("Invalid mode value fails validation")
    void testInvalidMode() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "INVALID",
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("mode")));
    }

    @Test
    @DisplayName("Missing sourceCode fails validation")
    void testMissingSourceCode() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                null
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("sourceCode")));
    }

    @Test
    @DisplayName("Blank sourceCode fails validation")
    void testBlankSourceCode() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                "   "
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("sourceCode")));
    }

    @Test
    @DisplayName("SourceCode exceeding max length fails validation")
    void testSourceCodeTooLong() {
        String longSourceCode = "a".repeat(100001);
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                longSourceCode
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected validation violations");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("sourceCode")));
    }

    @Test
    @DisplayName("SourceCode at max length passes validation")
    void testSourceCodeAtMaxLength() {
        String sourceCode = "a".repeat(100000);
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                sourceCode
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no validation violations");
    }

    @Test
    @DisplayName("Mode case-insensitive: 'run' passes validation")
    void testModeCaseInsensitiveLowercase() {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "run",
                "source code"
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no validation violations");
    }

    @Test
    @DisplayName("All fields invalid fails validation with multiple violations")
    void testAllFieldsInvalid() {
        ExecutionRequest request = new ExecutionRequest(
                null,
                null,
                "INVALID",
                null
        );

        Set<ConstraintViolation<ExecutionRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected multiple validation violations");
        assertEquals(4, violations.size(), "Expected 4 violations (one per field)");
    }
}
