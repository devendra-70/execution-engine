package com.epam.execution_engine_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application configuration properties loaded from application.properties
 * Prefix: app
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
    }
    
    @Getter
    @Setter
    public static class Kafka {
        private String topic;
        private int partitions;
        private int concurrency;
    }
    
    @Getter
    @Setter
    public static class Execution {
        private int orchestrationThreads;
    }
}
