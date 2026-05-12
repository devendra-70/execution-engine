package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import com.epam.execution_engine_service.dto.ExecutionTaskEvent;
import com.epam.execution_engine_service.dto.TestCaseDto;
import com.epam.execution_engine_service.dto.TestCaseResultDto;
import com.epam.execution_engine_service.util.VerdictAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * ExecutionOrchestratorServiceTest — Unit tests for ExecutionOrchestratorService (SRS §6, §11)
 * 
 * Coverage target: ≥90% (services)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExecutionOrchestratorServiceTest {

    @Mock
    private TestCaseService testCaseService;

    @Mock
    private ContainerPoolService containerPoolService;

    @Mock
    private VerdictAggregator verdictAggregator;
    
    @Mock
    private RedisExecutionStatusService redisStatusService;
    
    @Mock
    private ExecutionResultBroadcaster resultBroadcaster;
    
    @Mock
    private SandboxClient sandboxClient;

    @InjectMocks
    private ExecutionOrchestratorService executionOrchestratorService;

    private ExecutionTaskEvent testEvent;
    private UUID testExecutionId;

    @BeforeEach
    public void setUp() {
        testExecutionId = UUID.randomUUID();
        testEvent = ExecutionTaskEvent.builder()
                .executionId(testExecutionId)
                .userId(123L)
                .problemId(100L)
                .language("java")
                .mode("submit")
                .sourceCode("public class Solution { }")
                .submittedAt(Instant.now())
                .build();

        ReflectionTestUtils.setField(executionOrchestratorService, "executionTimeoutMs", 3000L);
        ReflectionTestUtils.setField(executionOrchestratorService, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(executionOrchestratorService, "sandboxPort", 9999);
    }

    @Test
    public void testExecute_Success() {
        // Arrange
        TestCaseDto testCase = TestCaseDto.builder()
                .id(1L)
                .problemId(100L)
                .input("5 10")
                .expectedOutput("15")
                .timeoutMs(1000)
                .build();

        TestCaseResultDto resultDto = TestCaseResultDto.builder()
                .testCaseId(1L)
                .status("PASS")
                .actualOutput("15")
                .expectedOutput("15")
                .executionTimeMs(10L)
                .memoryBytes(1024L)
                .build();

        when(testCaseService.getTestCases(anyLong())).thenReturn(Arrays.asList(testCase));
        when(containerPoolService.acquire(any())).thenReturn(
                new ContainerPoolService.ContainerHandle(UUID.randomUUID().toString(), Instant.now())
        );
        when(sandboxClient.executeViaSocket(any(), anyInt(), any(), any(), any(), any())).thenReturn(Arrays.asList(resultDto));
        when(verdictAggregator.aggregateVerdict(any())).thenReturn("PASSED");
        when(verdictAggregator.calculateScore(anyLong(), anyInt())).thenReturn(100.0);

        // Act
        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        // Assert
        assertNotNull(result);
        assertEquals(testExecutionId, result.getExecutionId());
        assertEquals(123L, result.getUserId());
        assertEquals("PASSED", result.getVerdict());
    }

    @Test
    public void testExecute_NoTestCases() {
        // Arrange
        when(testCaseService.getTestCases(100L)).thenReturn(Collections.emptyList());
        when(containerPoolService.acquire(any())).thenReturn(
                new ContainerPoolService.ContainerHandle(UUID.randomUUID().toString(), Instant.now())
        );
        when(sandboxClient.executeViaSocket(any(), anyInt(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // Act
        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        // Assert
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getVerdict());
        assertEquals(0.0, result.getScore());
    }

    @Test
    public void testExecute_ContainerAcquisitionFailure() {
        // Arrange
        TestCaseDto testCase = TestCaseDto.builder()
                .id(1L)
                .problemId(100L)
                .build();

        when(testCaseService.getTestCases(100L)).thenReturn(Arrays.asList(testCase));
        when(containerPoolService.acquire(any())).thenThrow(
                new RuntimeException("No container available")
        );

        // Act
        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        // Assert
        assertNotNull(result);
        assertEquals("RUNTIME_ERROR", result.getVerdict());
    }

}
