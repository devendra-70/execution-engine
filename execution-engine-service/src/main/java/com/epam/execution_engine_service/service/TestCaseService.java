package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.TestCaseDto;
import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import com.epam.execution_engine_service.persistence.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TestCaseService — Test case caching and retrieval (SRS §4.2)
 * 
 * Provides test cases with Caffeine caching:
 * - Key: problemId
 * - Value: List<TestCaseDto>
 * - TTL: configurable via app.cache.testcase-ttl-minutes
 * 
 * On cache miss, queries database and caches result.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;

    /**
     * Get test cases for a problem (cached)
     * 
     * @param problemId Problem identifier
     * @return List of test cases (may be cached by Caffeine)
     */
    @Cacheable(value = "testCaseCache", key = "#problemId")
    public List<TestCaseDto> getTestCases(Long problemId) {
        log.debug("Fetching test cases for problemId: {}", problemId);
        
        List<TestCaseEntity> entities = testCaseRepository.findByProblemId(problemId);
        
        List<TestCaseDto> dtos = entities.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
        
        log.info("Retrieved {} test cases for problemId: {}", dtos.size(), problemId);
        return dtos;
    }

    /**
     * Map TestCaseEntity to TestCaseDto
     */
    private TestCaseDto mapToDto(TestCaseEntity entity) {
        return TestCaseDto.builder()
                .id(entity.getId())
                .problemId(entity.getProblemId())
                .input(entity.getInput())
                .expectedOutput(entity.getExpectedOutput())
                .timeoutMs(entity.getTimeoutMs())
                .build();
    }

}
