package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.SubmissionTestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for SubmissionTestResultEntity persistence.
 * Provides CRUD and custom query operations for test case execution results.
 *
 * Thread-safe by design (Spring Data manages thread pooling).
 * All queries execute within transactional boundaries defined in service layer.
 */
@Repository
public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResultEntity, Long> {

    /**
     * Find all test results for a specific execution (submission).
     * Used to fetch complete test case results after persistence.
     *
     * @param executionId the UUID of the execution (idempotency key)
     * @return List of all test results for this execution (may be empty)
     */
    @Query("SELECT tr FROM SubmissionTestResultEntity tr WHERE tr.executionId = :executionId ORDER BY tr.id ASC")
    List<SubmissionTestResultEntity> findByExecutionId(@Param("executionId") UUID executionId);

    /**
     * Count test results for a specific execution.
     * Used for validation and verification.
     *
     * @param executionId the UUID of the execution
     * @return count of test results for this execution
     */
    @Query("SELECT COUNT(tr) FROM SubmissionTestResultEntity tr WHERE tr.executionId = :executionId")
    long countByExecutionId(@Param("executionId") UUID executionId);
}
