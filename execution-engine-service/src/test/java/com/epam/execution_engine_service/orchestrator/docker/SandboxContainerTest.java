package com.epam.execution_engine_service.orchestrator.docker;

import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.domain.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Slf4j
class SandboxContainerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private List<TestCase> singleTestCase() {
        return List.of(TestCase.builder().id(1L).problemId(100L).input("1").expectedOutput("1").timeoutMs(1000).build());
    }

    // ── happy-path execution ──────────────────────────────────────────

    @Test
    void execute_successfulResponse_returnsResults() throws Exception {
        List<TestCaseResultEvent> expected = List.of(
                TestCaseResultEvent.builder()
                        .testCaseId(1L).verdict(Verdict.ACCEPTED)
                        .actualOutput("42").expectedOutput("42")
                        .runtimeMs(10L).memoryBytes(512L)
                        .build()
        );
        SandboxContainer.SandboxResponse response = new SandboxContainer.SandboxResponse(expected);
        byte[] responseBytes = MAPPER.writeValueAsBytes(response);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket client = server.accept()) {
                    client.getInputStream().readAllBytes();   // consume the request
                    client.getOutputStream().write(responseBytes);
                    client.getOutputStream().flush();
                } catch (IOException e) {
                    log.debug("Server socket error (expected in test)", e);
                }
            });

            SandboxContainer container = new SandboxContainer("test-id", mock(DockerClient.class), "localhost", port);
            List<TestCaseResultEvent> results = container.execute("source", singleTestCase(), 5000L);

            assertEquals(1, results.size());
            assertEquals(Verdict.ACCEPTED, results.get(0).getVerdict());
            assertEquals("42", results.get(0).getActualOutput());
            assertEquals(10L, results.get(0).getRuntimeMs());
            assertEquals(512L, results.get(0).getMemoryBytes());
            assertTrue(container.isHealthy());

            serverThread.join(3000);
        }
    }

    @Test
    void execute_multipleResults_allReturned() throws Exception {
        List<TestCaseResultEvent> expected = List.of(
                TestCaseResultEvent.builder().testCaseId(1L).verdict(Verdict.ACCEPTED).runtimeMs(10L).memoryBytes(256L).build(),
                TestCaseResultEvent.builder().testCaseId(2L).verdict(Verdict.WRONG_ANSWER).runtimeMs(20L).memoryBytes(512L).build(),
                TestCaseResultEvent.builder().testCaseId(3L).verdict(Verdict.RUNTIME_ERROR).errorMessage("NPE").runtimeMs(5L).memoryBytes(128L).build()
        );
        byte[] responseBytes = MAPPER.writeValueAsBytes(new SandboxContainer.SandboxResponse(expected));

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket client = server.accept()) {
                    client.getInputStream().readAllBytes();
                    client.getOutputStream().write(responseBytes);
                    client.getOutputStream().flush();
                } catch (IOException e) {
                    log.debug("Server socket error (expected in test)", e);
                }
            });

            List<TestCase> testCases = List.of(
                    TestCase.builder().id(1L).input("a").expectedOutput("a").timeoutMs(500).build(),
                    TestCase.builder().id(2L).input("b").expectedOutput("b").timeoutMs(500).build(),
                    TestCase.builder().id(3L).input("c").expectedOutput("c").timeoutMs(500).build()
            );

            SandboxContainer container = new SandboxContainer("multi-id", mock(DockerClient.class), "localhost", port);
            List<TestCaseResultEvent> results = container.execute("src", testCases, 5000L);

            assertEquals(3, results.size());
            assertEquals(Verdict.ACCEPTED, results.get(0).getVerdict());
            assertEquals(Verdict.WRONG_ANSWER, results.get(1).getVerdict());
            assertEquals(Verdict.RUNTIME_ERROR, results.get(2).getVerdict());
            assertEquals("NPE", results.get(2).getErrorMessage());
            assertTrue(container.isHealthy());

            serverThread.join(3000);
        }
    }

    @Test
    void execute_serverClosesConnectionImmediately_returnsRuntimeErrorAndMarksUnhealthy() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket client = server.accept()) {
                    // Close immediately without writing any response
                } catch (IOException e) {
                    log.debug("Server socket error (expected in test)", e);
                }
            });

            SandboxContainer container = new SandboxContainer("close-id", mock(DockerClient.class), "localhost", port);
            List<TestCaseResultEvent> results = container.execute("src", singleTestCase(), 2000L);

            // Empty / invalid bytes → Jackson parse error → error result returned
            assertFalse(container.isHealthy());
            assertEquals(1, results.size());
            assertEquals(Verdict.RUNTIME_ERROR, results.get(0).getVerdict());
            assertEquals(1L, results.get(0).getTestCaseId());

            serverThread.join(2000);
        }
    }

    // ── error path: port not open ─────────────────────────────────────

    @Test
    void execute_portNotOpen_returnsRuntimeErrorResult() {
        // Port 1 is never open; socket connection will fail
        SandboxContainer container = new SandboxContainer("error-id", mock(DockerClient.class), "localhost", 1);

        List<TestCaseResultEvent> results = container.execute("source", singleTestCase(), 2000L);

        assertEquals(1, results.size());
        assertEquals(Verdict.RUNTIME_ERROR, results.get(0).getVerdict());
        assertEquals(1L, results.get(0).getTestCaseId());
        assertNotNull(results.get(0).getErrorMessage());
    }

    @Test
    void execute_portNotOpen_marksContainerUnhealthy() {
        SandboxContainer container = new SandboxContainer("unhealthy-id", mock(DockerClient.class), "localhost", 1);

        container.execute("source", singleTestCase(), 500L);

        assertFalse(container.isHealthy());
    }

    @Test
    void execute_multipleTestCases_portNotOpen_allGetRuntimeError() {
        SandboxContainer container = new SandboxContainer("multi-error", mock(DockerClient.class), "localhost", 1);
        List<TestCase> testCases = List.of(
                TestCase.builder().id(10L).build(),
                TestCase.builder().id(20L).build(),
                TestCase.builder().id(30L).build()
        );

        List<TestCaseResultEvent> results = container.execute("src", testCases, 500L);

        assertEquals(3, results.size());
        results.forEach(r -> assertEquals(Verdict.RUNTIME_ERROR, r.getVerdict()));
        assertEquals(10L, results.get(0).getTestCaseId());
        assertEquals(20L, results.get(1).getTestCaseId());
        assertEquals(30L, results.get(2).getTestCaseId());
    }

    @Test
    void execute_emptyTestCaseList_portNotOpen_returnsEmptyList() {
        SandboxContainer container = new SandboxContainer("empty-tc", mock(DockerClient.class), "localhost", 1);

        List<TestCaseResultEvent> results = container.execute("src", List.of(), 500L);

        assertTrue(results.isEmpty());
        assertFalse(container.isHealthy());
    }

    // ── isHealthy ─────────────────────────────────────────────────────

    @Test
    void isHealthy_newContainer_returnsTrue() {
        SandboxContainer container = new SandboxContainer("healthy-id", mock(DockerClient.class), "localhost", 9999);
        assertTrue(container.isHealthy());
    }

    @Test
    void isHealthy_afterFailedExecute_returnsFalse() {
        SandboxContainer container = new SandboxContainer("fail-id", mock(DockerClient.class), "localhost", 1);
        container.execute("src", singleTestCase(), 200L);
        assertFalse(container.isHealthy());
    }

    // ── getContainerId / getSandboxPort ───────────────────────────────

    @Test
    void getContainerId_returnsConstructorValue() {
        SandboxContainer container = new SandboxContainer("my-container-id", null, "localhost", 5000);
        assertEquals("my-container-id", container.getContainerId());
    }

    @Test
    void getSandboxPort_returnsConstructorValue() {
        SandboxContainer container = new SandboxContainer("any-id", null, "localhost", 12345);
        assertEquals(12345, container.getSandboxPort());
    }

    // ── SandboxRequest record ─────────────────────────────────────────

    @Test
    void sandboxRequest_record_fieldsAccessible() {
        List<TestCase> tcs = singleTestCase();
        SandboxContainer.SandboxRequest req = new SandboxContainer.SandboxRequest("code", tcs, 3000L);

        assertEquals("code", req.sourceCode());
        assertEquals(tcs, req.testCases());
        assertEquals(3000L, req.timeoutMs());
    }

    // ── SandboxResponse record ────────────────────────────────────────

    @Test
    void sandboxResponse_record_fieldsAccessible() {
        List<TestCaseResultEvent> results = List.of(
                TestCaseResultEvent.builder().testCaseId(1L).verdict(Verdict.ACCEPTED).build()
        );
        SandboxContainer.SandboxResponse resp = new SandboxContainer.SandboxResponse(results);

        assertEquals(1, resp.results().size());
        assertEquals(Verdict.ACCEPTED, resp.results().get(0).getVerdict());
    }
}
