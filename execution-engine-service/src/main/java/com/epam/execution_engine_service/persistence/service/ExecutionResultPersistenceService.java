package com.epam.execution_engine_service.persistence.service;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import com.epam.execution_engine_service.persistence.mapper.ResultMapper;
import com.epam.execution_engine_service.persistence.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for persisting execution results to PostgreSQL.
 * Implements SRS §5.2 transactional guarantees and Kafka offset acknowledgment semantics.
 *
 * Thread-safe: Spring manages transactional isolation via thread-local datasource pooling.
 * All database operations enforce ACID properties per SRS §5.1 (READ_COMMITTED isolation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionResultPersistenceService {

    private final SubmissionRepository submissionRepository;
    private final ResultMapper resultMapper;

    /**
     * Persists an execution result event to database with full transactional guarantee.
     * Called from Kafka consumer after broker message received (SRS §2.2 step 12).
     *
     * Transaction boundary: REQUIRED propagation (joins parent or creates new).
     * Isolation: READ_COMMITTED per SRS §5.1 (prevents dirty reads).
     * Hibernate batch size: 50 records with order_inserts=true (SRS §12).
     *
     * On success: Transaction committed; Kafka offset may be acknowledged (SRS §5.2).
     * On failure: Transaction rolled back; Kafka offset NOT acknowledged; message redelivered.
     *
     * @param event ExecutionResultEvent from Kafka topic "execution-tasks"
     * @return Persisted SubmissionEntity with database-assigned ID
     * @throws DataIntegrityViolationException if unique constraint violated (e.g., duplicate executionId)
     * @throws PersistenceException if database connection fails
     * @throws NullPointerException if event or required fields are null
     * @see org.springframework.dao.DataIntegrityViolationException
     * @see jakarta.persistence.PersistenceException
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    public SubmissionEntity persistExecutionResult(ExecutionResultEvent event) {
        if (event == null || event.getExecutionId() == null) {
            throw new IllegalArgumentException("ExecutionResultEvent and executionId cannot be null");
        }

        log.info("Persisting execution result for executionId: {}", event.getExecutionId());

        try {
            // Map DTO to entity (handles cascaded test results)
            final SubmissionEntity entity = resultMapper.toSubmissionEntity(event);

            // Save to database (Hibernate batch insert, order_inserts=true per SRS §12)
            final SubmissionEntity saved = submissionRepository.save(entity);

            log.info("Successfully persisted execution result with database ID: {}, executionId: {}",
                saved.getId(), saved.getExecutionId());

            return saved;
        } catch (final Exception e) {
            log.error("Failed to persist execution result for executionId: {}", event.getExecutionId(), e);
            throw e;  // Re-throw to trigger transaction rollback and prevent Kafka ACK
        }
    }

    /**
     * Finds a submission by its idempotency key (executionId).
     * Used for duplicate detection and result retrieval (SRS §5.2).
     *
     * @param executionId UUID of the execution
     * @return Optional containing the submission if found
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        readOnly = true
    )
    public Optional<SubmissionEntity> findByExecutionId(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId cannot be null");
        }

        return submissionRepository.findByExecutionId(executionId);
    }

    /**
     * Counts submissions for a specific user (analytics, SRS §2.2).
     *
     * @param userId user identifier
     * @return count of submissions by this user
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        readOnly = true
    )
    public long countByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }

        return submissionRepository.countByUserId(userId);
    }

    /**
     * Counts submissions for a specific problem (analytics).
     *
     * @param problemId problem identifier
     * @return count of submissions for this problem
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        readOnly = true
    )
    public long countByProblemId(String problemId) {
        if (problemId == null || problemId.isBlank()) {
            throw new IllegalArgumentException("problemId cannot be null or blank");
        }

        return submissionRepository.countByProblemId(problemId);
    }
}
