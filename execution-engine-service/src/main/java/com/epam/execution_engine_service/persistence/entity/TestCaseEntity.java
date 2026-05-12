package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * TestCaseEntity — JPA entity for test cases (read-only in this story) (SRS §4.2)
 * 
 * Represents a single test case for a problem:
 * - problemId: Which problem this test case belongs to
 * - input: Input to feed to user's code
 * - expectedOutput: Expected output
 * - timeoutMs: Timeout in milliseconds
 * 
 * This entity is read-only; no modifications occur during this story.
 * Test cases are fetched from Caffeine cache (with DB fallback).
 */
@Entity
@Table(name = "test_case", indexes = {
    @Index(name = "idx_problem_id", columnList = "problem_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Problem identifier (foreign key to problem table, external to this service)
     */
    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    /**
     * Input to feed to the user's code (TEXT for large inputs)
     */
    @Column(name = "input", nullable = false, columnDefinition = "TEXT")
    private String input;

    /**
     * Expected output for this test case (TEXT for large output)
     */
    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    /**
     * Timeout in milliseconds for this test case
     */
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

}
