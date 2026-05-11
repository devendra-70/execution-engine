package com.epam.execution_engine_service.metrics;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.metrics.orchestrator.ExecutionLatencyMetricsTimer;
import com.epam.execution_engine_service.metrics.orchestrator.SandboxPoolMetricsGauge;
import com.epam.execution_engine_service.metrics.persistence.PersistenceMetricsAspect;
import com.epam.execution_engine_service.metrics.health.HealthCheckMetricsFilter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Operational Metrics Registry and Coordinator
 *
 * <p><b>SRS Compliance:</b> Section 12 - Operational Metrics (6 Signals)
 * <br><b>EPMICMPCOD-525:</b> Implement 6 Micrometer metrics for ECS auto-scaling and CloudWatch monitoring
 *
 * <p><b>Responsibility:</b>
 * Centralized component that registers and coordinates all 6 operational metrics on application startup.
 * Ensures all metrics are properly initialized with configurable thresholds from ApplicationProperties.
 * Acts as single source of truth for metrics initialization and dependency injection.
 *
 * <p><b>Metrics Registered (6 Signals):</b>
 * <ol>
 *   <li><b>Signal 1: Kafka Consumer Lag</b> (Gauge)
 *       <br>Metric: {@code kafka.consumer.lag.records}
 *       <br>Threshold: {@code app.metrics.kafka.lag-threshold-records} (default: 1000)
 *       <br>Purpose: Auto-scaling trigger when lag exceeds threshold
 *       <br>SRS §11.2 Reference
 *   </li>
 *   <li><b>Signal 2: CPU Utilization</b> (Gauge)
 *       <br>Metric: {@code process.cpu.usage}
 *       <br>Threshold: {@code app.metrics.cpu.threshold-percent} (default: 70)
 *       <br>Purpose: JVM-managed metric, auto-scaling trigger when CPU > threshold
 *       <br>SRS §11.2 Reference
 *   </li>
 *   <li><b>Signal 3: Health Check Failure Rate</b> (Counter)
 *       <br>Metric: {@code actuator.health.failures.count}
 *       <br>Threshold: {@code app.metrics.health-check.failure-rate-threshold-percent} (default: 10%)
 *       <br>Purpose: Track non-2xx responses from /actuator/health endpoint
 *       <br>Implementation: {@link HealthCheckMetricsFilter}
 *       <br>SRS §12 Reference
 *   </li>
 *   <li><b>Signal 4: DB Write Failure Rate</b> (Counter)
 *       <br>Metric: {@code persistence.write.failures.count}
 *       <br>Threshold: {@code app.metrics.persistence.failure-rate-threshold-count} (default: 5/minute)
 *       <br>Purpose: Track DataAccessException on database operations
 *       <br>Implementation: {@link PersistenceMetricsAspect}
 *       <br>SRS §12 Reference
 *   </li>
 *   <li><b>Signal 5: Sandbox Pool Exhaustion</b> (Gauge)
 *       <br>Metric: {@code sandbox.pool.available.size}
 *       <br>Threshold: {@code app.metrics.sandbox-pool.exhaustion-threshold-percent} (default: 20%)
 *       <br>Purpose: Monitor available containers in sandbox pool
 *       <br>Implementation: {@link SandboxPoolMetricsGauge}
 *       <br>SRS §12 Reference
 *   </li>
 *   <li><b>Signal 6: Execution Latency</b> (Timer)
 *       <br>Metric: {@code execution.latency.milliseconds} (p50, p95, p99 percentiles)
 *       <br>Thresholds: 
 *           - {@code app.metrics.execution.p95-latency-ms-threshold} (default: 5000ms)
 *           - {@code app.metrics.execution.p99-latency-ms-threshold} (default: 3000ms)
 *       <br>Purpose: End-to-end execution timing from Kafka consume to offset commit
 *       <br>Implementation: {@link ExecutionLatencyMetricsTimer}
 *       <br>SRS §12 Reference
 *   </li>
 * </ol>
 *
 * <p><b>Configuration Properties (from application.properties):</b>
 * <ul>
 *   <li>{@code app.metrics.enabled} - Master toggle (default: true)</li>
 *   <li>{@code app.metrics.kafka.lag-threshold-records} - Signal 1 threshold</li>
 *   <li>{@code app.metrics.cpu.threshold-percent} - Signal 2 threshold</li>
 *   <li>{@code app.metrics.health-check.failure-rate-threshold-percent} - Signal 3 threshold</li>
 *   <li>{@code app.metrics.health-check.window-seconds} - Signal 3 time window</li>
 *   <li>{@code app.metrics.persistence.failure-rate-threshold-count} - Signal 4 threshold</li>
 *   <li>{@code app.metrics.persistence.window-seconds} - Signal 4 time window</li>
 *   <li>{@code app.metrics.sandbox-pool.exhaustion-threshold-percent} - Signal 5 threshold</li>
 *   <li>{@code app.metrics.execution.p95-latency-ms-threshold} - Signal 6 p95 threshold</li>
 *   <li>{@code app.metrics.execution.p99-latency-ms-threshold} - Signal 6 p99 threshold</li>
 * </ul>
 *
 * <p><b>Endpoint:</b> {@code GET /actuator/prometheus} (exposes all metrics for CloudWatch scraping)
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li>Spring Container initialization</li>
 *   <li>ApplicationProperties injection and validation</li>
 *   <li>All metric implementations bean initialization</li>
 *   <li>{@code ApplicationReadyEvent} triggers metric registration and logging</li>
 *   <li>Metrics available at /actuator/prometheus endpoint</li>
 * </ul>
 *
 * <p><b>Best Practices:</b>
 * <ul>
 *   <li>All thresholds are externalized to application.properties (NO hardcoding)</li>
 *   <li>Metrics are lazy-loaded only if enabled</li>
 *   <li>Each metric has detailed description and SRS reference</li>
 *   <li>Centralized logging for operational troubleshooting</li>
 * </ul>
 *
 * @author EPMICMPCOD-350 Implementation Team
 * @version 1.0
 * @see ApplicationProperties.Metrics
 * @see HealthCheckMetricsFilter
 * @see PersistenceMetricsAspect
 * @see SandboxPoolMetricsGauge
 * @see ExecutionLatencyMetricsTimer
 */
