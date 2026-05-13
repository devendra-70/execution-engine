package org.codeval.execution.orchestrator.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dockerjava.api.DockerClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.codeval.execution.domain.TestCase;
import org.codeval.execution.domain.TestCaseResultEvent;
import org.codeval.execution.domain.Verdict;

import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

@Slf4j
public class SandboxContainer {

    @Getter
    private final String containerId;
    private final DockerClient dockerClient;
    private final String sandboxHost;
    @Getter
    private final int sandboxPort;
    private boolean healthy = true;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public SandboxContainer(String containerId, DockerClient dockerClient,
                            String sandboxHost, int sandboxPort) {
        this.containerId = containerId;
        this.dockerClient = dockerClient;
        this.sandboxHost = sandboxHost;
        this.sandboxPort = sandboxPort;
    }

    /**
     * Execute source code against test cases via TCP socket to the sandbox wrapper.
     * The sandbox compiles once, then runs each test case with a fresh ClassLoader.
     */
    public List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) {
        try {
            SandboxRequest request = new SandboxRequest(sourceCode, testCases, timeoutMs);
            byte[] requestBytes = MAPPER.writeValueAsBytes(request);

            try (Socket socket = new Socket(sandboxHost, sandboxPort)) {
                socket.setSoTimeout((int) (timeoutMs + 10_000));

                // Send request, signal EOF
                OutputStream out = socket.getOutputStream();
                out.write(requestBytes);
                out.flush();
                socket.shutdownOutput();

                // Read response until EOF
                byte[] responseBytes = socket.getInputStream().readAllBytes();
                SandboxResponse response = MAPPER.readValue(responseBytes, SandboxResponse.class);
                return response.results();
            }
        } catch (Exception e) {
            log.error("Socket execution in container {} failed", containerId, e);
            healthy = false;
            return buildErrorResult(testCases, Verdict.RUNTIME_ERROR, e.getMessage());
        }
    }

    private List<TestCaseResultEvent> buildErrorResult(List<TestCase> testCases, Verdict verdict, String error) {
        return testCases.stream()
                .map(tc -> TestCaseResultEvent.builder()
                        .testCaseId(tc.getId())
                        .verdict(verdict)
                        .errorMessage(error)
                        .build())
                .toList();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public record SandboxRequest(String sourceCode, List<TestCase> testCases, long timeoutMs) {}
    public record SandboxResponse(List<TestCaseResultEvent> results) {}
}

