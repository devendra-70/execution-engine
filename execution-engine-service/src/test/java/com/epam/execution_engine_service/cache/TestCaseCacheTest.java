package com.epam.execution_engine_service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for TestCaseCache (SRS §4.2)
 * 
 * Tests Caffeine cache functionality for problem test cases.
 * Covers cache hits, misses, storage, invalidation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseCacheTest {
    
    @Mock
    private Cache<String, List<?>> testCaseCache;
    
    @InjectMocks
    private TestCaseCache testCaseCacheService;
    
    private String problemId;
    private List<?> testCases;
    
    @BeforeEach
    void setUp() {
        problemId = "two-sum";
        testCases = Arrays.asList(
                new Object[]{1, 2}, // [input, expected output]
                new Object[]{3, 4}
        );
    }
    
    @Test
    void testGetTestCases_CacheHit_ReturnsTestCases() {
        List<?> mockReturn = Arrays.asList(
                new Object[]{1, 2},
                new Object[]{3, 4}
        );
        when(testCaseCache.getIfPresent(problemId)).thenReturn((List) mockReturn);
        
        List<?> result = testCaseCacheService.getTestCases(problemId);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(testCaseCache, times(1)).getIfPresent(problemId);
    }
    
    @Test
    void testGetTestCases_CacheMiss_ReturnsNull() {
        when(testCaseCache.getIfPresent(problemId)).thenReturn(null);
        
        List<?> result = testCaseCacheService.getTestCases(problemId);
        
        assertNull(result);
        verify(testCaseCache, times(1)).getIfPresent(problemId);
    }
    
    @Test
    void testGetTestCases_Exception_ReturnsNull() {
        when(testCaseCache.getIfPresent(anyString())).thenThrow(new RuntimeException("Cache error"));
        
        List<?> result = testCaseCacheService.getTestCases(problemId);
        
        assertNull(result);
    }
    
    @Test
    void testPutTestCases_Success_StoresInCache() {
        testCaseCacheService.putTestCases(problemId, testCases);
        
        verify(testCaseCache, times(1)).put(problemId, testCases);
    }
    
    @Test
    void testPutTestCases_Exception_HandledGracefully() {
        doThrow(new RuntimeException("Cache error")).when(testCaseCache).put(anyString(), any());
        
        assertDoesNotThrow(() -> testCaseCacheService.putTestCases(problemId, testCases));
    }
    
    @Test
    void testInvalidateCache_Success_InvalidatesEntry() {
        testCaseCacheService.invalidateCache(problemId);
        
        verify(testCaseCache, times(1)).invalidate(problemId);
    }
    
    @Test
    void testInvalidateCache_Exception_HandledGracefully() {
        doThrow(new RuntimeException("Invalidation error")).when(testCaseCache).invalidate(anyString());
        
        assertDoesNotThrow(() -> testCaseCacheService.invalidateCache(problemId));
    }
    
    @Test
    void testClearAll_Success_ClearsEntireCache() {
        testCaseCacheService.clearAll();
        
        verify(testCaseCache, times(1)).invalidateAll();
    }
    
    @Test
    void testClearAll_Exception_HandledGracefully() {
        doThrow(new RuntimeException("Clear error")).when(testCaseCache).invalidateAll();
        
        assertDoesNotThrow(() -> testCaseCacheService.clearAll());
    }
}
