package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.exception.ContainerAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ContainerPoolServiceTest — Unit tests for ContainerPoolService (SRS §4.3, §12).
 *
 * <p>Covers EPMICMPCOD-530 (pool lifecycle), EPMICMPCOD-531 (idle TTL sweep), and
 * EPMICMPCOD-532 (TLE discard-and-replace).
 */
@ExtendWith(MockitoExtension.class)
public class ContainerPoolServiceTest {

    @Mock
    private ContainerSpawner containerSpawner;

    @InjectMocks
    private ContainerPoolService containerPoolService;

    @BeforeEach
    public void setUp() throws Exception {
        ReflectionTestUtils.setField(containerPoolService, "warmMinSize", 2);
        ReflectionTestUtils.setField(containerPoolService, "idleTtlSeconds", 300);
        ReflectionTestUtils.setField(containerPoolService, "sandboxBasePort", 20000);

        // Each startDetachedContainer call returns a unique fake container ID
        when(containerSpawner.startDetachedContainer(anyInt()))
                .thenAnswer(inv -> "container-" + inv.getArgument(0));

        containerPoolService.initialize();
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-530: Startup initialisation
    // -------------------------------------------------------------------------

    @Test
    public void testInitialize_StartsWarmMinSizeContainers() {
        // Pool was initialised in setUp — verify warm-min-size containers started
        verify(containerSpawner, times(2)).startDetachedContainer(anyInt());
        assertEquals(2, containerPoolService.getAvailableCount());
    }

    @Test
    public void testInitialize_PoolStatsReflectWarmMinSize() {
        Map<String, Integer> stats = containerPoolService.getPoolStats();
        assertEquals(2, stats.get("available"));
        assertEquals(2, stats.get("total"));
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-530: acquire / release lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void testAcquire_ReturnsContainerWithDockerIdAndPort() throws Exception {
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));

        assertNotNull(handle);
        assertNotNull(handle.getId());
        assertNotNull(handle.getContainerId());
        assertTrue(handle.getPort() >= 20000);
        assertEquals("localhost", handle.getHost());
    }

    @Test
    public void testAcquire_SchedulesAsyncReplacement() throws Exception {
        containerPoolService.acquire(Duration.ofSeconds(5));

        // Give the async replacement thread a moment to start
        Thread.sleep(200);
        // One extra startDetachedContainer call expected (beyond the 2 from initialize)
        verify(containerSpawner, atLeast(3)).startDetachedContainer(anyInt());
    }

    @Test
    public void testAcquire_Timeout_ThrowsContainerAcquisitionException() throws Exception {
        // Block async replacement from refilling the pool so the 3rd acquire truly times out
        when(containerSpawner.startDetachedContainer(anyInt()))
                .thenThrow(new RuntimeException("Replacement blocked for this test"));

        // Drain all available containers (replacement will fail silently inside startNewContainer)
        containerPoolService.acquire(Duration.ofSeconds(5));
        containerPoolService.acquire(Duration.ofSeconds(5));

        // Allow any in-flight replacement threads to complete (and fail)
        Thread.sleep(300);

        // Act & Assert: pool is empty — must throw
        assertThrows(ContainerAcquisitionException.class,
                () -> containerPoolService.acquire(Duration.ofMillis(50)));
    }

