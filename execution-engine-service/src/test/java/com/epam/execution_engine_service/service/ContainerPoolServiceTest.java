package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.exception.ContainerAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContainerPoolServiceTest — Unit tests for ContainerPoolService (SRS §4.3)
 * 
 * Coverage target: ≥90% (services)
 */
@ExtendWith(MockitoExtension.class)
public class ContainerPoolServiceTest {

    @InjectMocks
    private ContainerPoolService containerPoolService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(containerPoolService, "warmMinSize", 2);
        ReflectionTestUtils.setField(containerPoolService, "maxSize", 10);
        ReflectionTestUtils.setField(containerPoolService, "idleTtlSeconds", 300);
        containerPoolService.initialize();
    }

    @Test
    public void testAcquire_Success() throws Exception {
        // Act
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));

        // Assert
        assertNotNull(handle);
        assertNotNull(handle.getId());
    }

    @Test
    public void testAcquire_Timeout() throws Exception {
        // Arrange: Acquire all available containers
        ContainerPoolService.ContainerHandle handle1 = containerPoolService.acquire(Duration.ofSeconds(5));
        ContainerPoolService.ContainerHandle handle2 = containerPoolService.acquire(Duration.ofSeconds(5));

        // Act & Assert: Third acquisition should timeout
        assertThrows(ContainerAcquisitionException.class,
                () -> containerPoolService.acquire(Duration.ofMillis(100)));
    }

    @Test
    public void testRelease() throws Exception {
        // Arrange
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));

        // Act
        containerPoolService.release(handle);
        ContainerPoolService.ContainerHandle reacquiredHandle = containerPoolService.acquire(Duration.ofSeconds(5));

        // Assert
        assertNotNull(reacquiredHandle);
        assertNotNull(reacquiredHandle.getId());
        // Note: Container pool doesn't guarantee same UUID, just that a container is available
    }

    @Test
    public void testGetPoolStats() throws Exception {
        // Arrange
        containerPoolService.acquire(Duration.ofSeconds(5));

        // Act
        Map<String, Integer> stats = containerPoolService.getPoolStats();

        // Assert
        assertNotNull(stats);
        assertEquals(1, stats.get("available"));  // 2 - 1 acquired
        assertEquals(2, stats.get("total"));
        assertEquals(10, stats.get("max"));
    }

}
