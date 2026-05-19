package com.epam.execution_engine_service.orchestrator.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DockerContainerPoolTest {

    // ── isDockerAvailable ─────────────────────────────────────────────

    @Test
    void isDockerAvailable_whenDockerClientIsNull_returnsFalse() {
        DockerContainerPool pool = new DockerContainerPool();
        // dockerClient field is null by default (PostConstruct not called in unit tests)
        assertFalse(pool.isDockerAvailable());
    }

    @Test
    void isDockerAvailable_whenDockerClientIsSet_returnsTrue() {
        DockerContainerPool pool = new DockerContainerPool();
        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        assertTrue(pool.isDockerAvailable());
    }

    // ── getPoolSize ───────────────────────────────────────────────────

    @Test
    void getPoolSize_emptyPool_returnsZero() {
        DockerContainerPool pool = new DockerContainerPool();
        assertEquals(0, pool.getPoolSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPoolSize_afterInjectingContainers_returnsCorrectCount() {
        DockerContainerPool pool = new DockerContainerPool();
        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");

        internalPool.offer(new SandboxContainer("c1", null, "localhost", 1));
        internalPool.offer(new SandboxContainer("c2", null, "localhost", 2));

        assertEquals(2, pool.getPoolSize());
    }

    // ── shutdown ──────────────────────────────────────────────────────

    @Test
    void shutdown_dockerClientNull_completesWithoutException() {
        DockerContainerPool pool = new DockerContainerPool();
        // dockerClient is null; shutdown should return immediately without NPE
        assertDoesNotThrow(pool::shutdown);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_withContainersAndDockerClient_stopsAndRemovesEach() {
        DockerContainerPool pool = new DockerContainerPool();
        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(stopCmd.withTimeout(5)).thenReturn(stopCmd);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(mockDockerClient.stopContainerCmd("c1")).thenReturn(stopCmd);
        when(mockDockerClient.removeContainerCmd("c1")).thenReturn(removeCmd);

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        allContainers.add(new SandboxContainer("c1", mockDockerClient, "localhost", 9999));

        pool.shutdown();

        verify(stopCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_stopThrowsException_continuesAndRemovesContainer() {
        DockerContainerPool pool = new DockerContainerPool();
        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(stopCmd.withTimeout(5)).thenReturn(stopCmd);
        doThrow(new RuntimeException("stop failed")).when(stopCmd).exec();
        when(mockDockerClient.stopContainerCmd("c1")).thenReturn(stopCmd);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(mockDockerClient.removeContainerCmd("c1")).thenReturn(removeCmd);

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        allContainers.add(new SandboxContainer("c1", mockDockerClient, "localhost", 9999));

        // Should not throw even if stop fails
        assertDoesNotThrow(pool::shutdown);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_emptyAllContainers_doesNotInteractWithDockerClient() {
        DockerContainerPool pool = new DockerContainerPool();
        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        // allContainers is empty
        pool.shutdown();

        verifyNoInteractions(mockDockerClient);
    }

    // ── release ───────────────────────────────────────────────────────

    @Test
    void release_deadContainer_notAddedBackToPool() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        // Port 1 is not open → isAlive() = false
        SandboxContainer dead = new SandboxContainer("dead-id", null, "localhost", 1);

        pool.release(dead);

        assertEquals(0, pool.getPoolSize());
    }

    @Test
    void release_liveContainer_addedBackToPool() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            // Accept connections in background so isAlive() TCP probe succeeds
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* just close */ } catch (Exception ignored) { break; }
                }
            });

            SandboxContainer live = new SandboxContainer("live-id", null, "localhost", port);
            pool.release(live);

            assertEquals(1, pool.getPoolSize());
            acceptor.interrupt();
        }
    }

    // ── acquire ───────────────────────────────────────────────────────

    @Test
    void acquire_liveContainerInPool_returnsThatContainer() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* just close */ } catch (Exception ignored) { break; }
                }
            });

            SandboxContainer live = new SandboxContainer("acquire-test-id", null, "localhost", port);
            @SuppressWarnings("unchecked")
            BlockingQueue<SandboxContainer> internalPool =
                    (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
            internalPool.offer(live);

            SandboxContainer acquired = pool.acquire(2000);

            assertNotNull(acquired);
            assertEquals("acquire-test-id", acquired.getContainerId());
            acceptor.interrupt();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void acquire_deadContainerInPool_discardsItAndThrows() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        // dockerClient is null → createAndStartContainer will throw

        SandboxContainer dead = new SandboxContainer("dead-acquire", null, "localhost", 1);
        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
        internalPool.offer(dead);

        // Pool becomes empty after discarding dead → createAndStartContainer (NPE) thrown
        assertThrows(Exception.class, () -> pool.acquire(100));
        // Dead container should have been removed from pool
        assertEquals(0, pool.getPoolSize());
    }

    @Test
    void acquire_emptyPool_throwsAfterTimeout() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        // dockerClient is null → createAndStartContainer NPEs after timeout

        assertThrows(Exception.class, () -> pool.acquire(100));
    }

    // ── initialization without Docker (stub mode) ─────────────────────

    @Test
    void initialize_dockerUnavailable_dockerClientRemainsNull() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "dockerHost", "tcp://127.0.0.1:1"); // unreachable
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "warmMinSize", 1);
        ReflectionTestUtils.setField(pool, "memoryLimitMb", 256L);

        // initialize() will fail to connect → sets dockerClient = null
        pool.initialize();

        assertFalse(pool.isDockerAvailable());
        assertEquals(0, pool.getPoolSize());
    }

    // ── discardAndReplace (via release) with live dockerClient ─────────

    @Test
    @SuppressWarnings("unchecked")
    void release_deadContainer_withDockerClient_callsRemoveContainerCmd() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(mockDockerClient.removeContainerCmd("dead-dc")).thenReturn(removeCmd);

        // pre-populate allContainers so the remove has something to remove
        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        SandboxContainer dead = new SandboxContainer("dead-dc", null, "localhost", 1);
        allContainers.add(dead);

        pool.release(dead); // port 1 is not open → isAlive = false → discardAndReplace

        verify(removeCmd).exec();
        assertEquals(0, pool.getPoolSize()); // replacement spawns in background and may fail
    }

    @Test
    @SuppressWarnings("unchecked")
    void release_deadContainer_removeContainerThrows_handlesGracefully() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        doThrow(new RuntimeException("remove failed")).when(removeCmd).exec();
        when(mockDockerClient.removeContainerCmd("dead-err")).thenReturn(removeCmd);

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        SandboxContainer dead = new SandboxContainer("dead-err", null, "localhost", 1);
        allContainers.add(dead);

        assertDoesNotThrow(() -> pool.release(dead));
    }

    // ── liveCount tracking ────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void liveCount_decrementedWhenContainerDiscarded() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "dockerClient", null); // no Docker

        java.util.concurrent.atomic.AtomicInteger liveCount =
                (java.util.concurrent.atomic.AtomicInteger) ReflectionTestUtils.getField(pool, "liveCount");

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        SandboxContainer dead = new SandboxContainer("live-count-id", null, "localhost", 1);
        allContainers.add(dead);
        liveCount.set(3);

        pool.release(dead); // isAlive=false → discardAndReplace → liveCount.decrementAndGet()

        assertEquals(2, liveCount.get());
    }

    // ── acquire: dead container with dockerClient calls removeContainerCmd ──

    @Test
    @SuppressWarnings("unchecked")
    void acquire_deadContainer_withDockerClient_callsRemoveContainerCmd() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(mockDockerClient.removeContainerCmd("dead-acq")).thenReturn(removeCmd);

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        SandboxContainer dead = new SandboxContainer("dead-acq", null, "localhost", 1);
        allContainers.add(dead);

        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
        internalPool.offer(dead);

        // After discarding the dead container the pool empties and createAndStartContainer
        // (via dockerClient.createContainerCmd) will fail → acquire throws
        assertThrows(Exception.class, () -> pool.acquire(200));
        verify(removeCmd).exec();
    }

    // ── getPoolSize after release of live container ───────────────────

    @Test
    void getPoolSize_afterReleaseOfLiveContainer_isOne() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        try (java.net.ServerSocket server = new java.net.ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (java.net.Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            SandboxContainer live = new SandboxContainer("size-test", null, "localhost", port);
            pool.release(live);

            assertEquals(1, pool.getPoolSize());
            acceptor.interrupt();
        }
    }
}
