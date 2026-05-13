package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.dto.TestCaseResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VerdictAggregatorTest — Unit tests for VerdictAggregator (SRS §11)
 * 
 * Coverage target: ≥100%
 */
@ExtendWith(MockitoExtension.class)
public class VerdictAggregatorTest {

    @InjectMocks
    private VerdictAggregator verdictAggregator;

    @Test
    public void testAggregateVerdict_CompileError() {
        // Arrange
        List<TestCaseResultDto> results = Arrays.asList(
                TestCaseResultDto.builder().testCaseId(1L).status("COMPILE_ERROR").build()
        );

        // Act
        String verdict = verdictAggregator.aggregateVerdict(results);

        // Assert
        assertEquals("COMPILE_ERROR", verdict);
    }

    @Test
    public void testAggregateVerdict_RuntimeError() {
        // Arrange
        List<TestCaseResultDto> results = Arrays.asList(
                TestCaseResultDto.builder().testCaseId(1L).status("PASS").build(),
                TestCaseResultDto.builder().testCaseId(2L).status("RUNTIME_ERROR").build()
        );

        // Act
        String verdict = verdictAggregator.aggregateVerdict(results);

        // Assert
        assertEquals("RUNTIME_ERROR", verdict);
    }

    @Test
    public void testAggregateVerdict_Passed() {
        // Arrange
        List<TestCaseResultDto> results = Arrays.asList(
                TestCaseResultDto.builder().testCaseId(1L).status("PASS").build(),
                TestCaseResultDto.builder().testCaseId(2L).status("PASS").build()
        );

        // Act
        String verdict = verdictAggregator.aggregateVerdict(results);

        // Assert
        assertEquals("PASSED", verdict);
    }

    @Test
    public void testAggregateVerdict_PartialSuccess() {
        // Arrange
        List<TestCaseResultDto> results = Arrays.asList(
                TestCaseResultDto.builder().testCaseId(1L).status("PASS").build(),
                TestCaseResultDto.builder().testCaseId(2L).status("FAIL").build()
        );

        // Act
        String verdict = verdictAggregator.aggregateVerdict(results);

        // Assert
        assertEquals("PARTIAL_SUCCESS", verdict);
    }

    @Test
    public void testCalculateScore() {
        // Act
        Double score1 = verdictAggregator.calculateScore(5, 10);
        Double score2 = verdictAggregator.calculateScore(0, 10);
        Double score3 = verdictAggregator.calculateScore(10, 10);

        // Assert
        assertEquals(50.0, score1);
        assertEquals(0.0, score2);
        assertEquals(100.0, score3);
    }

}
