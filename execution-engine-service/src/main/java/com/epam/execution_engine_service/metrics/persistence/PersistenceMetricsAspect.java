package com.epam.execution_engine_service.metrics.persistence;

import com.epam.execution_engine_service.config.ApplicationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect for Database Write Failure Metrics
 *
 * <p><b>SRS Compliance:</b> Section 12 - DB Write Failure Rate Signal (Signal 4)
 * <br><b>EPMICMPCOD-525:</b> Operational Metrics - Signal 4: Database Persistence Failure Monitoring
 *
 * <p><b>Responsibility:</b>
 * Intercepts SubmissionRepository save operations and tracks DataAccessException occurrences.
 * Uses AOP @Aspect to capture database failures without modifying repository code.
 *
 * <p><b>Metric Exposed:</b>
 * <ul>
 *   <li><b>Name:</b> {@code persistence.write.failures.count}</li>
 *   <li><b>Type:</b> Counter (monotonically increasing)</li>
 *   <li><b>Unit:</b> Count of write failures</li>
 *   <li><b>Threshold:</b> {@code app.metrics.persistence.failure-rate-threshold-count} (default: 5/minute)</li>
 *   <li><b>Window:</b> {@code app.metrics.persistence.window-seconds} (default: 60s)</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code app.metrics.persistence.failure-rate-threshold-count} - Alert threshold (default: 5)</li>
 *   <li>{@code app.metrics.persistence.window-seconds} - Measurement window (default: 60)</li>
 * </ul>
 *
 * <p><b>Method Interception:</b>
 * <ol>
 *   <li>Spring intercepts calls to {@code SubmissionRepository.save()}</li>
 *   <li>Method executes (saves SubmissionEntity to PostgreSQL)</li>
 *   <li>If DataAccessException thrown (connection failure, constraint violation, etc.):</li>
 *   <li>Aspect @AfterThrowing triggers → failure counter incremented</li>
 *   <li>Exception propagated to caller</li>
 * </ol>
 *
 * <p><b>Captured Failure Types:</b>
 * <ul>
 *   <li>Connection failures (database offline)</li>
 *   <li>SQL syntax errors</li>
 *   <li>Constraint violations (unique, foreign key)</li>
 *   <li>Transaction rollback errors</li>
 *   <li>Deadlock exceptions</li>
 *   <li>Database permission errors</li>
 * </ul>
 *
 * <p><b>Example Metrics Output (Prometheus format):</b>
 * <pre>
 * # HELP persistence_write_failures_total Total number of database write failures
 * # TYPE persistence_write_failures_total counter
 * persistence_write_failures_total{repository="SubmissionRepository",operation="save"} 2.0
 * </pre>
 *
 * <p><b>SRS §5.2 & §12 Compliance:</b>
 * Monitors SubmissionRepository write operations as defined in SRS Section 5.2 (Persistence Component).
 * Provides visibility into database health for operational alerting.
 *
 * <p><b>Integration with Auto-Scaling:</b>
 * CloudWatch alarms monitor this metric:
 * - Failure count per time window
 * - If count > configured threshold → trigger scale-up alarm (indicates database saturation)
 * - ECS auto-scaling responds to alarm
 *
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Lightweight: Only increments Counter (minimal CPU impact)</li>
 *   <li>Non-invasive: Aspect only executes on exception (@AfterThrowing)</li>
 *   <li>No latency: Counter increment is <1μs operation</li>
 *   <li>Thread-safe: MeterRegistry.Counter is atomic</li>
 * </ul>
 *
 * <p><b>Aspect Pointcut:</b>
 * <pre>
 * execution(* com.epam.execution_engine_service.persistence.repository.SubmissionRepository.save(..))
 * </pre>
 *
 * <p><b>Exception Types Caught:</b>
 * <ul>
 *   <li>DataAccessException (base class for all Spring Data/JPA exceptions)</li>
 *   <li>Subclasses:
 *       <ul>
 *           <li>DataIntegrityViolationException (constraint violations)</li>
 *           <li>PermissionDeniedDataAccessException (SQL permission errors)</li>
 *           <li>DeadlockLoserDataAccessException (database deadlock)</li>
 *           <li>TransientDataAccessException (transient connection failures)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @author EPMICMPCOD-350 Implementation Team
 * @version 1.0
 * @see org.aspectj.lang.annotation.Aspect
 * @see org.springframework.dao.DataAccessException
 * @see com.epam.execution_engine_service.persistence.repository.SubmissionRepository
 * @see ApplicationProperties.Metrics.Persistence
 */
