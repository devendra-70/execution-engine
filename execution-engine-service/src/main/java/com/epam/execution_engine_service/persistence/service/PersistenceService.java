package com.epam.execution_engine_service.persistence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceService {

    private final SubmissionRepository submissionRepository;

    @Transactional
    public void saveSubmission(ExecutionTaskEvent taskEvent, ExecutionResultEvent resultEvent) {
        SubmissionEntity submission = SubmissionEntity.builder()
                .id(taskEvent.getExecutionId())
                .userId(taskEvent.getUserId())
                .problemId(taskEvent.getProblemId())
                .problemName(resultEvent.getProblemName())
                .language(taskEvent.getLanguage())
                .mode(taskEvent.getMode())
                .sourceCode(taskEvent.getSourceCode())
                .verdict(resultEvent.getVerdict())
                .score(resultEvent.getScore())
                .totalRuntimeMs(resultEvent.getTotalRuntimeMs())
                .memoryBytes(resultEvent.getMemoryBytes())
                .submittedAt(taskEvent.getSubmittedAt())
                .completedAt(Instant.now())
                .build();

        if (resultEvent.getTestCaseResults() != null) {
            List<SubmissionTestResultEntity> testResults = resultEvent.getTestCaseResults().stream()
                    .map(tc -> SubmissionTestResultEntity.builder()
                            .submission(submission)
                            .testCaseId(tc.getTestCaseId())
                            .verdict(tc.getVerdict())
                            .actualOutput(tc.getActualOutput())
                            .expectedOutput(tc.getExpectedOutput())
                            .runtimeMs(tc.getRuntimeMs())
                            .memoryBytes(tc.getMemoryBytes())
                            .errorMessage(tc.getErrorMessage())
                            .build())
                    .toList();
            submission.getTestResults().addAll(testResults);
        }

        submissionRepository.save(submission);
        log.info("Saved submission {} for user {}", taskEvent.getExecutionId(), taskEvent.getUserId());
    }
}

