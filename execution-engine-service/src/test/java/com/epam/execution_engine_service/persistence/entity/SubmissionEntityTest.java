package com.epam.execution_engine_service.persistence.entity;

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
 * Unit tests for SubmissionEntity JPA entity.
 * Covers entity construction, field mapping, lifecycle hooks, and relationships.
 *
 * Test cases: 15+
 */
@DisplayName("SubmissionEntity Tests")
class SubmissionEntityTest {

    private SubmissionEntity entity;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        entity = SubmissionEntity.builder()
            .executionId(executionId)
            .userId("user123")
            .problemId("problem456")
            .language("JAVA")
            .mode("SUBMIT")
            .verdict("PASSED")
            .status("COMPLETED")
            .score(100)
            .totalRuntimeMs(1500L)
            .memoryBytes(2048000L)
            .rawOutput("Output text")
            .errorOutput(null)
            .submittedCode("public class Main {}")
            .submittedAt(OffsetDateTime.now().minusMinutes(5))
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();
    }

    @Nested
    @DisplayName("Entity Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct entity with all required fields")
        void testConstructionWithAllFields() {
            assertNotNull(entity.getExecutionId());
            assertEquals("user123", entity.getUserId());
            assertEquals("problem456", entity.getProblemId());
            assertEquals("JAVA", entity.getLanguage());
            assertEquals("SUBMIT", entity.getMode());
        }

        @Test
        @DisplayName("Should handle null optional fields")
        void testNullOptionalFields() {
            entity.setScore(null);
            entity.setRawOutput(null);
            entity.setErrorOutput(null);
            entity.setCompletedAt(null);

            assertNull(entity.getScore());
            assertNull(entity.getRawOutput());
            assertNull(entity.getErrorOutput());
            assertNull(entity.getCompletedAt());
        }

        @Test
        @DisplayName("Should initialize empty test results list")
        void testEmptyTestResultsList() {
            entity.setTestResults(new ArrayList<>());
            assertNotNull(entity.getTestResults());
            assertTrue(entity.getTestResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Field Mapping")
    class FieldMappingTests {

        @Test
        @DisplayName("Should store and retrieve executionId (idempotency key)")
        void testExecutionIdMapping() {
            assertEquals(executionId, entity.getExecutionId());
        }

        @Test
        @DisplayName("Should store and retrieve userId")
        void testUserIdMapping() {
            entity.setUserId("user999");
            assertEquals("user999", entity.getUserId());
        }

        @Test
        @DisplayName("Should store and retrieve problemId")
        void testProblemIdMapping() {
            entity.setProblemId("problem999");
            assertEquals("problem999", entity.getProblemId());
        }

        @Test
        @DisplayName("Should store and retrieve verdict")
        void testVerdictMapping() {
            entity.setVerdict("WRONG_ANSWER");
            assertEquals("WRONG_ANSWER", entity.getVerdict());
        }

        @Test
        @DisplayName("Should store and retrieve status")
        void testStatusMapping() {
            entity.setStatus("FAILED");
            assertEquals("FAILED", entity.getStatus());
        }

        @Test
        @DisplayName("Should store and retrieve runtimes")
        void testRuntimeMapping() {
            entity.setTotalRuntimeMs(5000L);
            assertEquals(5000L, entity.getTotalRuntimeMs());
        }

        @Test
        @DisplayName("Should store and retrieve memory bytes")
        void testMemoryBytesMapping() {
            entity.setMemoryBytes(4096000L);
            assertEquals(4096000L, entity.getMemoryBytes());
        }

        @Test
        @DisplayName("Should store and retrieve code snippets")
        void testCodeMapping() {
            String code = "long code snippet here";
            entity.setSubmittedCode(code);
            assertEquals(code, entity.getSubmittedCode());
        }

        @Test
        @DisplayName("Should store and retrieve timestamps")
        void testTimestampMapping() {
            OffsetDateTime submitted = OffsetDateTime.now().minusHours(1);
            OffsetDateTime completed = OffsetDateTime.now();
            entity.setSubmittedAt(submitted);
            entity.setCompletedAt(completed);

            assertEquals(submitted, entity.getSubmittedAt());
            assertEquals(completed, entity.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("JPA Lifecycle Hooks")
    class LifecycleHookTests {

        @Test
        @DisplayName("onCreate() should set createdAt and updatedAt on insert")
        void testOnCreateHook() {
            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
            assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
        }

        @Test
        @DisplayName("onUpdate() should update updatedAt")
        void testOnUpdateHook() {
            entity.onCreate();
            OffsetDateTime createdTime = entity.getCreatedAt();

            // Simulate time passage and update
            try {
                Thread.sleep(10);  // Small delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            entity.onUpdate();

            assertEquals(createdTime, entity.getCreatedAt());  // Should not change
            assertNotNull(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("Timestamps should be OffsetDateTime type (timezone-aware)")
        void testTimestampTypes() {
            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
            assertTrue(entity.getCreatedAt() instanceof OffsetDateTime);
        }
    }

    @Nested
    @DisplayName("Relationships")
    class RelationshipTests {

        @Test
        @DisplayName("Should support cascaded test results (one-to-many)")
        void testTestResultsRelationship() {
            List<SubmissionTestResultEntity> results = new ArrayList<>();
            SubmissionTestResultEntity result1 = SubmissionTestResultEntity.builder()
                .testCaseId("tc1")
                .status("PASSED")
                .build();
            results.add(result1);

            entity.setTestResults(results);

            assertEquals(1, entity.getTestResults().size());
            assertEquals("tc1", entity.getTestResults().get(0).getTestCaseId());
        }

        @Test
        @DisplayName("Should support multiple test results")
        void testMultipleTestResults() {
            List<SubmissionTestResultEntity> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(SubmissionTestResultEntity.builder()
                    .testCaseId("tc" + i)
                    .status("PASSED")
                    .build());
            }

            entity.setTestResults(results);

            assertEquals(5, entity.getTestResults().size());
        }
    }

    @Nested
    @DisplayName("Equality and Hashing")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal if all fields match")
        void testEquality() {
            SubmissionEntity other = SubmissionEntity.builder()
                .executionId(executionId)
                .userId("user123")
                .problemId("problem456")
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .score(100)
                .totalRuntimeMs(1500L)
                .memoryBytes(2048000L)
                .rawOutput("Output text")
                .errorOutput(null)
                .submittedCode("public class Main {}")
                .submittedAt(entity.getSubmittedAt())
                .completedAt(entity.getCompletedAt())
                .testResults(new ArrayList<>())
                .build();

            assertEquals(entity, other);
        }

        @Test
        @DisplayName("Should have consistent hashCode")
        void testHashCode() {
            int hash1 = entity.hashCode();
            int hash2 = entity.hashCode();

            assertEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class StringRepresentationTests {

        @Test
        @DisplayName("Should provide useful toString() output")
        void testToString() {
            String str = entity.toString();

            assertNotNull(str);
            assertTrue(str.contains("user123") || str.contains("PASSED"));
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder should create valid entity")
        void testBuilderCreatesValidEntity() {
            SubmissionEntity builtEntity = SubmissionEntity.builder()
                .executionId(UUID.randomUUID())
                .userId("user123")
                .problemId("problem456")
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(1000L)
                .memoryBytes(2048000L)
                .submittedAt(OffsetDateTime.now())
                .build();

            assertNotNull(builtEntity.getExecutionId());
            assertEquals("user123", builtEntity.getUserId());
        }

        @Test
        @DisplayName("Builder with minimal required fields")
        void testBuilderMinimalFields() {
            SubmissionEntity minimal = SubmissionEntity.builder()
                .executionId(UUID.randomUUID())
                .userId("user")
                .problemId("problem")
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(0L)
                .memoryBytes(0L)
                .submittedAt(OffsetDateTime.now())
                .build();

            assertNotNull(minimal);
            assertEquals("user", minimal.getUserId());
        }
    }
}
