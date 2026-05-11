package com.epam.execution_engine_service.metrics.orchestrator;

import com.epam.execution_engine_service.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Execution Latency Metrics Timer
 *
 * <p><b>SRS Compliance:</b> Section 12 - Execution Latency Signal (Signal 6)
 * <br><b>EPMICMPCOD-525:</b> Operational Metrics - Signal 6: End-to-End Execution Timing
 *
 * <p><b>Responsibility:</b>
 * Provides Timer utility methods to measure end-to-end execution latency from Kafka message consumption
 * through test case execution to offset commit. Tracks percentile distributions (p50, p95, p99) for
 * performance analysis and auto-scaling decisions.
 *
 * <p><b>Metric Exposed:</b>
 * <ul>
 *   <li><b>Name:</b> {@code execution.latency.milliseconds}</li>
 *   <li><b>Type:</b> Timer (cumulative duration measurements with percentile distributions)</li>
 *   <li><b>Unit:</b> Milliseconds (duration from message consume → offset commit)</li>
 *   <li><b>Thresholds:</b>
 *       <ul>
 *           <li>p95: {@code app.metrics.execution.p95-latency-ms-threshold} (default: 5000ms)</li>
 *           <li>p99: {@code app.metrics.execution.p99-latency-ms-threshold} (default: 3000ms)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code app.metrics.execution.p95-latency-ms-threshold} - p95 alert threshold (default: 5000)</li>
 *   <li>{@code app.metrics.execution.p99-latency-ms-threshold} - p99 alert threshold (default: 3000)</li>
 * </ul>
 *
 * <p><b>Example Metrics Output (Prometheus format):</b>
 * <pre>
 * # HELP execution_latency_milliseconds_seconds End-to-end execution latency
 * # TYPE execution_latency_milliseconds_seconds summary
 * execution_latency_milliseconds_seconds_count{operation="kafka_listener"} 1234.0
 * execution_latency_milliseconds_seconds_sum{operation="kafka_listener"} 4567890.0
 * execution_latency_milliseconds_seconds{operation="kafka_listener",quantile="0.5"} 3500.0
 * execution_latency_milliseconds_seconds{operation="kafka_listener",quantile="0.95"} 4200.0
 * execution_latency_milliseconds_seconds{operation="kafka_listener",quantile="0.99"} 4800.0
 * </pre>
 *
 * <p><b>SRS §12 Compliance:</b>
 * Captures end-to-end execution latency as one of 6 critical operational signals.
 * Enables detection of performance degradation and triggers auto-scaling.
 *
 * <p><b>Integration with Auto-Scaling:</b>
 * CloudWatch alarms monitor percentile latencies:
 * - If p95 > configured threshold → potential performance issue, monitor closely
 * - If p99 > configured threshold → degradation confirmed, trigger scale-up
 * - ECS auto-scaling adds capacity to reduce per-task load
 *
 * <p><b>Execution Flow (with timing):</b>
 * <pre>
 * T0: Kafka listener receives ExecutionTaskEvent message
 * T1: Message handed to TaskExecutor (Pool B)
 * T2: ContainerSpawner acquires sandbox container
 * T3: Test cases fed to container via socket
 * T4: Execution latency timer STARTS
 * T5: Sandbox wrapper compiles user code
 * T6: Test cases executed in sandbox
 * T7: Results aggregated by Orchestrator
 * T8: SubmissionRepository.save() persists result
 * T9: Kafka offset committed (acks from broker)
 * T10: Execution latency timer STOPS
 * DURATION = T10 - T4 (core execution + persistence + offset commit)
 * </pre>
 *
 * <p><b>Percentile Interpretation:</b>
 * <ul>
 *   <li>p50 (median): 50% of executions complete within this time</li>
 *   <li>p95: 95% of executions complete within this time (good tail latency)</li>
 *   <li>p99: 99% of executions complete within this time (excellent tail latency)</li>
 *   <li>Used for SLA compliance: "99% of submissions complete within X ms"</li>
 * </ul>
 *
 * <p><b>Usage in Kafka Listener:</b>
 * <pre>
 * {@code
 * @KafkaListener(topics = "execution-tasks")
 * public void consumeExecutionTask(ExecutionTaskEvent event) {
 *     // Wrap entire execution with timer
 *     executionLatencyTimer.recordLatency(() -> {
 *         // 1. Get test cases
 *         List<TestCase> testCases = getTestCases(event.getProblemId());
 *         
 *         // 2. Spawn container and execute
 *         ContainerResult result = containerSpawner.spawn(event.getSourceCode(), timeout);
 *         
 *         // 3. Process results
 *         ExecutionResult executionResult = processContainerResult(result);
 *         
 *         // 4. Persist to database
 *         submissionRepository.save(executionResult.toEntity());
 *         
 *         // 5. Commit Kafka offset (automatic in listener)
 *     });
 * }
 * }
 * </pre>
 *
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Timer recording adds <1ms overhead per execution</li>
 *   <li>Percentile calculation is computed during scrape (not per request)</li>
 *   <li>Thread-safe: MeterRegistry.Timer is atomic</li>
 *   <li>Distribution: Stored in memory, summarized on scrape</li>
 * </ul>
 *
 * <p><b>Advanced Timer Features:</b>
 * <ul>
 *   <li>Percentile histograms: p50, p75, p95, p99, p99.9</li>
 *   <li>Service level objectives (SLOs): Compare actual vs threshold</li>
 *   <li>Max tracking: Records longest execution for anomaly detection</li>
 *   <li>Count/Sum: Total executions and aggregate duration</li>
 * </ul>
 *
 * @author EPMICMPCOD-350 Implementation Team
 * @version 1.0
 * @see io.micrometer.core.instrument.Timer
 * @see ApplicationProperties.Metrics.Execution
 */
