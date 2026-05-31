package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.epam.execution_engine_service.domain.ExecutionTaskEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * SRP: Single responsibility — map domain events to JPA entities.
 * Extracted from {@link PersistenceService} so the service handles only transaction + persistence,
 * not object transformation.
 */
@Component
public class SubmissionMapper {

    public SubmissionEntity toEntity(ExecutionTaskEvent taskEvent, ExecutionResultEvent resultEvent) {
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

        List<SubmissionTestResultEntity> testResults = toTestResultEntities(
                resultEvent, submission);
        submission.getTestResults().addAll(testResults);
        return submission;
    }

    private List<SubmissionTestResultEntity> toTestResultEntities(
            ExecutionResultEvent resultEvent, SubmissionEntity submission) {

        if (resultEvent.getTestCaseResults() == null) {
            return Collections.emptyList();
        }
        return resultEvent.getTestCaseResults().stream()
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
    }
}