    @Test
    public void testRelease_ReturnsContainerToPool() throws Exception {
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));
        int availableAfterAcquire = containerPoolService.getAvailableCount();

        containerPoolService.release(handle);

        // After release, available count increases by 1
        assertTrue(containerPoolService.getAvailableCount() > availableAfterAcquire);
    }

    @Test
    public void testRelease_SetsLastReturnedAt() throws Exception {
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));
        Instant before = Instant.now();
        containerPoolService.release(handle);
        Instant after = Instant.now();

        assertNotNull(handle.getLastReturnedAt());
        assertFalse(handle.getLastReturnedAt().isBefore(before));
        assertFalse(handle.getLastReturnedAt().isAfter(after));
    }

    @Test
    public void testRelease_Null_DoesNotThrow() {
        assertDoesNotThrow(() -> containerPoolService.release(null));
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-532: TLE discard-and-replace
    // -------------------------------------------------------------------------

    @Test
    public void testDiscardAndReplace_KillsContainerAndSchedulesReplacement() throws Exception {
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));
        String containerId = handle.getContainerId();

        containerPoolService.discardAndReplace(handle);

        verify(containerSpawner, times(1)).killContainer(containerId);
        // Give replacement thread time to start
        Thread.sleep(200);
        verify(containerSpawner, atLeast(3)).startDetachedContainer(anyInt());
    }

    @Test
    public void testDiscardAndReplace_ContainerNeverReturnsToPool() throws Exception {
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));
        int available = containerPoolService.getAvailableCount();
        containerPoolService.discardAndReplace(handle);

        // Immediately after discard, container is NOT back in the pool
        // (replacement is async — it may appear after a moment, but the original handle is gone)
        assertFalse(containerPoolService.getAvailableCount() > available + 1,
                "Discarded container must not be re-added to pool");
    }

    @Test
    public void testDiscardAndReplace_Null_DoesNotThrow() {
        assertDoesNotThrow(() -> containerPoolService.discardAndReplace(null));
    }

    // -------------------------------------------------------------------------
    // EPMICMPCOD-531: Idle TTL sweep
    // -------------------------------------------------------------------------

    @Test
    public void testIdleTtlSweep_RecyclesExpiredContainers() throws Exception {
        // Set idle TTL to 0 seconds so all idle containers are immediately stale
        ReflectionTestUtils.setField(containerPoolService, "idleTtlSeconds", 0);

        // Release a container so it enters the idle queue
        ContainerPoolService.ContainerHandle handle = containerPoolService.acquire(Duration.ofSeconds(5));
        containerPoolService.release(handle);

        // Make the container's lastReturnedAt appear old
        handle.setLastReturnedAt(Instant.now().minusSeconds(10));

        containerPoolService.idleTtlSweep();

        // Container was recycled: stopContainer called for it
        verify(containerSpawner, atLeastOnce()).stopContainer(handle.getContainerId());
        // Fresh replacement started
        verify(containerSpawner, atLeast(3)).startDetachedContainer(anyInt());
    }

    @Test
    public void testIdleTtlSweep_DoesNotRecycleNonExpiredContainers() throws Exception {
        // TTL is 300 seconds — nothing should be stale
        ReflectionTestUtils.setField(containerPoolService, "idleTtlSeconds", 300);

        containerPoolService.idleTtlSweep();

        // No containers should be stopped
        verify(containerSpawner, never()).stopContainer(anyString());
    }

    @Test
    public void testIdleTtlSweep_ActiveContainersNotTouched() throws Exception {
        // Acquire both containers (neither is in the available queue)
        ContainerPoolService.ContainerHandle h1 = containerPoolService.acquire(Duration.ofSeconds(5));
        ContainerPoolService.ContainerHandle h2 = containerPoolService.acquire(Duration.ofSeconds(5));
        Thread.sleep(100); // let async replacement add containers

        // Trigger sweep with TTL=0 — only idle pool containers should be affected
        ReflectionTestUtils.setField(containerPoolService, "idleTtlSeconds", 0);
        containerPoolService.idleTtlSweep();

        // The two acquired handles should NOT be killed
        verify(containerSpawner, never()).stopContainer(h1.getContainerId());
        verify(containerSpawner, never()).stopContainer(h2.getContainerId());
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    @Test
    public void testShutdown_StopsAllContainers() {
        containerPoolService.shutdown();

        verify(containerSpawner, atLeast(2)).stopContainer(anyString());
        assertEquals(0, containerPoolService.getAvailableCount());
    }
}
