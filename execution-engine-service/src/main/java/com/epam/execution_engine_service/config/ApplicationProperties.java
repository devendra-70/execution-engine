package com.epam.execution_engine_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application configuration properties loaded from application.yml
 * Prefix: app
 * 
 * Maps all custom application properties as per SRS Section 12.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class ApplicationProperties {
    
    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();
    private Redis redis = new Redis();
    private Kafka kafka = new Kafka();
    private Execution execution = new Execution();
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Jwt {
        private String secretKey;
        private long expirationMs;
    }
    
    @Getter
    @Setter
    public static class RateLimit {
        private int requestsPerMinute;
        private int windowSeconds;
    }
    
    @Getter
    @Setter
    public static class Redis {
        private int statusTtlSeconds;
        private RateLimitConfig rateLimit = new RateLimitConfig();
    }

    @Getter
    @Setter
    public static class RateLimitConfig {
        private int requestsPerMinute;
        private String pubsubChannel;
    }
    
    @Getter
    @Setter
    public static class Kafka {
        private String topic;
        private int partitions;
        private int concurrency;
        private int batchSize;
        private int retentionHours;
    }
    
    @Getter
    @Setter
    public static class Execution {
        private int orchestrationThreads;
        private int timeoutMs;
        private Pool pool = new Pool();
        private Sandbox sandbox = new Sandbox();
    }

    @Getter
    @Setter
    public static class Pool {
        private int warmMinSize;
        private int idleTtlSeconds;
    }

    @Getter
    @Setter
    public static class Sandbox {
        private int memoryLimitMb;
        private String jvmXms;
        private String jvmXmx;
        private int cpuShares;
    }

    @Getter
    @Setter
    public static class Cache {
        private int testcaseTtlMinutes;
        private int testcaseMaxSize;
        private boolean writeEnabled;
    }
}
