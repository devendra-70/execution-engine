package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import com.epam.execution_engine_service.dto.TestCaseResultDto;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import com.epam.execution_engine_service.persistence.repository.SubmissionTestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PersistenceServiceTest — Unit tests for PersistenceService (SRS §5.2)
 * 
 * Coverage target: ≥90% (services)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PersistenceServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @InjectMocks
    private PersistenceService persistenceService;

    private ExecutionResultEvent testResultEvent;
    private UUID testExecutionId;

    @BeforeEach
    public void setUp() {
        testExecutionId = UUID.randomUUID();
        testResultEvent = ExecutionResultEvent.builder()
                .executionId(testExecutionId)
                .userId(123L)
                .problemId(100L)
                .verdict("PASSED")
                .score(100.0)
                .totalRuntimeMs(500L)
                .totalMemoryBytes(2048L)
                .testCaseResults(Arrays.asList(
                        TestCaseResultDto.builder()
                                .testCaseId(1L)
                                .status("PASS")
                                .actualOutput("output")
                                .expectedOutput("output")
                                .executionTimeMs(100L)
                                .memoryBytes(1024L)
                                .build()
                ))
                .completedAt(Instant.now())
                .build();
    }

    @Test
    public void testPersistExecutionResult_Success() {
        // Arrange
        SubmissionEntity savedEntity = SubmissionEntity.builder()
                .id(1L)
                .executionId(testExecutionId)
                .userId(123L)
                .build();

        when(submissionRepository.save(any(SubmissionEntity.class))).thenReturn(savedEntity);
        when(submissionTestResultRepository.saveAll(any())).thenReturn(Arrays.asList());

        // Act
        SubmissionEntity result = persistenceService.persistExecutionResult(testResultEvent);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(testExecutionId, result.getExecutionId());
    }

    @Test
    public void testPersistExecutionResult_RepositoryError() {
        // Arrange
        when(submissionRepository.save(any(SubmissionEntity.class)))
                .thenThrow(new RuntimeException("DB connection error"));

        // Act & Assert
        assertThrows(RuntimeException.class, 
                () -> persistenceService.persistExecutionResult(testResultEvent));
    }

}
