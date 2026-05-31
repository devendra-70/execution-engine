package com.epam.execution_engine_service.orchestrator.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.epam.execution_engine_service.domain.TestCase;
import com.epam.execution_engine_service.domain.TestCaseResultEvent;
import com.epam.execution_engine_service.domain.Verdict;


import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

/**
 * DIP: ObjectMapper is injected via constructor rather than created as a static field,
 * allowing the shared Spring-managed instance (with all modules registered) to be used.
 */
@Slf4j
public class SandboxContainer {

    @Getter
    private final String containerId;
    private final DockerClient dockerClient;
    private final String sandboxHost;
    @Getter
    private final int sandboxPort;
    private final ObjectMapper objectMapper;
    private boolean healthy = true;

    public SandboxContainer(String containerId, DockerClient dockerClient,
                            String sandboxHost, int sandboxPort, ObjectMapper objectMapper) {
        this.containerId  = containerId;
        this.dockerClient = dockerClient;
        this.sandboxHost  = sandboxHost;
        this.sandboxPort  = sandboxPort;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute source code against test cases via TCP socket to the sandbox wrapper.
     */
    public List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) {
        try {
            SandboxRequest request = new SandboxRequest(sourceCode, testCases, timeoutMs);
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);

            try (Socket socket = new Socket(sandboxHost, sandboxPort)) {
                socket.setSoTimeout((int) (timeoutMs + 10_000));

                OutputStream out = socket.getOutputStream();
                out.write(requestBytes);
                out.flush();
                socket.shutdownOutput();

                byte[] responseBytes = socket.getInputStream().readAllBytes();
                SandboxResponse response = objectMapper.readValue(responseBytes, SandboxResponse.class);
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
