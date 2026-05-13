package com.epam.execution_engine_service.metrics.orchestrator;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.service.ContainerPoolService;
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
 * Unit Tests for SandboxPoolMetricsGauge.
 *
 * <p>Pool size is now sourced from {@link ContainerPoolService#getAvailableCount()} (SRS §12,
 * Signal 5 — EPMICMPCOD-525).
 */
@ExtendWith(MockitoExtension.class)
class SandboxPoolMetricsGaugeTest {

    private MeterRegistry meterRegistry;

    @Mock
    private ContainerPoolService containerPoolService;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private ApplicationProperties.Metrics metrics;

    @Mock
    private ApplicationProperties.Metrics.SandboxPool sandboxPoolConfig;

    private SandboxPoolMetricsGauge gauge;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        when(applicationProperties.getMetrics()).thenReturn(metrics);
        when(metrics.getSandboxPool()).thenReturn(sandboxPoolConfig);
        when(sandboxPoolConfig.getExhaustionThresholdPercent()).thenReturn(20);

        gauge = new SandboxPoolMetricsGauge(meterRegistry, containerPoolService, applicationProperties);
    }

    @Test
    void testConstructor_RegistersGauge() {
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testGauge_IsRegistered() {
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testConstructor_InjectsApplicationProperties() {
        SandboxPoolMetricsGauge testGauge = new SandboxPoolMetricsGauge(
                new SimpleMeterRegistry(), containerPoolService, applicationProperties);

        assertNotNull(testGauge);
        verify(applicationProperties, atLeastOnce()).getMetrics();
    }

    @Test
    void testGauge_WithExhaustionThreshold() {
        when(sandboxPoolConfig.getExhaustionThresholdPercent()).thenReturn(20);

        SandboxPoolMetricsGauge testGauge = new SandboxPoolMetricsGauge(
                new SimpleMeterRegistry(), containerPoolService, applicationProperties);

        assertNotNull(testGauge);
    }

    @Test
    void testGauge_CallsContainerPoolServiceGetAvailableCount() {
        when(containerPoolService.getAvailableCount()).thenReturn(8);

        // The gauge reads from ContainerPoolService on each scrape
        double value = meterRegistry.find("sandbox.pool.available.size").gauge().value();

        verify(containerPoolService, atLeastOnce()).getAvailableCount();
        assertEquals(8.0, value, "Gauge should return value from ContainerPoolService.getAvailableCount()");
    }

    @Test
    void testConstructor_WithNullContainerPoolService_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new SandboxPoolMetricsGauge(meterRegistry, null, applicationProperties),
                "Constructor should throw NullPointerException for null containerPoolService");
    }

    @Test
    void testConstructor_WithNullMeterRegistry_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new SandboxPoolMetricsGauge(null, containerPoolService, applicationProperties),
                "Constructor should throw NullPointerException for null meterRegistry");
    }

    @Test
    void testGauge_IsRealisticMetric() {
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testGauge_ReturnsNumericalValue() {
        when(containerPoolService.getAvailableCount()).thenReturn(5);
        SimpleMeterRegistry freshRegistry = new SimpleMeterRegistry();
        new SandboxPoolMetricsGauge(freshRegistry, containerPoolService, applicationProperties);

        double value = freshRegistry.find("sandbox.pool.available.size").gauge().value();

        assertEquals(5.0, value, "Gauge should return actual pool size from ContainerPoolService");
    }
}
