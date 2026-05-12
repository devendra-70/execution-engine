package com.epam.execution_engine_service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Test Case Cache Service (SRS §4.2)
 * 
 * Wrapper around Caffeine cache for test cases.
 * Eliminates Redis network overhead during execution.
 * 
 * Key: problemId
 * Value: List<TestCase>
 * TTL: Configurable (app.cache.testcase-ttl-minutes)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestCaseCache {
    
    private final Cache<String, List<?>> testCaseCache;
    
    /**
     * Get test cases for a problem from Caffeine cache
     * 
     * @param problemId The problem identifier
     * @return List<TestCase> if found, null if not in cache (needs fallback to DB)
     */
    public List<?> getTestCases(String problemId) {
        log.debug("Attempting to retrieve test cases from Caffeine cache: problemId={}", problemId);
        
        try {
            List<?> testCases = testCaseCache.getIfPresent(problemId);
            
            if (testCases != null) {
                log.debug("Cache HIT: Found {} test cases for problemId={}", 
                        testCases.size(), problemId);
                return testCases;
            } else {
                log.debug("Cache MISS: No test cases in cache for problemId={}", problemId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error retrieving test cases from cache: problemId={}, error={}",
                    problemId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Store test cases in Caffeine cache
     * Called when fetching from database (cache miss)
     * 
     * @param problemId The problem identifier
     * @param testCases List of test cases to cache
     */
    public void putTestCases(String problemId, List<?> testCases) {
        log.debug("Storing {} test cases in Caffeine cache for problemId={}", 
                testCases.size(), problemId);
        
        try {
            testCaseCache.put(problemId, testCases);
            log.debug("Test cases cached successfully: problemId={}", problemId);
        } catch (Exception e) {
            log.error("Error caching test cases: problemId={}, error={}",
                    problemId, e.getMessage(), e);
        }
    }
    
    /**
     * Manually invalidate cache entry
     * Used when test cases are updated
     * 
     * @param problemId The problem identifier
     */
    public void invalidateCache(String problemId) {
        log.debug("Invalidating cache entry: problemId={}", problemId);
        try {
            testCaseCache.invalidate(problemId);
            log.debug("Cache entry invalidated: problemId={}", problemId);
        } catch (Exception e) {
            log.error("Error invalidating cache: problemId={}, error={}",
                    problemId, e.getMessage(), e);
        }
    }
    
    /**
     * Clear entire cache
     * Use sparingly (e.g., maintenance, testing)
     */
    public void clearAll() {
        log.warn("Clearing entire test case cache");
        try {
            testCaseCache.invalidateAll();
            log.warn("Cache cleared");
        } catch (Exception e) {
            log.error("Error clearing cache: {}", e.getMessage(), e);
        }
    }
}
