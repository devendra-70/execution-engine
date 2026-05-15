package com.epam.execution_engine_service.orchestrator.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${app.cache.testcase-ttl-minutes:60}")
    private long testcaseTtlMinutes;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("testCases");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(testcaseTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats());
        return cacheManager;
    }
}

