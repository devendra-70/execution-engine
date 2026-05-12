package com.epam.execution_engine_service.persistence.mapper;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.event.TestCaseResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResultMapper DTO-to-Entity mapping.
 * Covers entity mapping, field fidelity, cascaded children, and reverse mapping.
 *
 * Test cases: 18+
 */
@DisplayName("ResultMapper Tests")
class ResultMapperTest {

    private ResultMapper mapper;
    private ExecutionResultEvent event;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        mapper = new ResultMapper();
        executionId = UUID.randomUUID();
        event = ExecutionResultEvent.builder()
            .executionId(executionId)
            .userId(123L)
            .problemId(456L)
            .language("JAVA")
            .mode("SUBMIT")
            .verdict("PASSED")
            .status("COMPLETED")
            .score(100.0)
            .totalRuntimeMs(1500L)
            .memoryBytes(2048000L)
            .rawOutput("Output")
            .errorOutput(null)
            .submittedCode("public class Main {}")
            .submittedAt(OffsetDateTime.now().minusMinutes(5))
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();
    }

    @Nested
    @DisplayName("DTO to Entity Mapping")
    class DtoToEntityMappingTests {

        @Test
        @DisplayName("Should map ExecutionResultEvent to SubmissionEntity")
        void testBasicMapping() {
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertNotNull(entity);
            assertEquals(event.getExecutionId(), entity.getExecutionId());
            assertEquals(event.getUserId(), entity.getUserId());
            assertEquals(event.getProblemId(), entity.getProblemId());
        }

        @Test
        @DisplayName("Should map all fields with full fidelity")
        void testFullFieldMapping() {
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertEquals(event.getExecutionId(), entity.getExecutionId());
            assertEquals(event.getUserId(), entity.getUserId());
            assertEquals(event.getProblemId(), entity.getProblemId());
            assertEquals(event.getLanguage(), entity.getLanguage());
            assertEquals(event.getMode(), entity.getMode());
            assertEquals(event.getVerdict(), entity.getVerdict());
            assertEquals(event.getStatus(), entity.getStatus());
            assertEquals(event.getScore(), entity.getScore());
            assertEquals(event.getTotalRuntimeMs(), entity.getTotalRuntimeMs());
            assertEquals(event.getMemoryBytes(), entity.getMemoryBytes());
            assertEquals(event.getRawOutput(), entity.getRawOutput());
            assertEquals(event.getErrorOutput(), entity.getErrorOutput());
            assertEquals(event.getSubmittedCode(), entity.getSubmittedCode());
            assertEquals(event.getSubmittedAt(), entity.getSubmittedAt());
            assertEquals(event.getCompletedAt(), entity.getCompletedAt());
        }

        @Test
        @DisplayName("Should map null optional fields")
        void testNullFieldMapping() {
            event.setScore(null);
            event.setRawOutput(null);
            event.setErrorOutput(null);
            event.setCompletedAt(null);

            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertNull(entity.getScore());
            assertNull(entity.getRawOutput());
            assertNull(entity.getErrorOutput());
            assertNull(entity.getCompletedAt());
        }

        @Test
        @DisplayName("Should throw NullPointerException for null event")
        void testNullEventThrows() {
            assertThrows(IllegalArgumentException.class, () -> mapper.toSubmissionEntity(null));
        }

        @Test
        @DisplayName("Should throw NullPointerException for null executionId")
        void testNullExecutionIdThrows() {
            event.setExecutionId(null);
            assertThrows(IllegalArgumentException.class, () -> mapper.toSubmissionEntity(event));
        }
    }

    @Nested
    @DisplayName("Cascaded Test Results Mapping")
    class CascadedMappingTests {

        @Test
        @DisplayName("Should map empty test results list")
        void testEmptyTestResults() {
            event.setTestResults(new ArrayList<>());
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertNotNull(entity.getTestResults());
            assertTrue(entity.getTestResults().isEmpty());
        }

        @Test
        @DisplayName("Should map single test result")
        void testSingleTestResult() {
            TestCaseResultEvent testCase = TestCaseResultEvent.builder()
                .testCaseId("tc1")
                .status("PASSED")
                .runtimeMs(100L)
                .memoryBytes(512000L)
                .expectedOutput("expected")
                .actualOutput("actual")
                .errorOutput(null)
                .build();

            event.setTestResults(List.of(testCase));
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertEquals(1, entity.getTestResults().size());
            SubmissionTestResultEntity mappedResult = entity.getTestResults().get(0);
            assertEquals("tc1", mappedResult.getTestCaseId());
            assertEquals("PASSED", mappedResult.getStatus());
            assertEquals(executionId, mappedResult.getExecutionId());
        }

        @Test
        @DisplayName("Should map multiple test results")
        void testMultipleTestResults() {
            List<TestCaseResultEvent> testCases = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                testCases.add(TestCaseResultEvent.builder()
                    .testCaseId("tc" + i)
                    .status("PASSED")
                    .runtimeMs((long) 100 * i)
                    .memoryBytes(512000L)
                    .build());
            }

            event.setTestResults(testCases);
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            assertEquals(5, entity.getTestResults().size());
            for (int i = 0; i < 5; i++) {
                assertEquals("tc" + i, entity.getTestResults().get(i).getTestCaseId());
                assertEquals(executionId, entity.getTestResults().get(i).getExecutionId());
            }
        }

        @Test
        @DisplayName("Should map test result fields with full fidelity")
        void testTestResultFieldFidelity() {
            TestCaseResultEvent testCase = TestCaseResultEvent.builder()
                .testCaseId("tc1")
                .status("FAILED")
                .runtimeMs(2000L)
                .memoryBytes(1024000L)
                .expectedOutput("expected output")
                .actualOutput("actual output")
                .errorOutput("runtime error")
                .build();

            event.setTestResults(List.of(testCase));
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            SubmissionTestResultEntity result = entity.getTestResults().get(0);
            assertEquals("tc1", result.getTestCaseId());
            assertEquals("FAILED", result.getStatus());
            assertEquals(2000L, result.getRuntimeMs());
            assertEquals(1024000L, result.getMemoryBytes());
            assertEquals("expected output", result.getExpectedOutput());
            assertEquals("actual output", result.getActualOutput());
            assertEquals("runtime error", result.getErrorOutput());
        }

        @Test
        @DisplayName("Should establish bidirectional relationship with parent")
        void testBidirectionalRelationship() {
            TestCaseResultEvent testCase = TestCaseResultEvent.builder()
                .testCaseId("tc1")
                .status("PASSED")
                .build();

            event.setTestResults(List.of(testCase));
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            SubmissionTestResultEntity result = entity.getTestResults().get(0);
            // Verify bidirectional relationship set by mapper
            assertEquals(entity, result.getSubmission());
        }
    }

    @Nested
    @DisplayName("Entity to DTO Reverse Mapping")
    class EntityToDtoMappingTests {

        @Test
        @DisplayName("Should map SubmissionEntity back to ExecutionResultEvent")
        void testReverseMapping() {
            SubmissionEntity entity = mapper.toSubmissionEntity(event);
            ExecutionResultEvent mapped = mapper.toExecutionResultEvent(entity);

            assertNotNull(mapped);
            assertEquals(entity.getExecutionId(), mapped.getExecutionId());
            assertEquals(entity.getUserId(), mapped.getUserId());
            assertEquals(entity.getProblemId(), mapped.getProblemId());
        }

        @Test
        @DisplayName("Should map all fields with full fidelity in reverse")
        void testFullReverseMappingFidelity() {
            SubmissionEntity entity = mapper.toSubmissionEntity(event);
            ExecutionResultEvent mapped = mapper.toExecutionResultEvent(entity);

            assertEquals(entity.getExecutionId(), mapped.getExecutionId());
            assertEquals(entity.getUserId(), mapped.getUserId());
            assertEquals(entity.getProblemId(), mapped.getProblemId());
            assertEquals(entity.getLanguage(), mapped.getLanguage());
            assertEquals(entity.getMode(), mapped.getMode());
            assertEquals(entity.getVerdict(), mapped.getVerdict());
            assertEquals(entity.getStatus(), mapped.getStatus());
            assertEquals(entity.getScore(), mapped.getScore());
            assertEquals(entity.getTotalRuntimeMs(), mapped.getTotalRuntimeMs());
            assertEquals(entity.getMemoryBytes(), mapped.getMemoryBytes());
        }

        @Test
        @DisplayName("Should reverse-map test results")
        void testReverseMapTestResults() {
            List<TestCaseResultEvent> testCases = List.of(
                TestCaseResultEvent.builder()
                    .testCaseId("tc1")
                    .status("PASSED")
                    .build(),
                TestCaseResultEvent.builder()
                    .testCaseId("tc2")
                    .status("FAILED")
                    .build()
            );

            event.setTestResults(testCases);
            SubmissionEntity entity = mapper.toSubmissionEntity(event);
            ExecutionResultEvent mapped = mapper.toExecutionResultEvent(entity);

            assertEquals(2, mapped.getTestResults().size());
            assertEquals("tc1", mapped.getTestResults().get(0).getTestCaseId());
            assertEquals("tc2", mapped.getTestResults().get(1).getTestCaseId());
        }

        @Test
        @DisplayName("Should throw NullPointerException for null entity in reverse mapping")
        void testNullEntityThrows() {
            assertThrows(IllegalArgumentException.class, () -> mapper.toExecutionResultEvent(null));
        }
    }

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripMappingTests {

        @Test
        @DisplayName("Should preserve data through round-trip mapping")
        void testRoundTripPreservation() {
            TestCaseResultEvent testCase = TestCaseResultEvent.builder()
                .testCaseId("tc1")
                .status("PASSED")
                .runtimeMs(500L)
                .memoryBytes(1024000L)
                .expectedOutput("expected")
                .actualOutput("actual")
                .build();

            event.setTestResults(List.of(testCase));

            // Forward mapping
            SubmissionEntity entity = mapper.toSubmissionEntity(event);

            // Reverse mapping
            ExecutionResultEvent reverseMapped = mapper.toExecutionResultEvent(entity);

            // Verify key fields preserved
            assertEquals(event.getExecutionId(), reverseMapped.getExecutionId());
            assertEquals(event.getUserId(), reverseMapped.getUserId());
            assertEquals(event.getVerdict(), reverseMapped.getVerdict());
            assertEquals(1, reverseMapped.getTestResults().size());
            assertEquals("tc1", reverseMapped.getTestResults().get(0).getTestCaseId());
        }
    }
}
