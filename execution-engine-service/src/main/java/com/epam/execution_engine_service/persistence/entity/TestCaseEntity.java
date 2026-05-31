package com.epam.execution_engine_service.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

/**
 * JPA best practice: @Data replaced with @Getter/@Setter.
 * equals/hashCode based on @Id only.
 */
@Entity
@Table(name = "test_cases")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long problemId;
    @Column(columnDefinition = "TEXT", nullable = false) private String input;
    @Column(columnDefinition = "TEXT", nullable = false) private String expectedOutput;
    private int timeoutMs;
    private boolean isHidden;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
