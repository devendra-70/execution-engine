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

    @Cacheable(cacheNames = "testCases", key = "#p0")
    public List<TestCase> getTestCases(Long problemId) {
        log.info("Cache miss for problemId {}. Loading from DB.", problemId);
        List<TestCaseEntity> entities = testCaseRepository.findByProblemId(problemId);
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

