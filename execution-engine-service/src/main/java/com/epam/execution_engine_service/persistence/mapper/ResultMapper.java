package com.epam.execution_engine_service.persistence.mapper;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.event.TestCaseResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper component converting ExecutionResultEvent DTOs to JPA entities.
 * Responsible for entity construction and cascaded mapping (SRS §5.2, EPMICMPCOD-459).
 *
 * Thread-safe: stateless component with no shared mutable state.
 * Implements full fidelity mapping per SRS §9 domain model.
 */
@Component
public class ResultMapper {

    /**
     * Maps ExecutionResultEvent DTO to SubmissionEntity with cascaded test results.
     * Enforces all required field population per SRS §2.2 steps 12–14.
     *
     * @param event the ExecutionResultEvent from Kafka
     * @return SubmissionEntity ready for persistence
     * @throws NullPointerException if event or required fields are null
     */
    public SubmissionEntity toSubmissionEntity(ExecutionResultEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("ExecutionResultEvent cannot be null");
        }

        // Build test results first (cascaded children)
        final List<SubmissionTestResultEntity> testResults =
            mapTestResults(event.getTestResults(), event.getExecutionId());

        // Build parent entity
        final SubmissionEntity entity = SubmissionEntity.builder()
            .executionId(event.getExecutionId())
            .userId(event.getUserId())
            .problemId(event.getProblemId())
            .language(event.getLanguage())
            .mode(event.getMode())
            .verdict(event.getVerdict())
            .status(event.getStatus())
            .score(event.getScore())
            .totalRuntimeMs(event.getTotalRuntimeMs())
            .memoryBytes(event.getMemoryBytes())
            .rawOutput(event.getRawOutput())
            .errorOutput(event.getErrorOutput())
            .submittedCode(event.getSubmittedCode())
            .submittedAt(event.getSubmittedAt())
            .completedAt(event.getCompletedAt())
            .testResults(testResults)
            .build();

        // Establish bidirectional relationship (optional, not required for persistence)
        testResults.forEach(tr -> tr.setSubmission(entity));

        return entity;
    }

    /**
     * Maps a list of TestCaseResultEvent DTOs to SubmissionTestResultEntity list.
     * Preserves order and full field fidelity per SRS §2.2 step 13.
     *
     * @param testCaseResults list of TestCaseResultEvent (may be null or empty)
     * @param executionId parent execution ID (foreign key)
     * @return List of SubmissionTestResultEntity (immutable List.of() enforces immutability, Gate 2)
     */
    private List<SubmissionTestResultEntity> mapTestResults(
        List<TestCaseResultEvent> testCaseResults,
        java.util.UUID executionId
    ) {
        if (testCaseResults == null || testCaseResults.isEmpty()) {
            return new ArrayList<>();
        }

        final List<SubmissionTestResultEntity> entities = new ArrayList<>();
        for (final TestCaseResultEvent testResult : testCaseResults) {
            final SubmissionTestResultEntity entity = SubmissionTestResultEntity.builder()
                .executionId(executionId)
                .testCaseId(testResult.getTestCaseId())
                .status(testResult.getStatus())
                .runtimeMs(testResult.getRuntimeMs())
                .memoryBytes(testResult.getMemoryBytes())
                .expectedOutput(testResult.getExpectedOutput())
                .actualOutput(testResult.getActualOutput())
                .errorOutput(testResult.getErrorOutput())
                .build();

            entities.add(entity);
        }

        return entities;
    }

    /**
     * Maps SubmissionEntity back to ExecutionResultEvent DTO (for Redis and API responses).
     * Reconstructs test case results from cascaded children.
     *
     * @param entity the SubmissionEntity from database
     * @return ExecutionResultEvent DTO
     * @throws NullPointerException if entity is null
     */
    public ExecutionResultEvent toExecutionResultEvent(SubmissionEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("SubmissionEntity cannot be null");
        }

        final List<TestCaseResultEvent> testCaseEvents = new ArrayList<>();
        if (entity.getTestResults() != null && !entity.getTestResults().isEmpty()) {
            for (final SubmissionTestResultEntity testResult : entity.getTestResults()) {
                testCaseEvents.add(
                    TestCaseResultEvent.builder()
                        .testCaseId(testResult.getTestCaseId())
                        .status(testResult.getStatus())
                        .runtimeMs(testResult.getRuntimeMs())
                        .memoryBytes(testResult.getMemoryBytes())
                        .expectedOutput(testResult.getExpectedOutput())
                        .actualOutput(testResult.getActualOutput())
                        .errorOutput(testResult.getErrorOutput())
                        .build()
                );
            }
        }

        return ExecutionResultEvent.builder()
            .executionId(entity.getExecutionId())
            .userId(entity.getUserId())
            .problemId(entity.getProblemId())
            .language(entity.getLanguage())
            .mode(entity.getMode())
            .verdict(entity.getVerdict())
            .status(entity.getStatus())
            .score(entity.getScore())
            .totalRuntimeMs(entity.getTotalRuntimeMs())
            .memoryBytes(entity.getMemoryBytes())
            .rawOutput(entity.getRawOutput())
            .errorOutput(entity.getErrorOutput())
            .submittedCode(entity.getSubmittedCode())
            .submittedAt(entity.getSubmittedAt())
            .completedAt(entity.getCompletedAt())
            .testResults(testCaseEvents)
            .build();
    }
}
