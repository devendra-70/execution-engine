package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA Entity representing a single test case result within a submission.
 * Maps to the "submission_test_results" table per SRS §2.2 steps 12–14.
 *
 * Owned by SubmissionEntity via ManyToOne relationship with cascade delete.
 * Immutable fields enforced via final declarations.
 */
@Entity
@Table(
    name = "submission_test_results",
    indexes = {
        @Index(name = "idx_submission_test_results_execution", columnList = "execution_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "submission")
@EqualsAndHashCode(exclude = "submission")
public class SubmissionTestResultEntity {

    /**
     * Database auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Foreign key to parent SubmissionEntity.executionId.
     * Indexed for join performance (SRS §9).
     * H2 compatibility: stored as VARCHAR(36) for UUID string representation.
     */
    @Column(name = "execution_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID executionId;

    /**
     * Test case identifier (from TestCaseResultEvent.testCaseId).
     */
    @Column(name = "test_case_id", nullable = false, length = 128)
    private String testCaseId;

    /**
     * Test case execution status: PASSED, FAILED, TIMEOUT, etc.
     * (from TestCaseResultEvent.status).
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * Runtime for this test case in milliseconds (from TestCaseResultEvent.runtimeMs).
     */
    @Column(name = "runtime_ms")
    private Long runtimeMs;

    /**
     * Memory used for this test case in bytes (from TestCaseResultEvent.memoryBytes).
     */
    @Column(name = "memory_bytes")
    private Long memoryBytes;

    /**
     * Expected output for this test case (from TestCaseResultEvent.expectedOutput).
     */
    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    /**
     * Actual output produced by code for this test case (from TestCaseResultEvent.actualOutput).
     */
    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    /**
     * Error message or diagnostics for this test case (from TestCaseResultEvent.errorOutput).
     */
    @Column(name = "error_output", columnDefinition = "TEXT")
    private String errorOutput;

    /**
     * Timestamp when record was created in database.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp when record was last updated.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Many-to-One relationship to parent SubmissionEntity.
     * Bidirectional mapping: synchronized with SubmissionEntity.testResults via cascading operations.
     * Lazy fetch by default; loaded only when accessed.
     * Inverse side of the relationship (parent controls updates via mappedBy).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "execution_id",
        referencedColumnName = "execution_id",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_submission_test_results_execution")
    )
    private SubmissionEntity submission;

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
