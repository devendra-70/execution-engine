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
    private Health health = new Health();
    private Metrics metrics = new Metrics();
    
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

    /**
     * Health Probe Configuration (EPMICMPCOD-522, SRS §3.2)
     * Configures health indicators for external dependencies (Kafka, PostgreSQL, Redis)
     */
    @Getter
    @Setter
    public static class Health {
        private Kafka kafka = new Kafka();

        @Getter
        @Setter
        public static class Kafka {
            private boolean lagCheckEnabled = true;
            private int checkIntervalSeconds = 30;
            private long lagThresholdForHealthRecords = 50000;
        }
    }

    /**
     * Operational Metrics Configuration (EPMICMPCOD-525, SRS §12)
     * Configures 6 Micrometer signals for ECS auto-scaling and CloudWatch monitoring
     */
    @Getter
    @Setter
    public static class Metrics {
        private boolean enabled = true;
        private Kafka kafka = new Kafka();
        private Cpu cpu = new Cpu();
        private HealthCheck healthCheck = new HealthCheck();
        private Persistence persistence = new Persistence();
        private SandboxPool sandboxPool = new SandboxPool();
        private Execution execution = new Execution();

        /**
         * Signal 1: Kafka Consumer Lag (SRS §11.2, §12)
         */
        @Getter
        @Setter
        public static class Kafka {
            private long lagThresholdRecords = 1000;
            private int lagCheckIntervalSeconds = 60;
        }

        /**
         * Signal 2: CPU Utilization (SRS §11.2)
         */
        @Getter
        @Setter
        public static class Cpu {
            private int thresholdPercent = 70;
        }

        /**
         * Signal 3: Health Check Failure Rate (SRS §12)
         */
        @Getter
        @Setter
        public static class HealthCheck {
            private int failureRateThresholdPercent = 10;
            private int windowSeconds = 300;
        }

        /**
         * Signal 4: Database Write Failure Rate (SRS §12)
         */
        @Getter
        @Setter
        public static class Persistence {
            private int failureRateThresholdCount = 5;
            private int windowSeconds = 60;
        }

        /**
         * Signal 5: Sandbox Pool Exhaustion (SRS §12)
         */
        @Getter
        @Setter
        public static class SandboxPool {
            private int exhaustionThresholdPercent = 20;
        }

        /**
         * Signal 6: Execution Latency (SRS §12)
         */
        @Getter
        @Setter
        public static class Execution {
            private long p95LatencyMsThreshold = 5000;
            private long p99LatencyMsThreshold = 3000;
        }
    }
}
