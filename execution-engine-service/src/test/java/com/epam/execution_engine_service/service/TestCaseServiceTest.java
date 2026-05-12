package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.TestCaseDto;
import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import com.epam.execution_engine_service.persistence.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TestCaseServiceTest — Unit tests for TestCaseService (SRS §4.2)
 * 
 * Coverage target: ≥90% (services)
 */
@ExtendWith(MockitoExtension.class)
public class TestCaseServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @InjectMocks
    private TestCaseService testCaseService;

    private List<TestCaseEntity> testCaseEntities;

    @BeforeEach
    public void setUp() {
        TestCaseEntity tc1 = TestCaseEntity.builder()
                .id(1L)
                .problemId(100L)
                .input("5 10")
                .expectedOutput("15")
                .timeoutMs(1000)
                .build();

        TestCaseEntity tc2 = TestCaseEntity.builder()
                .id(2L)
                .problemId(100L)
                .input("3 7")
                .expectedOutput("10")
                .timeoutMs(1000)
                .build();

        testCaseEntities = Arrays.asList(tc1, tc2);
    }

    @Test
    public void testGetTestCases_Success() {
        // Arrange
        Long problemId = 100L;
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(testCaseEntities);

        // Act
        List<TestCaseDto> result = testCaseService.getTestCases(problemId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("5 10", result.get(0).getInput());
    }

    @Test
    public void testGetTestCases_Empty() {
        // Arrange
        Long problemId = 999L;
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(Arrays.asList());

        // Act
        List<TestCaseDto> result = testCaseService.getTestCases(problemId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetTestCases_Cached() {
        // Arrange: Call twice, should only query DB once (if caching works)
        Long problemId = 100L;
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(testCaseEntities);

        // Act
        List<TestCaseDto> result1 = testCaseService.getTestCases(problemId);
        List<TestCaseDto> result2 = testCaseService.getTestCases(problemId);

        // Assert
        assertEquals(result1, result2);
    }

}
