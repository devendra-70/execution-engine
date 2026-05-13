package org.codeval.execution.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.codeval.execution.domain.Verdict;

@Entity
@Table(name = "submission_test_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionTestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private SubmissionEntity submission;

    @Column(nullable = false)
    private Long testCaseId;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Column(columnDefinition = "TEXT")
    private String actualOutput;

    @Column(columnDefinition = "TEXT")
    private String expectedOutput;

    private long runtimeMs;
    private long memoryBytes;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}

