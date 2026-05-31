package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.epam.execution_engine_service.domain.Verdict;

import java.util.Objects;

/**
 * JPA best practice: @Data replaced with @Getter/@Setter.
 * @ToString excludes the parent submission to avoid circular reference + lazy loading issues.
 * equals/hashCode based on @Id only.
 */
@Entity
@Table(name = "submission_test_results")
@Getter
@Setter
@ToString(exclude = "submission")
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

    @Column(nullable = false) private Long testCaseId;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Column(columnDefinition = "TEXT") private String actualOutput;
    @Column(columnDefinition = "TEXT") private String expectedOutput;
    private long runtimeMs;
    private long memoryBytes;
    @Column(columnDefinition = "TEXT") private String errorMessage;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubmissionTestResultEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
