package com.epam.execution_engine_service.persistence.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.epam.execution_engine_service.dto.TestCaseDto;
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
 * CaffeineCacheConfig — Caffeine local caching configuration (SRS §4.2)
 * 
 * Configures in-JVM cache for test cases:
 * - Key: problemId (String)
 * - Value: List<?>
 * - TTL: configurable via app.cache.testcase-ttl-minutes
 * - Max size: configurable via app.cache.testcase-max-size
 * 
 * This eliminates Redis network overhead for test case fetches.
 */
@Configuration
@EnableCaching
@Slf4j
public class CaffeineCacheConfig {

    @Value("${app.cache.testcase-ttl-minutes:60}")
    private int ttlMinutes;

    @Value("${app.cache.testcase-max-size:10000}")
    private long maxSize;

    /**
     * Create Caffeine cache bean for direct injection into services
     * @return Cache<String, List<?>> with configured TTL and max size
     */
    @Bean
    public Cache<String, List<?>> testCaseCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats()
                .build();
    }

    /**
     * Create Caffeine cache manager for @Cacheable annotations
     * @return CacheManager with testCaseCache configured
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("testCaseCache");
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats());

        return cacheManager;
    }

}
