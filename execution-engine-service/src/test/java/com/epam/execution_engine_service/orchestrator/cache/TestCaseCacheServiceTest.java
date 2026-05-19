package com.epam.execution_engine_service.orchestrator.cache;

import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.persistence.entity.TestCaseEntity;
import com.epam.execution_engine_service.persistence.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseCacheServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @InjectMocks
    private TestCaseCacheService testCaseCacheService;

    @Test
    void getTestCases_singleEntity_mappedCorrectly() {
        Long problemId = 42L;
        TestCaseEntity entity = TestCaseEntity.builder()
                .id(1L)
                .problemId(problemId)
                .input("1 2")
                .expectedOutput("3")
                .timeoutMs(1000)
                .build();
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertEquals(1, result.size());
        TestCase tc = result.get(0);
        assertEquals(1L, tc.getId());
        assertEquals(problemId, tc.getProblemId());
        assertEquals("1 2", tc.getInput());
        assertEquals("3", tc.getExpectedOutput());
        assertEquals(1000, tc.getTimeoutMs());
    }

    @Test
    void getTestCases_noTestCases_returnsEmptyList() {
        Long problemId = 99L;
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(Collections.emptyList());

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertTrue(result.isEmpty());
        verify(testCaseRepository, times(1)).findByProblemId(problemId);
    }

    @Test
    void getTestCases_multipleEntities_allMappedInOrder() {
        Long problemId = 10L;
        List<TestCaseEntity> entities = List.of(
                TestCaseEntity.builder().id(1L).problemId(problemId).input("a").expectedOutput("b").timeoutMs(500).build(),
                TestCaseEntity.builder().id(2L).problemId(problemId).input("c").expectedOutput("d").timeoutMs(600).build(),
                TestCaseEntity.builder().id(3L).problemId(problemId).input("e").expectedOutput("f").timeoutMs(700).build()
        );
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(entities);

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("a", result.get(0).getInput());
        assertEquals(2L, result.get(1).getId());
        assertEquals("c", result.get(1).getInput());
        assertEquals("d", result.get(1).getExpectedOutput());
        assertEquals(3L, result.get(2).getId());
        assertEquals("f", result.get(2).getExpectedOutput());
    }

    @Test
    void getTestCases_differentProblemIds_repositoryCalledForEach() {
        when(testCaseRepository.findByProblemId(1L)).thenReturn(List.of(
                TestCaseEntity.builder().id(1L).problemId(1L).input("x").expectedOutput("y").timeoutMs(200).build()
        ));
        when(testCaseRepository.findByProblemId(2L)).thenReturn(Collections.emptyList());

        List<TestCase> result1 = testCaseCacheService.getTestCases(1L);
        List<TestCase> result2 = testCaseCacheService.getTestCases(2L);

        assertEquals(1, result1.size());
        assertEquals(1L, result1.get(0).getId());
        assertTrue(result2.isEmpty());
        verify(testCaseRepository).findByProblemId(1L);
        verify(testCaseRepository).findByProblemId(2L);
    }

    @Test
    void getTestCases_entityWithNullInputAndOutput_mappedWithNulls() {
        Long problemId = 5L;
        TestCaseEntity entity = TestCaseEntity.builder().id(10L).problemId(problemId).build();
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertEquals(1, result.size());
        assertNull(result.get(0).getInput());
        assertNull(result.get(0).getExpectedOutput());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void getTestCases_zeroTimeoutMs_mappedAsZero() {
        Long problemId = 7L;
        TestCaseEntity entity = TestCaseEntity.builder()
                .id(20L).problemId(problemId).input("in").expectedOutput("out").timeoutMs(0).build();
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertEquals(0, result.get(0).getTimeoutMs());
    }

    @Test
    void getTestCases_problemIdMappedCorrectly() {
        Long problemId = 123L;
        TestCaseEntity entity = TestCaseEntity.builder()
                .id(5L).problemId(problemId).input("inp").expectedOutput("out").timeoutMs(300).build();
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertEquals(problemId, result.get(0).getProblemId());
    }

    @Test
    void getTestCases_repositoryCalledOnce_perInvocation() {
        Long problemId = 88L;
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(Collections.emptyList());

        testCaseCacheService.getTestCases(problemId);
        testCaseCacheService.getTestCases(problemId);

        // Without Spring cache proxy, repository is called each time
        verify(testCaseRepository, times(2)).findByProblemId(problemId);
    }

    // ── getVisibleTestCases ───────────────────────────────────────────

    @Test
    void getVisibleTestCases_singleVisibleEntity_mappedCorrectly() {
        Long problemId = 42L;
        TestCaseEntity entity = TestCaseEntity.builder()
                .id(1L)
                .problemId(problemId)
                .input("1 2")
                .expectedOutput("3")
                .timeoutMs(1000)
                .isHidden(false)
                .build();
        when(testCaseRepository.findByProblemIdAndIsHidden(problemId, false)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getVisibleTestCases(problemId);

        assertEquals(1, result.size());
        TestCase tc = result.get(0);
        assertEquals(1L, tc.getId());
        assertEquals(problemId, tc.getProblemId());
        assertEquals("1 2", tc.getInput());
        assertEquals("3", tc.getExpectedOutput());
        assertEquals(1000, tc.getTimeoutMs());
        assertFalse(tc.isHidden());
        verify(testCaseRepository).findByProblemIdAndIsHidden(problemId, false);
    }

    @Test
    void getVisibleTestCases_noVisibleEntities_returnsEmptyList() {
        Long problemId = 99L;
        when(testCaseRepository.findByProblemIdAndIsHidden(problemId, false))
                .thenReturn(Collections.emptyList());

        List<TestCase> result = testCaseCacheService.getVisibleTestCases(problemId);

        assertTrue(result.isEmpty());
        verify(testCaseRepository).findByProblemIdAndIsHidden(problemId, false);
    }

    @Test
    void getVisibleTestCases_multipleEntities_allMappedInOrder() {
        Long problemId = 10L;
        List<TestCaseEntity> entities = List.of(
                TestCaseEntity.builder().id(1L).problemId(problemId).input("a").expectedOutput("b").timeoutMs(500).isHidden(false).build(),
                TestCaseEntity.builder().id(2L).problemId(problemId).input("c").expectedOutput("d").timeoutMs(600).isHidden(false).build()
        );
        when(testCaseRepository.findByProblemIdAndIsHidden(problemId, false)).thenReturn(entities);

        List<TestCase> result = testCaseCacheService.getVisibleTestCases(problemId);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }

    // ── isHidden field mapping ────────────────────────────────────────

    @Test
    void getTestCases_hiddenEntity_isHiddenMappedTrue() {
        Long problemId = 55L;
        TestCaseEntity entity = TestCaseEntity.builder()
                .id(7L).problemId(problemId).input("x").expectedOutput("y").timeoutMs(300).isHidden(true)
                .build();
        when(testCaseRepository.findByProblemId(problemId)).thenReturn(List.of(entity));

        List<TestCase> result = testCaseCacheService.getTestCases(problemId);

        assertTrue(result.get(0).isHidden());
    }
}