@Component
public class ExecutionLatencyMetricsTimer {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionLatencyMetricsTimer.class);
    private static final String METRIC_NAME = "execution.latency.milliseconds";
    private static final String COMPONENT_NAME = "ExecutionLatencyMetricsTimer";

    private final MeterRegistry meterRegistry;
    private final ApplicationProperties applicationProperties;
    private Timer latencyTimer;

    /**
     * Constructs the execution latency timer with dependency injection.
     *
     * <p>Registers the timer during bean initialization.
     *
     * @param meterRegistry          Micrometer MeterRegistry for metric registration
     * @param applicationProperties  Application configuration containing latency thresholds
     */
    @Autowired
    public ExecutionLatencyMetricsTimer(
            MeterRegistry meterRegistry,
            ApplicationProperties applicationProperties) {
        this.meterRegistry = meterRegistry;
        this.applicationProperties = applicationProperties;
        initializeTimer();
    }

    /**
     * Initializes the execution latency timer during bean creation.
     *
     * <p><b>Timer Configuration:</b>
     * <ul>
     *   <li>Name: {@code execution.latency.milliseconds}</li>
     *   <li>Unit: Milliseconds</li>
     *   <li>Percentiles: p50, p95, p99 (for SLA compliance)</li>
     *   <li>Description: Includes SRS reference and thresholds</li>
     * </ul>
     */
    private void initializeTimer() {
        try {
            this.latencyTimer = Timer.builder(METRIC_NAME)
                    .description("End-to-end execution latency from Kafka consume to offset commit. " +
                            "SRS §12: Execution Latency Signal. " +
                            "p95 Threshold: " + applicationProperties.getMetrics().getExecution().getP95LatencyMsThreshold() + "ms, " +
                            "p99 Threshold: " + applicationProperties.getMetrics().getExecution().getP99LatencyMsThreshold() + "ms")
                    .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)  // p50, p75, p95, p99, p99.9
                    .tag("operation", "kafka_listener")
                    .tag("component", "execution-orchestrator")
                    .tag("srs_section", "12")
                    .tag("signal_number", "6")
                    .register(meterRegistry);

            logger.info("✓ {} registered successfully", COMPONENT_NAME);
            logger.info("  Metric: {}", METRIC_NAME);
            logger.info("  Percentiles: p50, p75, p95, p99, p99.9");
            logger.info("  p95 Threshold: {}ms", applicationProperties.getMetrics().getExecution().getP95LatencyMsThreshold());
            logger.info("  p99 Threshold: {}ms", applicationProperties.getMetrics().getExecution().getP99LatencyMsThreshold());

        } catch (Exception e) {
            logger.error("Failed to initialize {} timer", COMPONENT_NAME, e);
            throw new RuntimeException("Failed to register execution latency metrics", e);
        }
    }

    /**
     * Records execution latency for a Kafka listener execution.
     *
     * <p><b>Usage Pattern:</b>
     * <pre>
     * {@code
     * executionLatencyTimer.recordLatency(() -> {
     *     // Entire execution wrapped
     *     containerSpawner.spawn(code);
     *     submissionRepository.save(result);
     *     kafkaTemplate.sendDefault(message);
     * });
     * }
     * </pre>
     *
     * <p><b>Timing Window:</b>
     * <ul>
     *   <li>Start: Immediately before callable executes</li>
     *   <li>End: After callable completes (including exceptions)</li>
     *   <li>Unit: Milliseconds (converted automatically)</li>
     * </ul>
     *
     * <p><b>Exception Handling:</b>
     * <ul>
     *   <li>If callable throws exception: still recorded in timer</li>
     *   <li>Exception propagated to caller (not swallowed)</li>
     * </ul>
     *
     * @param task The executable task (Kafka listener execution logic)
     */
    public void recordLatency(Runnable task) {
        latencyTimer.record(task);
    }

    /**
     * Records execution latency for a task with return value.
     *
     * <p><b>Usage Pattern:</b>
     * <pre>
     * {@code
     * ExecutionResult result = executionLatencyTimer.recordLatency(() -> {
     *     ContainerResult containerResult = containerSpawner.spawn(code);
     *     return processResult(containerResult);
     * });
     * }
     * </pre>
     *
     * @param <T>      Return type of the task
     * @param task     The executable task (with return value)
     * @return         Result from the task execution
     */
    public <T> T recordLatency(Supplier<T> task) {
        // Record task execution with automatic timing
        long startTime = System.nanoTime();
        try {
            return task.get();
        } finally {
            long endTime = System.nanoTime();
            latencyTimer.record(endTime - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Records execution latency using a callable that may throw checked exceptions.
     *
     * <p><b>Usage Pattern:</b>
     * <pre>
     * {@code
     * executionLatencyTimer.recordLatencyCallable(() -> {
     *     // Code that throws InterruptedException, IOException, etc.
     *     Thread.sleep(100);
     *     return processResult();
     * });
     * }
     * </pre>
     *
     * @param <T>      Return type of the callable
     * @param callable The executable task (may throw checked exceptions)
     * @return         Result from the callable execution
     * @throws Exception If the callable throws any checked exception
     */
    public <T> T recordLatencyCallable(java.util.concurrent.Callable<T> callable) throws Exception {
        return latencyTimer.recordCallable(callable);
    }

    /**
     * Records a specific duration manually (if pre-calculated).
     *
     * <p><b>Usage Pattern:</b>
     * <pre>
     * {@code
     * long startTime = System.nanoTime();
     * // ... execution ...
     * long endTime = System.nanoTime();
     * executionLatencyTimer.recordDuration(endTime - startTime, TimeUnit.NANOSECONDS);
     * }
     * </pre>
     *
     * @param duration The duration value
     * @param unit     The unit of the duration value
     */
    public void recordDuration(long duration, TimeUnit unit) {
        latencyTimer.record(duration, unit);
    }

    /**
     * Returns the underlying Timer for advanced usage (if needed).
     *
     * @return The MeterRegistry Timer instance
     */
    public Timer getTimer() {
        return latencyTimer;
    }
}
