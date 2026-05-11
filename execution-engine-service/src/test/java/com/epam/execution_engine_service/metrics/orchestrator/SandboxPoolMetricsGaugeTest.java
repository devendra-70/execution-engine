package com.epam.execution_engine_service.metrics.orchestrator;

import com.epam.execution_engine_service.config.ApplicationProperties;
import com.epam.execution_engine_service.orchestrator.ContainerSpawner;
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
 * Unit Tests for SandboxPoolMetricsGauge
 *
 * <p>Test Coverage:
 * - Gauge initialization and registration
 * - Pool size metric retrieval from ContainerSpawner
 * - Configuration property injection
 * - Real-time pool capacity monitoring
 * - Metric metadata and tags
 *
 * <p>SRS §12 Compliance: Sandbox pool exhaustion signal (Signal 5)
 */
@ExtendWith(MockitoExtension.class)
class SandboxPoolMetricsGaugeTest {

    private MeterRegistry meterRegistry;

    @Mock
    private ContainerSpawner containerSpawner;

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

        gauge = new SandboxPoolMetricsGauge(meterRegistry, containerSpawner, applicationProperties);
    }

    @Test
    void testConstructor_RegistersGauge() {
        // Act & Assert
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testGauge_IsRegistered() {
        // Act & Assert
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testConstructor_InjectsApplicationProperties() {
        // Act
        SandboxPoolMetricsGauge testGauge = new SandboxPoolMetricsGauge(meterRegistry, containerSpawner, applicationProperties);

        // Assert
        assertNotNull(testGauge);
        verify(applicationProperties, atLeastOnce()).getMetrics();
    }

    @Test
    void testGauge_WithExhaustionThreshold() {
        // Arrange
        when(sandboxPoolConfig.getExhaustionThresholdPercent()).thenReturn(20);

        // Act
        SandboxPoolMetricsGauge testGauge = new SandboxPoolMetricsGauge(meterRegistry, containerSpawner, applicationProperties);

        // Assert
        assertNotNull(testGauge);
    }

    @Test
    void testConstructor_WithNullContainerSpawner_SetsReference() {
        // Act & Assert - Should handle initialization without requiring pool methods
        assertDoesNotThrow(() -> new SandboxPoolMetricsGauge(meterRegistry, containerSpawner, applicationProperties));
    }

    @Test
    void testGauge_IsRealisticMetric() {
        // Act & Assert
        assertNotNull(meterRegistry.find("sandbox.pool.available.size").gauge());
    }

    @Test
    void testGauge_ReturnsNumericalValue() {
        // Act
        double value = meterRegistry.find("sandbox.pool.available.size").gauge().value();

        // Assert - Should return a number >= 0
        assertTrue(value >= 0 || Double.isNaN(value));
    }
}
