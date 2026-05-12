package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.ExecutionResultEvent;
import com.epam.execution_engine_service.dto.TestCaseResultDto;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import com.epam.execution_engine_service.persistence.repository.SubmissionTestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * PersistenceService — Synchronous database write orchestration (SRS §5.2, §12)
 * 
 * Persists ExecutionResultEvent to PostgreSQL with batch optimization:
 * - Single transaction encompassing parent + children inserts
 * - Batch size: 50 (configurable via Hibernate properties)
 * - Kafka offset committed ONLY after DB success
 * 
 * On failure: transaction rolls back, exception propagated to Kafka listener.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;

    /**
     * Persist execution result to database (SRS §12, §14)
     * 
     * @param resultEvent ExecutionResultEvent from orchestrator
     * @return Persisted SubmissionEntity
     * @throws Exception if persistence fails (transaction rolls back)
     */
    @Transactional
    public SubmissionEntity persistExecutionResult(ExecutionResultEvent resultEvent) {

        try {
            // Create SubmissionEntity
            // C1 Fix: userId and problemId are now Long (match DTO types)
            // H3 Fix: score is now Double (match DTO type)
            SubmissionEntity submission = SubmissionEntity.builder()
                    .executionId(resultEvent.getExecutionId())
                    .userId(resultEvent.getUserId())
                    .problemId(resultEvent.getProblemId())
                    .verdict(resultEvent.getVerdict())
                    .score(resultEvent.getScore())
                    .totalRuntimeMs(resultEvent.getTotalRuntimeMs())
                    .memoryBytes(resultEvent.getTotalMemoryBytes())
                    .submittedAt(java.time.OffsetDateTime.now())
                    .completedAt(resultEvent.getCompletedAt() != null ? resultEvent.getCompletedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                    .testResults(new ArrayList<>())
                    .build();

            // Save parent entity (generates submissionId)
            submission = submissionRepository.save(submission);

            // Create and save child entities (batch insert via Hibernate batch_size)
            List<SubmissionTestResultEntity> testResults = new ArrayList<>();
            for (TestCaseResultDto testCase : resultEvent.getTestCaseResults()) {
                SubmissionTestResultEntity testResult = SubmissionTestResultEntity.builder()
                        .submission(submission)
                        .testCaseId(String.valueOf(testCase.getTestCaseId()))
                        .status(testCase.getStatus())
                        .actualOutput(testCase.getActualOutput() != null ? testCase.getActualOutput() : "")
                        .expectedOutput(testCase.getExpectedOutput() != null ? testCase.getExpectedOutput() : "")
                        .runtimeMs(testCase.getExecutionTimeMs())
                        .memoryBytes(testCase.getMemoryBytes())
                        .build();
                testResults.add(testResult);
            }

            submission.setTestResults(testResults);
            submissionTestResultRepository.saveAll(testResults);

            return submission;
        } catch (Exception e) {
            throw e;  // Exception propagates to Kafka listener; offset not committed
        }
    }

}
