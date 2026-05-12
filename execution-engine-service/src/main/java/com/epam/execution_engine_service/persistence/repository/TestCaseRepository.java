package com.epam.execution_engine_service.persistence.repository;

import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TestCaseRepository — Spring Data JPA repository for TestCaseEntity (SRS §4.2)
 * 
 * Read-only repository for fetching test cases by problem ID.
 * Results are cached by Caffeine to eliminate database query overhead.
 */
@Repository
public interface TestCaseRepository extends JpaRepository<TestCaseEntity, Long> {

    /**
     * Find all test cases for a given problem
     * @param problemId Problem identifier
     * @return List of test cases (may be cached by Caffeine)
     */
    List<TestCaseEntity> findByProblemId(Long problemId);

}
