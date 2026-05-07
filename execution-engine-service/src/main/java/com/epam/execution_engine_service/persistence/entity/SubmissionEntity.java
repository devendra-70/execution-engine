package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity representing a code submission execution result.
 * Maps to the "submissions" table per SRS §2.2 steps 12–14.
 *
 * Immutable fields enforced via final declarations for thread safety.
 * Cascade persist/delete to SubmissionTestResultEntity for atomic writes.
 */
@Entity
@Table(
    name = "submissions",
    indexes = {
        @Index(name = "idx_submissions_user_created_at", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_submissions_problem_created_at", columnList = "problem_id, created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "testResults")
@EqualsAndHashCode(exclude = "testResults")
public class SubmissionEntity {

    /**
     * Database auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Idempotent idempotency key (UUID from ExecutionResultEvent.executionId).
     * Prevents duplicate submissions for the same execution.
     */
    @Column(name = "execution_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID executionId;

    /**
     * User identifier (from ExecutionResultEvent.userId).
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Problem identifier (from ExecutionResultEvent.problemId).
     */
    @Column(name = "problem_id", nullable = false, length = 128)
    private String problemId;

    /**
     * Programming language (from ExecutionResultEvent.language).
     */
    @Column(name = "language", nullable = false, length = 32)
    private String language;

    /**
     * Execution mode: RUN or SUBMIT (from ExecutionResultEvent.mode).
     */
    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    /**
     * Verdict: PASSED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR.
     * (from ExecutionResultEvent.verdict).
     */
    @Column(name = "verdict", nullable = false, length = 32)
    private String verdict;

    /**
     * Execution status: COMPLETED, FAILED, TIMEOUT (from ExecutionResultEvent.status).
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * Score (nullable, from ExecutionResultEvent.score).
     */
    @Column(name = "score")
    private Integer score;

    /**
     * Total runtime in milliseconds (from ExecutionResultEvent.totalRuntimeMs).
     */
    @Column(name = "total_runtime_ms", nullable = false)
    private Long totalRuntimeMs;

    /**
     * Memory usage in bytes (from ExecutionResultEvent.memoryBytes).
     */
    @Column(name = "memory_bytes", nullable = false)
    private Long memoryBytes;

    /**
     * Raw output from execution (from ExecutionResultEvent.rawOutput).
     */
    @Column(name = "raw_output", columnDefinition = "TEXT")
    private String rawOutput;

    /**
     * Error output or compilation diagnostics (from ExecutionResultEvent.errorOutput).
     */
    @Column(name = "error_output", columnDefinition = "TEXT")
    private String errorOutput;

    /**
     * Source code submitted by user (from ExecutionResultEvent.submittedCode).
     */
    @Column(name = "submitted_code", nullable = false, columnDefinition = "TEXT")
    private String submittedCode;

    /**
     * Timestamp when code was submitted (from ExecutionResultEvent.submittedAt).
     */
    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    /**
     * Timestamp when execution completed (from ExecutionResultEvent.completedAt).
     */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * Timestamp when record was created in database.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    /**
     * Timestamp when record was last updated.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * One-to-Many relationship to submission test results.
     * Cascade ALL for atomic writes (SRS §5.2).
     */
    @OneToMany(
        mappedBy = "submission",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    @Builder.Default
    private List<SubmissionTestResultEntity> testResults = new ArrayList<>();

    /**
     * JPA lifecycle hook: set createdAt on insert and updatedAt on insert/update.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    /**
     * JPA lifecycle hook: update updatedAt on every update.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
