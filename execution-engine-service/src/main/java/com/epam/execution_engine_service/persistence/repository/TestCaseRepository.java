package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCaseEntity, Long> {
    List<TestCaseEntity> findByProblemId(Long problemId);
}