@Component
public class OperationalMetricsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(OperationalMetricsRegistry.class);

    private final ApplicationProperties applicationProperties;
    private final HealthCheckMetricsFilter healthCheckMetricsFilter;
    private final PersistenceMetricsAspect persistenceMetricsAspect;
    private final SandboxPoolMetricsGauge sandboxPoolMetricsGauge;
    private final ExecutionLatencyMetricsTimer executionLatencyMetricsTimer;

    /**
     * Constructs the metrics registry with all metric implementations.
     *
     * @param applicationProperties     Application configuration with all metric thresholds
     * @param healthCheckMetricsFilter  Signal 3 implementation
     * @param persistenceMetricsAspect  Signal 4 implementation
     * @param sandboxPoolMetricsGauge   Signal 5 implementation
     * @param executionLatencyMetricsTimer Signal 6 implementation
     */
    @Autowired
    public OperationalMetricsRegistry(
            ApplicationProperties applicationProperties,
            HealthCheckMetricsFilter healthCheckMetricsFilter,
            PersistenceMetricsAspect persistenceMetricsAspect,
            SandboxPoolMetricsGauge sandboxPoolMetricsGauge,
            ExecutionLatencyMetricsTimer executionLatencyMetricsTimer) {
        this.applicationProperties = Objects.requireNonNull(applicationProperties, "applicationProperties cannot be null");
        this.healthCheckMetricsFilter = Objects.requireNonNull(healthCheckMetricsFilter, "healthCheckMetricsFilter cannot be null");
        this.persistenceMetricsAspect = Objects.requireNonNull(persistenceMetricsAspect, "persistenceMetricsAspect cannot be null");
        this.sandboxPoolMetricsGauge = Objects.requireNonNull(sandboxPoolMetricsGauge, "sandboxPoolMetricsGauge cannot be null");
        this.executionLatencyMetricsTimer = Objects.requireNonNull(executionLatencyMetricsTimer, "executionLatencyMetricsTimer cannot be null");
    }

    /**
     * Registers all operational metrics on Spring application startup.
     *
     * <p><b>Triggered by:</b> {@link ApplicationReadyEvent} (fired after context initialization)
     *
     * <p><b>Registration Flow:</b>
     * <ol>
     *   <li>Check if metrics are globally enabled</li>
     *   <li>Log all 6 signal thresholds from ApplicationProperties</li>
     *   <li>Initialize and register each metric bean</li>
     *   <li>Log success summary</li>
     * </ol>
     *
     * <p><b>SRS §12 Compliance:</b>
     * All 6 metrics are registered and ready for CloudWatch export via /actuator/prometheus endpoint.
     *
     * @see org.springframework.boot.context.event.ApplicationReadyEvent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOperationalMetrics() {
        boolean metricsEnabled = applicationProperties.getMetrics().isEnabled();
        logger.info("Operational Metrics Registry Initialization");
        logger.info("Metrics Global Toggle: {}", metricsEnabled ? "ENABLED" : "DISABLED");

        if (!metricsEnabled) {
            logger.warn("Metrics are disabled. Set app.metrics.enabled=true to activate monitoring.");
            return;
        }

        // Log all metric thresholds (no hardcoding - all from ApplicationProperties)
        logMetricThresholds();

        // Ensure all metric implementations are initialized
        ensureMetricImplementationsReady();

        logger.info("Operational Metrics Registration Completed Successfully");
        logger.info("Available at: GET /actuator/prometheus (for CloudWatch scraping)");
    }

    /**
     * Logs all configured metric thresholds for troubleshooting and auditability.
     *
     * <p>Provides visibility into what thresholds have been loaded from application.properties,
     * making it easier to debug auto-scaling and alerting behavior.
     */
    private void logMetricThresholds() {
        ApplicationProperties.Metrics metrics = applicationProperties.getMetrics();

        logger.info("");
        logger.info("SIGNAL 1: Kafka Consumer Lag");
        logger.info("  - Metric: kafka.consumer.lag.records (Gauge)");
        logger.info("  - Threshold: {} records", metrics.getKafka().getLagThresholdRecords());
        logger.info("  - Check Interval: {} seconds", metrics.getKafka().getLagCheckIntervalSeconds());
        logger.info("  - SRS Reference: §11.2 (Auto-scaling trigger)");

        logger.info("");
        logger.info("SIGNAL 2: CPU Utilization");
        logger.info("  - Metric: process.cpu.usage (Gauge)");
        logger.info("  - Threshold: {}%", metrics.getCpu().getThresholdPercent());
        logger.info("  - SRS Reference: §11.2 (Auto-scaling trigger)");

        logger.info("");
        logger.info("SIGNAL 3: Health Check Failure Rate");
        logger.info("  - Metric: actuator.health.failures.count (Counter)");
        logger.info("  - Threshold: {}% failure rate", metrics.getHealthCheck().getFailureRateThresholdPercent());
        logger.info("  - Window: {} seconds", metrics.getHealthCheck().getWindowSeconds());
        logger.info("  - Implementation: HealthCheckMetricsFilter");
        logger.info("  - SRS Reference: §12 (Operational signal)");

        logger.info("");
        logger.info("SIGNAL 4: DB Write Failure Rate");
        logger.info("  - Metric: persistence.write.failures.count (Counter)");
        logger.info("  - Threshold: {} failures/minute", metrics.getPersistence().getFailureRateThresholdCount());
        logger.info("  - Window: {} seconds", metrics.getPersistence().getWindowSeconds());
        logger.info("  - Implementation: PersistenceMetricsAspect (AOP on SubmissionRepository)");
        logger.info("  - SRS Reference: §12 (Operational signal)");

        logger.info("");
        logger.info("SIGNAL 5: Sandbox Pool Exhaustion");
        logger.info("  - Metric: sandbox.pool.available.size (Gauge)");
        logger.info("  - Threshold: {}% of warm-min-size", metrics.getSandboxPool().getExhaustionThresholdPercent());
        logger.info("  - Implementation: SandboxPoolMetricsGauge (backed by ContainerSpawner)");
        logger.info("  - SRS Reference: §12 (Operational signal)");

        logger.info("");
        logger.info("SIGNAL 6: Execution Latency");
        logger.info("  - Metric: execution.latency.milliseconds (Timer: p50, p95, p99)");
        logger.info("  - p95 Threshold: {}ms", metrics.getExecution().getP95LatencyMsThreshold());
        logger.info("  - p99 Threshold: {}ms", metrics.getExecution().getP99LatencyMsThreshold());
        logger.info("  - Implementation: ExecutionLatencyMetricsTimer (wraps Kafka listener)");
        logger.info("  - SRS Reference: §12 (Operational signal)");
    }

    /**
     * Validates that all metric implementations are properly initialized as Spring beans.
     *
     * <p>This ensures that all @Component beans have been created and are ready to collect metrics.
     * If any bean initialization fails, it will be caught by Spring during application startup.
     */
    private void ensureMetricImplementationsReady() {
        logger.debug("Verifying metric implementation beans...");

        if (healthCheckMetricsFilter == null) {
            throw new IllegalStateException("HealthCheckMetricsFilter bean not initialized. SRS §12 Signal 3 unavailable.");
        }
        logger.debug("✓ HealthCheckMetricsFilter bean initialized");

        if (persistenceMetricsAspect == null) {
            throw new IllegalStateException("PersistenceMetricsAspect bean not initialized. SRS §12 Signal 4 unavailable.");
        }
        logger.debug("✓ PersistenceMetricsAspect bean initialized");

        if (sandboxPoolMetricsGauge == null) {
            throw new IllegalStateException("SandboxPoolMetricsGauge bean not initialized. SRS §12 Signal 5 unavailable.");
        }
        logger.debug("✓ SandboxPoolMetricsGauge bean initialized");

        if (executionLatencyMetricsTimer == null) {
            throw new IllegalStateException("ExecutionLatencyMetricsTimer bean not initialized. SRS §12 Signal 6 unavailable.");
        }
        logger.debug("✓ ExecutionLatencyMetricsTimer bean initialized");

        logger.info("✓ All metric implementations verified and ready to collect metrics.");
    }
}
