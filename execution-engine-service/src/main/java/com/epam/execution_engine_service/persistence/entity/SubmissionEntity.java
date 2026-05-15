package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.epam.execution_engine_service.domain.Verdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long problemId;

    private String problemName;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String mode;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    private int score;
    private long totalRuntimeMs;
    private long memoryBytes;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant completedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubmissionTestResultEntity> testResults = new ArrayList<>();
}