@Aspect
@Component
public class PersistenceMetricsAspect {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceMetricsAspect.class);
    private static final String METRIC_NAME = "persistence.write.failures.count";
    private static final String ASPECT_NAME = "PersistenceMetricsAspect";

    private final MeterRegistry meterRegistry;
    private final ApplicationProperties applicationProperties;
    private Counter failureCounter;

    /**
     * Constructs the persistence metrics aspect with dependency injection.
     *
     * @param meterRegistry          Micrometer MeterRegistry for metric registration
     * @param applicationProperties  Application configuration containing persistence thresholds
     */
    @Autowired
    public PersistenceMetricsAspect(
            MeterRegistry meterRegistry,
            ApplicationProperties applicationProperties) {
        this.meterRegistry = meterRegistry;
        this.applicationProperties = applicationProperties;
        initializeMetrics();
    }

    /**
     * Initializes the failure counter metric on bean creation.
     *
     * <p>Creates the metric that will be incremented whenever a database write operation fails.
     */
    private void initializeMetrics() {
        this.failureCounter = Counter.builder(METRIC_NAME)
                .description("Total number of database write failures (DataAccessException). " +
                        "SRS §12: DB Write Failure Signal. Threshold: " +
                        applicationProperties.getMetrics().getPersistence().getFailureRateThresholdCount() +
                        " failures/" +
                        applicationProperties.getMetrics().getPersistence().getWindowSeconds() + "s")
                .tag("repository", "SubmissionRepository")
                .tag("operation", "save")
                .tag("srs_section", "12")
                .tag("signal_number", "4")
                .register(meterRegistry);

        logger.info("✓ {} initialized successfully", ASPECT_NAME);
        logger.info("  Metric: {}", METRIC_NAME);
        logger.info("  Threshold: {} failures per {}s",
                applicationProperties.getMetrics().getPersistence().getFailureRateThresholdCount(),
                applicationProperties.getMetrics().getPersistence().getWindowSeconds());
    }

    /**
     * Intercepts SubmissionRepository.save() method calls and records failures.
     *
     * <p><b>Pointcut:</b> Applies to SubmissionRepository.save() method execution
     * <br><b>Trigger:</b> When DataAccessException is thrown by the save operation
     * <br><b>Action:</b> Increment failure counter and log details
     *
     * <p><b>Method Interception Flow:</b>
     * <ol>
     *   <li>save() method called on SubmissionRepository bean</li>
     *   <li>Spring Data JPA attempts to persist SubmissionEntity</li>
     *   <li>If successful: aspect not triggered, counter not incremented</li>
     *   <li>If DataAccessException thrown: this method triggered</li>
     *   <li>Counter incremented by 1</li>
     *   <li>Exception propagated to caller (aspect does not swallow it)</li>
     * </ol>
     *
     * <p><b>Example Stack Trace Capture:</b>
     * <pre>
     * org.springframework.dao.DataIntegrityViolationException: 
     *   could not execute statement [insert] 
     *   [duplicate key value violates unique constraint]
     * Caused by: org.postgresql.util.PSQLException: 
     *   ERROR: duplicate key value violates unique constraint
     * </pre>
     *
     * <p><b>SRS §12 Compliance:</b>
     * Captures database persistence failures for operational alerting and auto-scaling.
     *
     * @param joinPoint    AOP join point providing method context
     * @param exception    The DataAccessException thrown by the save operation
     * @see org.aspectj.lang.JoinPoint
     * @see org.springframework.dao.DataAccessException
     */
    @AfterThrowing(
            pointcut = "execution(* com.epam.execution_engine_service.persistence.repository.SubmissionRepository.save(..))",
            throwing = "exception"
    )
    public void recordWriteFailure(JoinPoint joinPoint, DataAccessException exception) {
        try {
            // Increment failure counter
            failureCounter.increment();

            // Log failure details for troubleshooting
            logger.warn("Database write failure recorded: {} | Exception: {} | Message: {}",
                    METRIC_NAME,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());

            logger.debug("Failure context - Repository method: {}.{}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName());

        } catch (Exception loggingError) {
            // Ensure aspect does not fail if logging fails
            logger.error("Error processing persistence failure metric", loggingError);
        }
    }

    /**
     * Alternative pointcut for batch save operations (saveAll).
     *
     * <p>Intercepts SubmissionRepository.saveAll() to track batch write failures.
     * Invoked similarly to recordWriteFailure() when DataAccessException occurs.
     *
     * @param joinPoint    AOP join point providing method context
     * @param exception    The DataAccessException thrown by the saveAll operation
     */
    @AfterThrowing(
            pointcut = "execution(* com.epam.execution_engine_service.persistence.repository.SubmissionRepository.saveAll(..))",
            throwing = "exception"
    )
    public void recordBatchWriteFailure(JoinPoint joinPoint, DataAccessException exception) {
        try {
            // Increment failure counter (batch failure counts as one event)
            failureCounter.increment();

            logger.warn("Database batch write failure recorded: {} | Exception: {} | Method: saveAll",
                    METRIC_NAME,
                    exception.getClass().getSimpleName());

        } catch (Exception loggingError) {
            logger.error("Error processing batch persistence failure metric", loggingError);
        }
    }
}
