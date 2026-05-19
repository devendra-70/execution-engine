package com.epam.execution_engine_service.orchestrator.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ── initialize() happy path via MockedStatic ─────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void initialize_dockerAvailable_warmMinSizeOne_spawnsContainerAndSetsClient() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "dockerHost", "tcp://127.0.0.1:1");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "warmMinSize", 1);
        ReflectionTestUtils.setField(pool, "memoryLimitMb", 256L);
        ReflectionTestUtils.setField(pool, "readyPollMs", 50);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 10);

        DockerClient mockDockerClient = mock(DockerClient.class);
        PingCmd pingCmd = mock(PingCmd.class);
        when(mockDockerClient.pingCmd()).thenReturn(pingCmd);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
            bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[]{ Ports.Binding.bindPort(port) });
            setupDockerChainMocks(mockDockerClient, "init-id", bindingsMap);

            try (MockedStatic<DockerClientImpl> mockedStatic = mockStatic(DockerClientImpl.class)) {
                mockedStatic.when(() -> DockerClientImpl.getInstance(any(), any())).thenReturn(mockDockerClient);
                pool.initialize();
            }

            assertTrue(pool.isDockerAvailable());
            assertEquals(1, pool.getPoolSize());
            List<SandboxContainer> allContainers =
                    (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
            assertEquals(1, allContainers.size());
        }
    }

    // ── spawnAndRegisterContainer — pool full warn branch ───────────────

    @Test
    @SuppressWarnings("unchecked")
    void spawnAndRegisterContainer_poolFull_logsWarnAndTracksInAllContainers() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "readyPollMs", 50);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 10);

        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
        for (int i = 0; i < 200; i++) {
            internalPool.offer(new SandboxContainer("filler-" + i, null, "localhost", 1));
        }

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            DockerClient mockDockerClient = mock(DockerClient.class);
            ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

            Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
            bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[]{ Ports.Binding.bindPort(port) });
            setupDockerChainMocks(mockDockerClient, "pool-full-id", bindingsMap);

            int result = (int) ReflectionTestUtils.invokeMethod(pool, "spawnAndRegisterContainer", 0, 1);

            assertEquals(1, result); // still returns 1 even though pool was full
            // Container tracked in allContainers but pool size unchanged
            List<SandboxContainer> allContainers =
                    (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
            assertEquals(1, allContainers.size());
            assertEquals(200, pool.getPoolSize());
        }
    }

    // ── spawnAndRegisterContainer — exception catch branch ──────────────

    @Test
    void spawnAndRegisterContainer_dockerThrows_returnsZero() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, Answers.RETURNS_SELF);
        doThrow(new RuntimeException("Docker create failed")).when(createCmd).exec();
        when(mockDockerClient.createContainerCmd(anyString())).thenReturn(createCmd);

        int result = (int) ReflectionTestUtils.invokeMethod(pool, "spawnAndRegisterContainer", 0, 1);

        assertEquals(0, result);
        assertEquals(0, pool.getPoolSize());
    }

    // ── discardAndReplace background thread — success path ──────────────

    @Test
    @SuppressWarnings("unchecked")
    void discardAndReplace_backgroundThread_addsReplacementToPool() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "readyPollMs", 50);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 10);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            DockerClient mockDockerClient = mock(DockerClient.class);
            ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

            // Mock removal of the dead container
            RemoveContainerCmd deadRemove = mock(RemoveContainerCmd.class);
            when(deadRemove.withForce(true)).thenReturn(deadRemove);
            when(mockDockerClient.removeContainerCmd("dead-bg")).thenReturn(deadRemove);

            Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
            bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[]{ Ports.Binding.bindPort(port) });
            setupDockerChainMocks(mockDockerClient, "replacement-bg", bindingsMap);

            List<SandboxContainer> allContainers =
                    (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
            SandboxContainer dead = new SandboxContainer("dead-bg", null, "localhost", 1);
            allContainers.add(dead);
            AtomicInteger liveCount = (AtomicInteger) ReflectionTestUtils.getField(pool, "liveCount");
            liveCount.set(1);

            pool.release(dead); // isAlive=false → discardAndReplace → bg thread spawns replacement

            // Poll until replacement is added to pool (bg virtual thread completes quickly)
            long deadline = System.currentTimeMillis() + 3000;
            while (pool.getPoolSize() == 0 && System.currentTimeMillis() < deadline) {
                LockSupport.parkNanos(50_000_000L);
            }

            assertEquals(1, pool.getPoolSize());
            server.close();
        }
    }

    // ── discardAndReplace background thread — pool full (warn branch) ─────

    @Test
    @SuppressWarnings("unchecked")
    void discardAndReplace_backgroundThread_poolFull_logsWarnAndDoesNotThrow() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "readyPollMs", 50);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 10);

        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
        for (int i = 0; i < 200; i++) {
            internalPool.offer(new SandboxContainer("fill-" + i, null, "localhost", 1));
        }

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            DockerClient mockDockerClient = mock(DockerClient.class);
            ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

            RemoveContainerCmd deadRemove = mock(RemoveContainerCmd.class);
            when(deadRemove.withForce(true)).thenReturn(deadRemove);
            when(mockDockerClient.removeContainerCmd("dead-overflow")).thenReturn(deadRemove);

            Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
            bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[]{ Ports.Binding.bindPort(port) });
            setupDockerChainMocks(mockDockerClient, "overflow-replacement", bindingsMap);

            List<SandboxContainer> allContainers =
                    (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
            SandboxContainer dead = new SandboxContainer("dead-overflow", null, "localhost", 1);
            allContainers.add(dead);
            AtomicInteger liveCount = (AtomicInteger) ReflectionTestUtils.getField(pool, "liveCount");
            liveCount.set(1);

            assertDoesNotThrow(() -> pool.release(dead));

            // Poll until bg thread increments liveCount back to 1 (signal it has completed)
            long deadline = System.currentTimeMillis() + 3000;
            while (liveCount.get() == 0 && System.currentTimeMillis() < deadline) {
                LockSupport.parkNanos(50_000_000L);
            }

            assertEquals(200, pool.getPoolSize()); // pool did not grow
            server.close();
        }
    }

    // ── waitForSandboxReady — retry catch branch (one failed then success) ───

    @Test
    void waitForSandboxReady_firstAttemptFails_retriesThenSucceeds() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "readyPollMs", 250);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 5);

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        // Reserve a free port and immediately release it so the first connect attempt fails
        final int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        } // port is now free — first connect will get Connection refused

        // After 200ms, open a server on that same port (well within the 500ms retry sleep)
        Thread.ofVirtual().start(() -> {
            try {
                LockSupport.parkNanos(200_000_000L);
                try (ServerSocket server = new ServerSocket(port)) {
                    while (!server.isClosed()) {
                        try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                    }
                }
            } catch (Exception ignored) {
                // virtual thread interrupted or server could not bind during test teardown
            }
        });

        // First attempt: connection refused (catch block hit, Thread.sleep called)
        // After 500ms sleep: server is listening, second attempt succeeds
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                pool, "waitForSandboxReady", port, "retry-probe-id"));
    }

    // ── waitForSandboxReady — all attempts exhausted, removeContainer succeeds ──

    @Test
    void waitForSandboxReady_allAttemptsExhausted_removesContainerAndThrows() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        // Use port 1 — guaranteed connection refused; set tiny poll/attempt values
        ReflectionTestUtils.setField(pool, "readyPollMs", 1);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 2);

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(mockDockerClient.removeContainerCmd("exhausted-id")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        Exception ex = assertThrows(Exception.class, () ->
                ReflectionTestUtils.invokeMethod(pool, "waitForSandboxReady", 1, "exhausted-id"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("exhausted-id"));
        verify(removeCmd).exec();
    }

    // ── waitForSandboxReady — all attempts exhausted, removeContainer throws ──

    @Test
    void waitForSandboxReady_allAttemptsExhausted_removeThrows_stillThrowsIllegalState() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "readyPollMs", 1);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 2);

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(mockDockerClient.removeContainerCmd("remove-throws-id")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        doThrow(new RuntimeException("docker error")).when(removeCmd).exec();

        // Should still throw IllegalStateException even if removeContainerCmd fails
        Exception ex = assertThrows(Exception.class, () ->
                ReflectionTestUtils.invokeMethod(pool, "waitForSandboxReady", 1, "remove-throws-id"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("remove-throws-id"));
    }

    // ── waitForSandboxReady — every-5th-attempt debug log branch ─────────

    @Test
    void waitForSandboxReady_fiveAttemptsExhausted_coversEvery5thLogBranch() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "readyPollMs", 1);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 5);

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(mockDockerClient.removeContainerCmd("log5-id")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        // 5 attempts hit the `attempt % 5 == 0` log branch (attempt=5), then exhaustion throw
        assertThrows(Exception.class, () ->
                ReflectionTestUtils.invokeMethod(pool, "waitForSandboxReady", 1, "log5-id"));
        verify(removeCmd).exec();
    }

    // ── Docker-mocked createAndStartContainer — null port bindings ─────

    @Test
    void createAndStartContainer_nullPortBindings_callsRemoveAndThrowsIllegalState() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        // Empty map → get(containerPort) returns null → null bindings branch
        Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
        RemoveContainerCmd removeCmd = setupDockerChainMocks(mockDockerClient, "null-bind-id", bindingsMap);

        // acquire(0) skips the pool-poll loop entirely and calls createAndStartContainer() directly
        assertThrows(Exception.class, () -> pool.acquire(0));
        verify(removeCmd).exec();
    }

    // ── Docker-mocked createAndStartContainer — empty port bindings array ─

    @Test
    void createAndStartContainer_emptyPortBindingsArray_callsRemoveAndThrowsIllegalState() {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");

        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        // Present key but empty array → bindings.length == 0 branch
        Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
        bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[0]);
        RemoveContainerCmd removeCmd = setupDockerChainMocks(mockDockerClient, "empty-bind-id", bindingsMap);

        assertThrows(Exception.class, () -> pool.acquire(0));
        verify(removeCmd).exec();
    }

    // ── Docker-mocked acquire — on-demand spawn full success path ─────────

    @Test
    @SuppressWarnings("unchecked")
    void acquire_onDemandSpawn_fullSuccessPath_returnsContainer() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");
        ReflectionTestUtils.setField(pool, "sandboxImage", "test-image");
        ReflectionTestUtils.setField(pool, "readyPollMs", 50);
        ReflectionTestUtils.setField(pool, "readyMaxAttempts", 10);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            // Accept connections so waitForSandboxReady TCP probe succeeds
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            DockerClient mockDockerClient = mock(DockerClient.class);
            ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

            Map<ExposedPort, Ports.Binding[]> bindingsMap = new HashMap<>();
            bindingsMap.put(ExposedPort.tcp(5000), new Ports.Binding[]{ Ports.Binding.bindPort(port) });
            setupDockerChainMocks(mockDockerClient, "ondemand-id", bindingsMap);

            // acquire(0) bypasses the loop, spawns on-demand via createAndStartContainer()
            SandboxContainer result = pool.acquire(0);

            assertNotNull(result);
            assertEquals("ondemand-id", result.getContainerId());
            assertEquals(port, result.getSandboxPort());

            // verify liveCount and allContainers were updated
            List<SandboxContainer> allContainers =
                    (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
            assertEquals(1, allContainers.size());

            AtomicInteger liveCount =
                    (AtomicInteger) ReflectionTestUtils.getField(pool, "liveCount");
            assertEquals(1, liveCount.get());

            server.close();
            acceptor.interrupt();
        }
    }

    // ── release — pool at maximum capacity ────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void release_liveContainer_poolAtMaxCapacity_doesNotThrow() throws Exception {
        DockerContainerPool pool = new DockerContainerPool();
        ReflectionTestUtils.setField(pool, "sandboxHost", "localhost");

        BlockingQueue<SandboxContainer> internalPool =
                (BlockingQueue<SandboxContainer>) ReflectionTestUtils.getField(pool, "pool");
        // Fill pool to its hard cap of 200
        for (int i = 0; i < 200; i++) {
            internalPool.offer(new SandboxContainer("dummy-" + i, null, "localhost", 1));
        }
        assertEquals(200, pool.getPoolSize());

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) { /* close */ } catch (Exception ignored) { break; }
                }
            });

            SandboxContainer live = new SandboxContainer("live-overflow", null, "localhost", port);
            // pool.offer(live) returns false (full) — should log warning and not throw
            assertDoesNotThrow(() -> pool.release(live));
            // Pool size stays at 200 since the offer was rejected
            assertEquals(200, pool.getPoolSize());

            server.close();
            acceptor.interrupt();
        }
    }

    // ── shutdown — stop succeeds but removeCmd throws ─────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_stopSucceeds_removeCmdThrows_doesNotThrow() {
        DockerContainerPool pool = new DockerContainerPool();
        DockerClient mockDockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(pool, "dockerClient", mockDockerClient);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(stopCmd.withTimeout(5)).thenReturn(stopCmd);
        // stop succeeds (exec() returns normally)
        when(mockDockerClient.stopContainerCmd("c-remove-fail")).thenReturn(stopCmd);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        doThrow(new RuntimeException("remove failed")).when(removeCmd).exec();
        when(mockDockerClient.removeContainerCmd("c-remove-fail")).thenReturn(removeCmd);

        List<SandboxContainer> allContainers =
                (List<SandboxContainer>) ReflectionTestUtils.getField(pool, "allContainers");
        allContainers.add(new SandboxContainer("c-remove-fail", null, "localhost", 9999));

        assertDoesNotThrow(pool::shutdown);
        verify(stopCmd).exec();
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

    // ── Helper: mock the full Docker command chain ────────────────────────

    /**
     * Wires up the createContainerCmd → startContainerCmd → inspectContainerCmd chain
     * on the given dockerClient mock, using the supplied bindings map.
     * Returns the RemoveContainerCmd mock so callers can verify force-removal.
     */
    private RemoveContainerCmd setupDockerChainMocks(
            DockerClient dockerClient,
            String containerId,
            Map<ExposedPort, Ports.Binding[]> bindingsMap) {

        // createContainerCmd — RETURNS_SELF handles all .withXxx() chaining automatically
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, Answers.RETURNS_SELF);
        CreateContainerResponse createResponse = mock(CreateContainerResponse.class);
        when(createResponse.getId()).thenReturn(containerId);
        doReturn(createResponse).when(createCmd).exec();
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);

        // startContainerCmd
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startCmd);

        // inspectContainerCmd
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        NetworkSettings networkSettings = mock(NetworkSettings.class);
        Ports ports = mock(Ports.class);
        when(ports.getBindings()).thenReturn(bindingsMap);
        when(networkSettings.getPorts()).thenReturn(ports);
        when(inspectResponse.getNetworkSettings()).thenReturn(networkSettings);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);

        // removeContainerCmd — used when bindings are invalid or readiness times out
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(dockerClient.removeContainerCmd(containerId)).thenReturn(removeCmd);

        return removeCmd;
    }
}
