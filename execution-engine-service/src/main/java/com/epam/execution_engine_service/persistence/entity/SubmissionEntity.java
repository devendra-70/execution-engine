package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.epam.execution_engine_service.domain.Verdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA best practice: @Data is replaced with explicit @Getter/@Setter/@ToString.
 * - @ToString excludes the lazy testResults collection to prevent unintended SQL on logging.
 * - equals/hashCode are based solely on the @Id to satisfy JPA identity semantics
 *   (two proxies for the same row must be equal, regardless of which fields are loaded).
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@ToString(exclude = "testResults")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long problemId;
    private String problemName;
    @Column(nullable = false) private String language;
    @Column(nullable = false) private String mode;
    @Column(columnDefinition = "TEXT", nullable = false) private String sourceCode;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    private int score;
    private long totalRuntimeMs;
    private long memoryBytes;

    @Column(nullable = false) private Instant submittedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubmissionTestResultEntity> testResults = new ArrayList<>();

    /** Id-based equality — required for correct JPA proxy behaviour. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubmissionEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
