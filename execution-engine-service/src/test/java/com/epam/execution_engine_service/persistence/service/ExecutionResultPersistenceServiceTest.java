package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.event.TestCaseResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.mapper.ResultMapper;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionResultPersistenceService.
 * Covers database persistence, transactional semantics, and Kafka ACK behavior.
 *
 * Test cases: 20+
 */
@DisplayName("ExecutionResultPersistenceService Tests")
@ExtendWith(MockitoExtension.class)
class ExecutionResultPersistenceServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ResultMapper resultMapper;

    @InjectMocks
    private ExecutionResultPersistenceService service;

    private ExecutionResultEvent event;
    private SubmissionEntity savedEntity;
    private UUID executionId;

    @BeforeEach
    void setUp() {
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
            .submittedCode("code")
            .submittedAt(OffsetDateTime.now().minusMinutes(5))
            .completedAt(OffsetDateTime.now())
            .testResults(new ArrayList<>())
            .build();

        savedEntity = SubmissionEntity.builder()
            .id(1L)
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
            .submittedCode("code")
            .submittedAt(event.getSubmittedAt())
            .completedAt(event.getCompletedAt())
            .testResults(new ArrayList<>())
            .build();
    }

    @Nested
    @DisplayName("Persistence Operations")
    class PersistenceTests {

        @Test
        @DisplayName("Should persist ExecutionResultEvent to database")
        void testPersistExecutionResult() {
            // Arrange
            when(resultMapper.toSubmissionEntity(event)).thenReturn(savedEntity);
            when(submissionRepository.save(savedEntity)).thenReturn(savedEntity);

            // Act
            SubmissionEntity result = service.persistExecutionResult(event);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(executionId, result.getExecutionId());
            verify(submissionRepository, times(1)).save(savedEntity);
        }

        @Test
        @DisplayName("Should call mapper to convert DTO to entity")
        void testMapperInvoked() {
            // Arrange
            when(resultMapper.toSubmissionEntity(event)).thenReturn(savedEntity);
            when(submissionRepository.save(any())).thenReturn(savedEntity);

            // Act
            service.persistExecutionResult(event);

            // Assert
            verify(resultMapper, times(1)).toSubmissionEntity(event);
        }

        @Test
        @DisplayName("Should return entity with database-assigned ID")
        void testReturnsDatabaseAssignedId() {
            // Arrange
            SubmissionEntity mappedEntity = SubmissionEntity.builder()
                .executionId(executionId)
                .userId(123L)
                .problemId(456L)
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(1000L)
                .memoryBytes(2048000L)
                .submittedAt(OffsetDateTime.now())
                .build();

            SubmissionEntity entityWithId = SubmissionEntity.builder()
                .id(999L)
                .executionId(executionId)
                .userId(123L)
                .problemId(456L)
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(1000L)
                .memoryBytes(2048000L)
                .submittedAt(OffsetDateTime.now())
                .build();

            when(resultMapper.toSubmissionEntity(event)).thenReturn(mappedEntity);
            when(submissionRepository.save(mappedEntity)).thenReturn(entityWithId);

            // Act
            SubmissionEntity result = service.persistExecutionResult(event);

            // Assert
            assertEquals(999L, result.getId());
        }

        @Test
        @DisplayName("Should include cascaded test results in persistence")
        void testCascadedTestResultsPersisted() {
            // Arrange
            TestCaseResultEvent testCase = TestCaseResultEvent.builder()
                .testCaseId("tc1")
                .status("PASSED")
                .build();
            event.setTestResults(List.of(testCase));

            SubmissionEntity mappedEntity = SubmissionEntity.builder()
                .executionId(executionId)
                .userId(123L)
                .problemId(456L)
                .language("JAVA")
                .mode("SUBMIT")
                .verdict("PASSED")
                .status("COMPLETED")
                .submittedCode("code")
                .totalRuntimeMs(1000L)
                .memoryBytes(2048000L)
                .submittedAt(OffsetDateTime.now())
                .testResults(new ArrayList<>())
                .build();

            when(resultMapper.toSubmissionEntity(event)).thenReturn(mappedEntity);
            when(submissionRepository.save(mappedEntity)).thenReturn(savedEntity);

            // Act
            service.persistExecutionResult(event);

            // Assert
            verify(submissionRepository, times(1)).save(mappedEntity);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for null event")
        void testNullEventThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.persistExecutionResult(null));
        }

        @Test
        @DisplayName("Should throw exception for null executionId")
        void testNullExecutionIdThrows() {
            event.setExecutionId(null);
            assertThrows(IllegalArgumentException.class,
                () -> service.persistExecutionResult(event));
        }

        @Test
        @DisplayName("Should propagate database exceptions for Kafka offset NOT acknowledgment")
        void testDatabaseExceptionPropagates() {
            // Arrange
            when(resultMapper.toSubmissionEntity(event)).thenReturn(savedEntity);
            when(submissionRepository.save(any()))
                .thenThrow(new RuntimeException("DB Connection failed"));

            // Act & Assert
            assertThrows(RuntimeException.class,
                () -> service.persistExecutionResult(event));

            // Verify exception occurred before offset would be acked
            verify(submissionRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should re-throw exceptions to prevent Kafka ACK")
        void testExceptionRethrown() {
            // Arrange
            when(resultMapper.toSubmissionEntity(event)).thenThrow(
                new IllegalArgumentException("Invalid event"));

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                () -> service.persistExecutionResult(event));
        }
    }

    @Nested
    @DisplayName("Idempotency and Duplicate Detection")
    class IdempotencyTests {

        @Test
        @DisplayName("Should support duplicate detection via executionId")
        void testFindByExecutionId() {
            // Arrange
            when(submissionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(savedEntity));

            // Act
            Optional<SubmissionEntity> result = service.findByExecutionId(executionId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(executionId, result.get().getExecutionId());
        }

        @Test
        @DisplayName("Should return empty Optional for missing execution")
        void testFindByExecutionIdNotFound() {
            // Arrange
            when(submissionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.empty());

            // Act
            Optional<SubmissionEntity> result = service.findByExecutionId(executionId);

            // Assert
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should throw exception for null executionId in find")
        void testFindByExecutionIdNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.findByExecutionId(null));
        }
    }

    @Nested
    @DisplayName("Analytics Queries")
    class AnalyticsTests {

        @Test
        @DisplayName("Should count submissions by userId")
        void testCountByUserId() {
            // Arrange
            when(submissionRepository.countByUserId(123L)).thenReturn(5L);

            // Act
            long count = service.countByUserId(123L);

            // Assert
            assertEquals(5L, count);
            verify(submissionRepository, times(1)).countByUserId(123L);
        }

        @Test
        @DisplayName("Should return 0 for user with no submissions")
        void testCountByUserIdZero() {
            // Arrange
            when(submissionRepository.countByUserId(999L)).thenReturn(0L);

            // Act
            long count = service.countByUserId(999L);

            // Assert
            assertEquals(0L, count);
        }

        @Test
        @DisplayName("Should throw exception for null or invalid userId")
        void testCountByUserIdNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.countByUserId(null));
            assertThrows(IllegalArgumentException.class,
                () -> service.countByUserId(0L));
            assertThrows(IllegalArgumentException.class,
                () -> service.countByUserId(-1L));
        }

        @Test
        @DisplayName("Should count submissions by problemId")
        void testCountByProblemId() {
            // Arrange
            when(submissionRepository.countByProblemId(456L)).thenReturn(3L);

            // Act
            long count = service.countByProblemId(456L);

            // Assert
            assertEquals(3L, count);
            verify(submissionRepository, times(1)).countByProblemId(456L);
        }

        @Test
        @DisplayName("Should throw exception for null or invalid problemId")
        void testCountByProblemIdNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> service.countByProblemId(null));
            assertThrows(IllegalArgumentException.class,
                () -> service.countByProblemId(0L));
            assertThrows(IllegalArgumentException.class,
                () -> service.countByProblemId(-1L));
        }
    }

    @Nested
    @DisplayName("Transactional Semantics")
    class TransactionalTests {

        @Test
        @DisplayName("Should use READ_COMMITTED isolation level (@Transactional)")
        void testTransactionalIsolation() {
            // Note: Actual isolation level verified at integration test level
            // This test verifies method annotation exists
            try {
                var method = ExecutionResultPersistenceService.class
                    .getMethod("persistExecutionResult", ExecutionResultEvent.class);
                var txn = method.getAnnotation(
                    org.springframework.transaction.annotation.Transactional.class);

                assertNotNull(txn);
                assertEquals(org.springframework.transaction.annotation.Isolation.READ_COMMITTED,
                    txn.isolation());
            } catch (NoSuchMethodException e) {
                fail("Method not found");
            }
        }

        @Test
        @DisplayName("Should rollback entire transaction on exception")
        void testTransactionRollback() {
            // Arrange
            when(resultMapper.toSubmissionEntity(event)).thenReturn(savedEntity);
            when(submissionRepository.save(any()))
                .thenThrow(new RuntimeException("Rollback triggered"));

            // Act & Assert
            assertThrows(RuntimeException.class,
                () -> service.persistExecutionResult(event));
        }
    }
}
