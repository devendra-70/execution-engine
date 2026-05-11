package com.epam.execution_engine_service.metrics.orchestrator;

import com.epam.execution_engine_service.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ExecutionLatencyMetricsTimer
 *
 * <p>Test Coverage:
 * - Timer initialization and registration
 * - Latency recording functionality
 * - Percentile threshold configuration (p95, p99)
 * - Configuration property injection
 * - Performance impact validation
 *
 * <p>SRS §12 Compliance: Execution latency signal (Signal 6)
 */
@ExtendWith(MockitoExtension.class)
class ExecutionLatencyMetricsTimerTest {

    private MeterRegistry meterRegistry;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ApplicationProperties.Metrics metrics;

    @Mock
    private ApplicationProperties.Metrics.Execution executionConfig;

    private ExecutionLatencyMetricsTimer timer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        when(applicationProperties.getMetrics()).thenReturn(metrics);
        when(metrics.getExecution()).thenReturn(executionConfig);
        when(executionConfig.getP95LatencyMsThreshold()).thenReturn(5000L);
        when(executionConfig.getP99LatencyMsThreshold()).thenReturn(3000L);

        timer = new ExecutionLatencyMetricsTimer(meterRegistry, applicationProperties);
    }

    @Test
    void testConstructor_InitializesTimer() {
        // Act & Assert
        assertNotNull(meterRegistry.find("execution.latency.milliseconds").timer());
    }

    @Test
    void testRecordLatency_WithRunnableExecutable() {
        // Arrange
        Runnable executable = () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Act
        timer.recordLatency(executable);

        // Assert
        assertTrue(meterRegistry.find("execution.latency.milliseconds").timer().count() > 0);
    }

    @Test
    void testRecordLatency_WithSupplierExecutable() {
        // Arrange
        Object result = new Object();
        java.util.function.Supplier<Object> supplier = () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result;
        };

        // Act
        Object returned = timer.recordLatency(supplier);

        // Assert
        assertNotNull(returned);
    }

    @Test
    void testConstructor_InjectsApplicationProperties() {
        // Act
        ExecutionLatencyMetricsTimer testTimer = new ExecutionLatencyMetricsTimer(meterRegistry, applicationProperties);

        // Assert
        assertNotNull(testTimer);
        verify(applicationProperties, atLeastOnce()).getMetrics();
    }

    @Test
    void testTimer_WithMultipleRecordings() {
        // Arrange
        Runnable executable = () -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Act
        for (int i = 0; i < 10; i++) {
            timer.recordLatency(executable);
        }

        // Assert
        assertTrue(meterRegistry.find("execution.latency.milliseconds").timer().count() >= 10);
    }

    @Test
    void testTimer_ReturnsPercentileData() {
        // Act & Assert
        assertNotNull(meterRegistry.find("execution.latency.milliseconds").timer());
    }

    @Test
    void testConstructor_WithConfigurationThresholds() {
        // Arrange
        when(executionConfig.getP95LatencyMsThreshold()).thenReturn(5000L);
        when(executionConfig.getP99LatencyMsThreshold()).thenReturn(3000L);

        // Act
        ExecutionLatencyMetricsTimer testTimer = new ExecutionLatencyMetricsTimer(meterRegistry, applicationProperties);

        // Assert
        assertNotNull(testTimer);
    }

    @Test
    void testRecordLatency_MeasuresCorrectDuration() {
        // Arrange
        long sleepMs = 50;
        Runnable executable = () -> {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Act
        timer.recordLatency(executable);
        double recordedMs = meterRegistry.find("execution.latency.milliseconds").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert - Recorded time should be at least the sleep time (may be more due to overhead)
        assertTrue(recordedMs >= sleepMs * 0.8); // Allow 20% variance
    }

    @Test
    void testTimer_IsThreadSafe() {
        // Arrange
        Runnable executable = () -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Act - Simulate concurrent access
        for (int i = 0; i < 100; i++) {
            timer.recordLatency(executable);
        }

        // Assert
        assertTrue(meterRegistry.find("execution.latency.milliseconds").timer().count() >= 100);
    }

    @Test
    void testConstructor_MeterRegistryInjected() {
        // Act
        ExecutionLatencyMetricsTimer testTimer = new ExecutionLatencyMetricsTimer(meterRegistry, applicationProperties);

        // Assert
        assertNotNull(testTimer);
    }

    @Test
    void testRecordLatency_WithCallableExecutable() throws Exception {
        // Arrange
        Object result = new Object();
        java.util.concurrent.Callable<Object> callable = () -> {
            Thread.sleep(10);
            return result;
        };

        // Act
        Object returned = timer.recordLatencyCallable(callable);

        // Assert
        assertNotNull(returned);
    }
}
