package com.epam.execution_engine_service.config;

import com.epam.execution_engine_service.dto.TestCaseDto;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CaffeineConfig — Local JVM caching for test cases (SRS §4.2, §8)
 * 
 * Configures Caffeine cache to eliminate Redis network overhead for test case lookups.
 * 
 * Key Pattern: problem:{problemId}
 * Value: List<TestCaseDto>
 * TTL: Configurable via app.cache.testcase-ttl-minutes
 * 
 * Strategy: 
 * - On first lookup, fetch from DB and cache locally
 * - On subsequent lookups (within TTL), serve from local cache
 * - Cache invalidation on TTL expiry triggers DB refresh
 * 
 * (SRS Section 4.2: "To eliminate Redis network overhead for test cases,
 *  the Orchestrator uses JVM-local Caffeine Caching")
 */
@Configuration
@EnableCaching
@Slf4j
public class CaffeineConfig {

    @Value("${app.cache.testcase-ttl-minutes:60}")
    private int testcaseTtlMinutes;

    @Value("${app.cache.testcase-max-size:1000}")
    private int testcaseMaxSize;

    /**
     * Configure Caffeine cache manager for test cases
     * 
     * @return CaffeineCacheManager with test case cache configured
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("testCases");
        Caffeine<Object, Object> caffeine = (Caffeine<Object, Object>) (Object) Caffeine.newBuilder()
                .maximumSize(testcaseMaxSize)
                .expireAfterWrite(testcaseTtlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .evictionListener((key, value, cause) -> {
                    log.debug("Cache eviction: key={}, cause={}", key, cause);
                });
        cacheManager.setCaffeine(caffeine);

        log.info("Caffeine cache configured: TTL={}min, MaxSize={}", testcaseTtlMinutes, testcaseMaxSize);
        return cacheManager;
    }

}
