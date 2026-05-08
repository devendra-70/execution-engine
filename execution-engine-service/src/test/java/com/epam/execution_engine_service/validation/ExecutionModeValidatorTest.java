package com.epam.execution_engine_service.validation;

import com.epam.execution_engine_service.model.ExecutionMode;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExecutionModeValidator.
 *
 * <p>Tests the custom validator's ability to convert and validate execution mode strings.
 *
 * @author Execution Engine Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionModeValidator Tests")
class ExecutionModeValidatorTest {

    private ExecutionModeValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ExecutionModeValidator();
        validator.initialize(null);
    }

    @Test
    @DisplayName("Valid mode 'RUN' should pass validation")
    void testValidModeRun() {
        assertTrue(validator.isValid("RUN", context));
    }

    @Test
    @DisplayName("Valid mode 'SUBMIT' should pass validation")
    void testValidModeSubmit() {
        assertTrue(validator.isValid("SUBMIT", context));
    }

    @Test
    @DisplayName("Valid mode 'run' (lowercase) should pass validation")
    void testValidModeLowercase() {
        assertTrue(validator.isValid("run", context));
    }

    @Test
    @DisplayName("Valid mode 'submit' (lowercase) should pass validation")
    void testValidModeSubmitLowercase() {
        assertTrue(validator.isValid("submit", context));
    }

    @Test
    @DisplayName("Valid mode 'Run' (mixed case) should pass validation")
    void testValidModeMixedCase() {
        assertTrue(validator.isValid("Run", context));
    }

    @Test
    @DisplayName("Invalid mode 'DELETE' should fail validation")
    void testInvalidModeDelete() {
        assertFalse(validator.isValid("DELETE", context));
    }

    @Test
    @DisplayName("Invalid mode 'INVALID' should fail validation")
    void testInvalidModeInvalid() {
        assertFalse(validator.isValid("INVALID", context));
    }

    @Test
    @DisplayName("Null mode should pass validation (let @NotBlank handle it)")
    void testNullMode() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    @DisplayName("Empty string should fail validation")
    void testEmptyMode() {
        assertFalse(validator.isValid("", context));
    }

    @Test
    @DisplayName("Whitespace-only mode should fail validation")
    void testWhitespaceMode() {
        assertFalse(validator.isValid("   ", context));
    }

    @Test
    @DisplayName("Mode with extra characters should fail validation")
    void testModeWithExtraCharacters() {
        assertFalse(validator.isValid("RUN_EXTRA", context));
    }

    @Test
    @DisplayName("Mode with leading whitespace should fail validation")
    void testModeWithLeadingWhitespace() {
        assertFalse(validator.isValid(" RUN", context));
    }

    @Test
    @DisplayName("Mode with trailing whitespace should fail validation")
    void testModeWithTrailingWhitespace() {
        assertFalse(validator.isValid("RUN ", context));
    }
}
