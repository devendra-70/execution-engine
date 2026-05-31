package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.SubmissionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, UUID> {

    /**
     * Returns the most recent submissions ordered by submittedAt descending.
     * Use with {@code PageRequest.of(0, limit, Sort.by("submittedAt").descending())} to avoid
     * loading the full table (replaces findAll() + Java-side sort + limit).
     */
    List<SubmissionEntity> findBy(Pageable pageable);
}

