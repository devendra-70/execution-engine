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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExecutionOrchestratorServiceTest — Unit tests for ExecutionOrchestratorService
 * (SRS §4.3, §6, §10, §11; EPMICMPCOD-532, EPMICMPCOD-533).
 *
 * <p>Coverage target: ≥90% (services)
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
    private ContainerPoolService.ContainerHandle fakeContainer;

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

        fakeContainer = new ContainerPoolService.ContainerHandle(
                UUID.randomUUID().toString(), "container-abc", "localhost", 20000, Instant.now());
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-533: Single-container submission lifecycle — success path
    // -------------------------------------------------------------------------

    @Test
    public void testExecute_Success_OneContainerForAllTestCases() throws Exception {
        TestCaseDto testCase = buildTestCase(1L, "5 10", "15");
        TestCaseResultDto resultDto = buildResult(1L, "PASS", "15");

        when(testCaseService.getTestCases(anyLong())).thenReturn(Arrays.asList(testCase));
        when(containerPoolService.acquire(any())).thenReturn(fakeContainer);
        when(sandboxClient.executeViaSocket(eq("localhost"), eq(20000), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(resultDto));
        when(verdictAggregator.aggregateVerdict(any())).thenReturn("PASSED");
        when(verdictAggregator.calculateScore(anyLong(), anyInt())).thenReturn(100.0);

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        assertNotNull(result);
        assertEquals(testExecutionId, result.getExecutionId());
        assertEquals("PASSED", result.getVerdict());
        // Container must be released (not discarded) on success (EPMICMPCOD-533 AC 6)
        verify(containerPoolService, times(1)).release(fakeContainer);
        verify(containerPoolService, never()).discardAndReplace(any());
    }

    @Test
    public void testExecute_UsesContainerHostAndPort() throws Exception {
        // Container with custom host/port
        ContainerPoolService.ContainerHandle customContainer = new ContainerPoolService.ContainerHandle(
                UUID.randomUUID().toString(), "container-xyz", "10.0.0.1", 19999, Instant.now());

        when(testCaseService.getTestCases(anyLong())).thenReturn(Arrays.asList(buildTestCase(1L, "1", "1")));
        when(containerPoolService.acquire(any())).thenReturn(customContainer);
        when(sandboxClient.executeViaSocket(eq("10.0.0.1"), eq(19999), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(buildResult(1L, "PASS", "1")));
        when(verdictAggregator.aggregateVerdict(any())).thenReturn("PASSED");
        when(verdictAggregator.calculateScore(anyLong(), anyInt())).thenReturn(100.0);

        executionOrchestratorService.execute(testEvent);

        // Socket call must use the container's own host/port
        verify(sandboxClient).executeViaSocket(eq("10.0.0.1"), eq(19999), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-533 AC 4: COMPILE_ERROR → release (container healthy)
    // -------------------------------------------------------------------------

    @Test
    public void testExecute_CompileError_ReleasesContainer() throws Exception {
        TestCaseResultDto compileError = TestCaseResultDto.builder()
                .testCaseId(-1L)
                .status("COMPILE_ERROR")
                .actualOutput("error: ';' expected")
                .expectedOutput("")
                .executionTimeMs(0L)
                .memoryBytes(0L)
                .build();

        when(testCaseService.getTestCases(anyLong())).thenReturn(Arrays.asList(buildTestCase(1L, "in", "out")));
        when(containerPoolService.acquire(any())).thenReturn(fakeContainer);
        when(sandboxClient.executeViaSocket(any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(compileError));

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        assertEquals("COMPILE_ERROR", result.getVerdict());
        // Container is healthy after compile error — must be released, NOT discarded
        verify(containerPoolService, times(1)).release(fakeContainer);
        verify(containerPoolService, never()).discardAndReplace(any());
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-532: TLE → SIGKILL via discardAndReplace
    // -------------------------------------------------------------------------

    @Test
    public void testExecute_TLE_DiscardsContainerAndReturnsTLEVerdict() throws Exception {
        // Set an extremely short timeout so the Future will time out
        ReflectionTestUtils.setField(executionOrchestratorService, "executionTimeoutMs", 1L);

        when(testCaseService.getTestCases(anyLong())).thenReturn(Arrays.asList(buildTestCase(1L, "in", "out")));
        when(containerPoolService.acquire(any())).thenReturn(fakeContainer);
        // Make the socket call block for longer than the timeout
        when(sandboxClient.executeViaSocket(any(), anyInt(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Thread.sleep(500);
                    return Collections.emptyList();
                });

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        // TLE verdict must be returned (SRS §10, EPMICMPCOD-532)
        assertEquals("TIME_LIMIT_EXCEEDED", result.getVerdict());
        // Container must be discarded — never released (EPMICMPCOD-532 AC 2)
        verify(containerPoolService, times(1)).discardAndReplace(fakeContainer);
        verify(containerPoolService, never()).release(fakeContainer);
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    public void testExecute_NoTestCases_ReturnsUnknownVerdict() throws Exception {
        when(testCaseService.getTestCases(anyLong())).thenReturn(Collections.emptyList());

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        assertEquals("UNKNOWN", result.getVerdict());
        // No container acquired when there are no test cases
        verify(containerPoolService, never()).acquire(any());
    }

    @Test
    public void testExecute_ContainerAcquisitionFailure_ReturnsRuntimeError() throws Exception {
        when(testCaseService.getTestCases(anyLong()))
                .thenReturn(Arrays.asList(buildTestCase(1L, "in", "out")));
        when(containerPoolService.acquire(any()))
                .thenThrow(new RuntimeException("No container available"));

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        assertEquals("RUNTIME_ERROR", result.getVerdict());
    }

    @Test
    public void testExecute_FiveTestCases_OneContainerOneRelease() throws Exception {
        // EPMICMPCOD-533 AC 8: 5 test cases → 5 results, 1 acquire, 1 release
        var testCases = Arrays.asList(
                buildTestCase(1L, "1", "1"),
                buildTestCase(2L, "2", "2"),
                buildTestCase(3L, "3", "3"),
                buildTestCase(4L, "4", "4"),
                buildTestCase(5L, "5", "5")
        );
        var results = Arrays.asList(
                buildResult(1L, "PASS", "1"),
                buildResult(2L, "PASS", "2"),
                buildResult(3L, "PASS", "3"),
                buildResult(4L, "PASS", "4"),
                buildResult(5L, "PASS", "5")
        );

        when(testCaseService.getTestCases(anyLong())).thenReturn(testCases);
        when(containerPoolService.acquire(any())).thenReturn(fakeContainer);
        when(sandboxClient.executeViaSocket(any(), anyInt(), any(), any(), any(), any())).thenReturn(results);
        when(verdictAggregator.aggregateVerdict(any())).thenReturn("PASSED");
        when(verdictAggregator.calculateScore(anyLong(), anyInt())).thenReturn(100.0);

        ExecutionResultEvent result = executionOrchestratorService.execute(testEvent);

        assertEquals(5, result.getTestCaseResults().size());
        verify(containerPoolService, times(1)).acquire(any());
        verify(containerPoolService, times(1)).release(fakeContainer);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestCaseDto buildTestCase(Long id, String input, String expected) {
        return TestCaseDto.builder()
                .id(id)
                .problemId(100L)
                .input(input)
                .expectedOutput(expected)
                .timeoutMs(1000)
                .build();
    }

    private TestCaseResultDto buildResult(Long id, String status, String actual) {
        return TestCaseResultDto.builder()
                .testCaseId(id)
                .status(status)
                .actualOutput(actual)
                .expectedOutput(actual)
                .executionTimeMs(10L)
                .memoryBytes(1024L)
                .build();
    }
}
