package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.domain.*;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private PersistenceService persistenceService;

    // ── helpers ───────────────────────────────────────────────────────

    private ExecutionTaskEvent buildTaskEvent() {
        return ExecutionTaskEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(1L)
                .problemId(100L)
                .language("JAVA")
                .mode("SUBMIT")
                .sourceCode("public class Solution {}")
                .submittedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    private ExecutionResultEvent buildResultEvent(ExecutionTaskEvent task, List<TestCaseResultEvent> results) {
        return ExecutionResultEvent.builder()
                .executionId(task.getExecutionId())
                .userId(task.getUserId())
                .problemId(task.getProblemId())
                .problemName("Problem 100")
                .verdict(Verdict.ACCEPTED)
                .score(100)
                .totalRuntimeMs(50L)
                .memoryBytes(1024L)
                .testCaseResults(results)
                .build();
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void saveSubmission_withTestCaseResults_persistsSubmissionAndResults() {
        ExecutionTaskEvent task = buildTaskEvent();
        List<TestCaseResultEvent> tcResults = List.of(
                TestCaseResultEvent.builder()
                        .testCaseId(1L).verdict(Verdict.ACCEPTED)
                        .actualOutput("42").expectedOutput("42")
                        .runtimeMs(30L).memoryBytes(512L)
                        .build()
        );
        ExecutionResultEvent result = buildResultEvent(task, tcResults);

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository, times(1)).save(captor.capture());

        SubmissionEntity saved = captor.getValue();
        assertEquals(task.getExecutionId(), saved.getId());
        assertEquals(1L, saved.getUserId());
        assertEquals(100L, saved.getProblemId());
        assertEquals("Problem 100", saved.getProblemName());
        assertEquals("JAVA", saved.getLanguage());
        assertEquals("SUBMIT", saved.getMode());
        assertEquals("public class Solution {}", saved.getSourceCode());
        assertEquals(Verdict.ACCEPTED, saved.getVerdict());
        assertEquals(100, saved.getScore());
        assertEquals(50L, saved.getTotalRuntimeMs());
        assertEquals(1024L, saved.getMemoryBytes());
        assertEquals(1, saved.getTestResults().size());
    }

    @Test
    void saveSubmission_withNullTestCaseResults_savesEmptyTestResults() {
        ExecutionTaskEvent task = buildTaskEvent();
        ExecutionResultEvent result = buildResultEvent(task, null);

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        assertTrue(captor.getValue().getTestResults().isEmpty());
    }

    @Test
    void saveSubmission_withEmptyTestCaseResults_savesEmptyTestResults() {
        ExecutionTaskEvent task = buildTaskEvent();
        ExecutionResultEvent result = buildResultEvent(task, List.of());

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        assertTrue(captor.getValue().getTestResults().isEmpty());
    }

    @Test
    void saveSubmission_multipleTestCaseResults_allPersisted() {
        ExecutionTaskEvent task = buildTaskEvent();
        List<TestCaseResultEvent> tcResults = List.of(
                TestCaseResultEvent.builder().testCaseId(1L).verdict(Verdict.ACCEPTED).runtimeMs(10L).memoryBytes(256L).build(),
                TestCaseResultEvent.builder().testCaseId(2L).verdict(Verdict.WRONG_ANSWER).runtimeMs(20L).memoryBytes(256L).build(),
                TestCaseResultEvent.builder().testCaseId(3L).verdict(Verdict.RUNTIME_ERROR).errorMessage("NPE").runtimeMs(5L).memoryBytes(128L).build()
        );
        ExecutionResultEvent result = ExecutionResultEvent.builder()
                .executionId(task.getExecutionId())
                .userId(task.getUserId())
                .problemId(task.getProblemId())
                .problemName("Problem 100")
                .verdict(Verdict.WRONG_ANSWER)
                .score(33)
                .testCaseResults(tcResults)
                .build();

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        List<SubmissionTestResultEntity> testResults = captor.getValue().getTestResults();
        assertEquals(3, testResults.size());
        assertEquals(1L, testResults.get(0).getTestCaseId());
        assertEquals(Verdict.ACCEPTED, testResults.get(0).getVerdict());
        assertEquals(2L, testResults.get(1).getTestCaseId());
        assertEquals(Verdict.WRONG_ANSWER, testResults.get(1).getVerdict());
        assertEquals(3L, testResults.get(2).getTestCaseId());
        assertEquals("NPE", testResults.get(2).getErrorMessage());
    }

    @Test
    void saveSubmission_testResultLinkedToSubmission() {
        ExecutionTaskEvent task = buildTaskEvent();
        List<TestCaseResultEvent> tcResults = List.of(
                TestCaseResultEvent.builder().testCaseId(5L).verdict(Verdict.ACCEPTED)
                        .actualOutput("out").expectedOutput("out").runtimeMs(10L).memoryBytes(512L).build()
        );
        ExecutionResultEvent result = buildResultEvent(task, tcResults);

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        SubmissionEntity saved = captor.getValue();
        SubmissionTestResultEntity testResult = saved.getTestResults().get(0);
        // The test result should reference back to the parent submission
        assertSame(saved, testResult.getSubmission());
    }

    @Test
    void saveSubmission_submittedAtPreservedFromTaskEvent() {
        Instant submittedAt = Instant.parse("2026-05-01T08:30:00Z");
        ExecutionTaskEvent task = ExecutionTaskEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(2L).problemId(200L)
                .language("PYTHON").mode("RUN")
                .sourceCode("print('hello')")
                .submittedAt(submittedAt)
                .build();
        ExecutionResultEvent result = buildResultEvent(task, List.of());

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        assertEquals(submittedAt, captor.getValue().getSubmittedAt());
    }

    @Test
    void saveSubmission_completedAtSetToNow() {
        Instant before = Instant.now();
        ExecutionTaskEvent task = buildTaskEvent();
        ExecutionResultEvent result = buildResultEvent(task, List.of());

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        Instant completedAt = captor.getValue().getCompletedAt();
        assertNotNull(completedAt);
        assertFalse(completedAt.isBefore(before));
    }

    @Test
    void saveSubmission_testResultActualAndExpectedOutputPersisted() {
        ExecutionTaskEvent task = buildTaskEvent();
        List<TestCaseResultEvent> tcResults = List.of(
                TestCaseResultEvent.builder()
                        .testCaseId(7L).verdict(Verdict.WRONG_ANSWER)
                        .actualOutput("wrong").expectedOutput("correct")
                        .runtimeMs(15L).memoryBytes(300L)
                        .build()
        );
        ExecutionResultEvent result = buildResultEvent(task, tcResults);

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        SubmissionTestResultEntity tr = captor.getValue().getTestResults().get(0);
        assertEquals("wrong", tr.getActualOutput());
        assertEquals("correct", tr.getExpectedOutput());
        assertEquals(15L, tr.getRuntimeMs());
        assertEquals(300L, tr.getMemoryBytes());
    }

    @Test
    void saveSubmission_compileErrorVerdict_persistedCorrectly() {
        ExecutionTaskEvent task = buildTaskEvent();
        ExecutionResultEvent result = ExecutionResultEvent.builder()
                .executionId(task.getExecutionId())
                .userId(task.getUserId())
                .problemId(task.getProblemId())
                .problemName("Problem 100")
                .verdict(Verdict.COMPILE_ERROR)
                .score(0)
                .testCaseResults(List.of())
                .build();

        persistenceService.saveSubmission(task, result);

        ArgumentCaptor<SubmissionEntity> captor = ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(captor.capture());
        assertEquals(Verdict.COMPILE_ERROR, captor.getValue().getVerdict());
        assertEquals(0, captor.getValue().getScore());
    }
}
