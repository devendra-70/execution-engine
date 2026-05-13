package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionResultValidatorTest — 100% coverage test suite
 * Created for coverage improvement feature (EPMICMPCOD-352 Loop Run 2)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionResultValidator - 100% Coverage Tests")
class ExecutionResultValidatorTest {

    @InjectMocks
    private ExecutionResultValidator validator;

    private ExecutionResultEvent validEvent;

    @BeforeEach
    void setUp() {
        validEvent = ExecutionResultEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(123L)
                .verdict("PASSED")
                .score(100.0)
                .problemId(100L)
                .build();
    }

    @Nested
    @DisplayName("validate() - Main Validation Logic")
    class ValidateTests {

        @Test
        @DisplayName("Should return true for valid event")
        void testValidate_ValidEvent() {
            assertTrue(validator.validate(validEvent));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when event is null")
        void testValidate_NullEvent() {
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(null),
                    "ExecutionResultEvent cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when executionId is null")
        void testValidate_NullExecutionId() {
            validEvent.setExecutionId(null);
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "executionId cannot be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when userId is null")
        void testValidate_NullUserId() {
            validEvent.setUserId(null);
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "userId must be positive");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when userId is zero")
        void testValidate_ZeroUserId() {
            validEvent.setUserId(0L);
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "userId must be positive");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when userId is negative")
        void testValidate_NegativeUserId() {
            validEvent.setUserId(-1L);
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "userId must be positive");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when verdict is null")
        void testValidate_NullVerdict() {
            validEvent.setVerdict(null);
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "verdict cannot be blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when verdict is blank")
        void testValidate_BlankVerdict() {
            validEvent.setVerdict("   ");
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "verdict cannot be blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when verdict is empty string")
        void testValidate_EmptyVerdict() {
            validEvent.setVerdict("");
            assertThrows(IllegalArgumentException.class, 
                    () -> validator.validate(validEvent),
                    "verdict cannot be blank");
        }
    }

    @Nested
    @DisplayName("isValidVerdict() - Verdict Validation")
    class IsValidVerdictTests {

        @Test
        @DisplayName("Should return true for PASSED verdict")
        void testIsValidVerdict_Passed() {
            assertTrue(validator.isValidVerdict("PASSED"));
        }

        @Test
        @DisplayName("Should return true for FAILED verdict")
        void testIsValidVerdict_Failed() {
            assertTrue(validator.isValidVerdict("FAILED"));
        }

        @Test
        @DisplayName("Should return true for RUNTIME_ERROR verdict")
        void testIsValidVerdict_RuntimeError() {
            assertTrue(validator.isValidVerdict("RUNTIME_ERROR"));
        }

        @Test
        @DisplayName("Should return true for COMPILE_ERROR verdict")
        void testIsValidVerdict_CompileError() {
            assertTrue(validator.isValidVerdict("COMPILE_ERROR"));
        }

        @Test
        @DisplayName("Should return true for PARTIAL_SUCCESS verdict")
        void testIsValidVerdict_PartialSuccess() {
            assertTrue(validator.isValidVerdict("PARTIAL_SUCCESS"));
        }

        @Test
        @DisplayName("Should return true for UNKNOWN verdict")
        void testIsValidVerdict_Unknown() {
            assertTrue(validator.isValidVerdict("UNKNOWN"));
        }

        @Test
        @DisplayName("Should return false for invalid verdict")
        void testIsValidVerdict_Invalid() {
            assertFalse(validator.isValidVerdict("INVALID_VERDICT"));
        }

        @Test
        @DisplayName("Should return false for null verdict")
        void testIsValidVerdict_Null() {
            assertFalse(validator.isValidVerdict(null));
        }

        @Test
        @DisplayName("Should return false for blank verdict")
        void testIsValidVerdict_Blank() {
            assertFalse(validator.isValidVerdict("   "));
        }

        @Test
        @DisplayName("Should return false for empty verdict")
        void testIsValidVerdict_Empty() {
            assertFalse(validator.isValidVerdict(""));
        }
    }

    @Nested
    @DisplayName("getVerdictCategory() - Verdict Categorization")
    class GetVerdictCategoryTests {

        @Test
        @DisplayName("Should return SUCCESS for PASSED verdict")
        void testGetVerdictCategory_Passed() {
            assertEquals("SUCCESS", validator.getVerdictCategory("PASSED"));
        }

        @Test
        @DisplayName("Should return SUCCESS for PARTIAL_SUCCESS verdict")
        void testGetVerdictCategory_PartialSuccess() {
            assertEquals("SUCCESS", validator.getVerdictCategory("PARTIAL_SUCCESS"));
        }

        @Test
        @DisplayName("Should return ERROR for FAILED verdict")
        void testGetVerdictCategory_Failed() {
            assertEquals("ERROR", validator.getVerdictCategory("FAILED"));
        }

        @Test
        @DisplayName("Should return ERROR for RUNTIME_ERROR verdict")
        void testGetVerdictCategory_RuntimeError() {
            assertEquals("ERROR", validator.getVerdictCategory("RUNTIME_ERROR"));
        }

        @Test
        @DisplayName("Should return ERROR for COMPILE_ERROR verdict")
        void testGetVerdictCategory_CompileError() {
            assertEquals("ERROR", validator.getVerdictCategory("COMPILE_ERROR"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for UNKNOWN verdict")
        void testGetVerdictCategory_Unknown() {
            assertEquals("UNKNOWN", validator.getVerdictCategory("UNKNOWN"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for invalid verdict")
        void testGetVerdictCategory_Invalid() {
            assertEquals("UNKNOWN", validator.getVerdictCategory("INVALID"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for null verdict")
        void testGetVerdictCategory_Null() {
            assertEquals("UNKNOWN", validator.getVerdictCategory(null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for empty verdict")
        void testGetVerdictCategory_Empty() {
            assertEquals("UNKNOWN", validator.getVerdictCategory(""));
        }
    }
}
