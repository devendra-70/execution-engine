package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for SubmissionEntity persistence.
 * Provides CRUD and custom query operations for code submission results.
 *
 * Thread-safe by design (Spring Data manages thread pooling).
 * All queries execute within transactional boundaries defined in service layer.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    /**
     * Find a submission by its idempotency key (executionId).
     * Used to check for duplicate executions and retrieve saved results.
     *
     * @param executionId UUID of the execution
     * @return Optional containing the submission if found
     */
    Optional<SubmissionEntity> findByExecutionId(UUID executionId);

    /**
     * Count submissions for a specific user.
     * Used for user history queries and analytics (SRS §2.2).
     *
     * @param userId the user identifier
     * @return count of submissions by this user
     */
    @Query("SELECT COUNT(s) FROM SubmissionEntity s WHERE s.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    /**
     * Count submissions for a specific problem.
     * Used for problem popularity and analytics.
     *
     * @param problemId the problem identifier
     * @return count of submissions for this problem
     */
    @Query("SELECT COUNT(s) FROM SubmissionEntity s WHERE s.problemId = :problemId")
    long countByProblemId(@Param("problemId") String problemId);
}
