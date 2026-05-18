package com.epam.execution_engine_service.orchestrator.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import com.epam.execution_engine_service.persistence.repository.TestCaseRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseCacheService {

    private final TestCaseRepository testCaseRepository;

    /** Returns ALL test cases for a problem (used by submit mode). */
    @Cacheable(cacheNames = "testCases", key = "'all:' + #p0")
    public List<TestCase> getTestCases(Long problemId) {
        log.info("Cache miss (all) for problemId {}. Loading from DB.", problemId);
        List<TestCaseEntity> entities = testCaseRepository.findByProblemId(problemId);
        return toTestCases(entities);
    }

    /** Returns only visible (non-hidden) test cases (used by run mode). */
    @Cacheable(cacheNames = "testCases", key = "'visible:' + #p0")
    public List<TestCase> getVisibleTestCases(Long problemId) {
        log.info("Cache miss (visible) for problemId {}. Loading from DB.", problemId);
        List<TestCaseEntity> entities = testCaseRepository.findByProblemIdAndIsHidden(problemId, false);
        return toTestCases(entities);
    }

    private List<TestCase> toTestCases(List<TestCaseEntity> entities) {
        return entities.stream()
                .map(e -> TestCase.builder()
                        .id(e.getId())
                        .problemId(e.getProblemId())
                        .input(e.getInput())
                        .expectedOutput(e.getExpectedOutput())
                        .timeoutMs(e.getTimeoutMs())
                        .build())
                .toList();
    }
}

