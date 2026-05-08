package com.epam.execution_engine_service.persistence.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubmissionTestResultEntity JPA entity.
 * Covers entity construction, field mapping, lifecycle hooks, and FK relationships.
 *
 * Test cases: 12+
 */
@DisplayName("SubmissionTestResultEntity Tests")
class SubmissionTestResultEntityTest {

    private SubmissionTestResultEntity entity;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        entity = SubmissionTestResultEntity.builder()
            .executionId(executionId)
            .testCaseId("test_1")
            .status("PASSED")
            .runtimeMs(500L)
            .memoryBytes(1024000L)
            .expectedOutput("Hello World")
            .actualOutput("Hello World")
            .errorOutput(null)
            .build();
    }

    @Nested
    @DisplayName("Entity Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should construct entity with all fields")
        void testConstruction() {
            assertNotNull(entity.getExecutionId());
            assertEquals("test_1", entity.getTestCaseId());
            assertEquals("PASSED", entity.getStatus());
            assertEquals(500L, entity.getRuntimeMs());
        }

        @Test
        @DisplayName("Should handle null optional fields")
        void testNullOptionalFields() {
            entity.setExpectedOutput(null);
            entity.setActualOutput(null);
            entity.setErrorOutput(null);

            assertNull(entity.getExpectedOutput());
            assertNull(entity.getActualOutput());
            assertNull(entity.getErrorOutput());
        }
    }

    @Nested
    @DisplayName("Field Mapping")
    class FieldMappingTests {

        @Test
        @DisplayName("Should store and retrieve executionId (FK)")
        void testExecutionIdMapping() {
            assertEquals(executionId, entity.getExecutionId());
        }

        @Test
        @DisplayName("Should store and retrieve testCaseId")
        void testTestCaseIdMapping() {
            entity.setTestCaseId("test_99");
            assertEquals("test_99", entity.getTestCaseId());
        }

        @Test
        @DisplayName("Should store and retrieve status")
        void testStatusMapping() {
            entity.setStatus("FAILED");
            assertEquals("FAILED", entity.getStatus());
        }

        @Test
        @DisplayName("Should store and retrieve runtime")
        void testRuntimeMapping() {
            entity.setRuntimeMs(2000L);
            assertEquals(2000L, entity.getRuntimeMs());
        }

        @Test
        @DisplayName("Should store and retrieve memory usage")
        void testMemoryBytesMapping() {
            entity.setMemoryBytes(5120000L);
            assertEquals(5120000L, entity.getMemoryBytes());
        }

        @Test
        @DisplayName("Should store and retrieve outputs")
        void testOutputMapping() {
            entity.setExpectedOutput("expected");
            entity.setActualOutput("actual");
            entity.setErrorOutput("error message");

            assertEquals("expected", entity.getExpectedOutput());
            assertEquals("actual", entity.getActualOutput());
            assertEquals("error message", entity.getErrorOutput());
        }
    }

    @Nested
    @DisplayName("JPA Lifecycle Hooks")
    class LifecycleHookTests {

        @Test
        @DisplayName("onCreate() should set createdAt and updatedAt")
        void testOnCreateHook() {
            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("onUpdate() should update updatedAt only")
        void testOnUpdateHook() {
            entity.onCreate();
            OffsetDateTime createdTime = entity.getCreatedAt();

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            entity.onUpdate();

            assertEquals(createdTime, entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("Foreign Key Relationship")
    class RelationshipTests {

        @Test
        @DisplayName("Should support ManyToOne relationship to SubmissionEntity")
        void testSubmissionRelationship() {
            SubmissionEntity submission = SubmissionEntity.builder()
                .executionId(executionId)
                .userId("user1")
                .problemId("problem1")
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(1000L)
                .memoryBytes(2048000L)
                .submittedAt(OffsetDateTime.now())
                .build();

            entity.setSubmission(submission);

            assertNotNull(entity.getSubmission());
            assertEquals(executionId, entity.getSubmission().getExecutionId());
        }
    }

    @Nested
    @DisplayName("Equality and Hashing")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal if all fields match")
        void testEquality() {
            SubmissionTestResultEntity other = SubmissionTestResultEntity.builder()
                .executionId(executionId)
                .testCaseId("test_1")
                .status("PASSED")
                .runtimeMs(500L)
                .memoryBytes(1024000L)
                .expectedOutput("Hello World")
                .actualOutput("Hello World")
                .errorOutput(null)
                .build();

            assertEquals(entity, other);
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder should create valid entity")
        void testBuilderCreatesValidEntity() {
            SubmissionTestResultEntity builtEntity = SubmissionTestResultEntity.builder()
                .executionId(UUID.randomUUID())
                .testCaseId("tc1")
                .status("PASSED")
                .build();

            assertNotNull(builtEntity.getExecutionId());
            assertEquals("tc1", builtEntity.getTestCaseId());
        }
    }
}
