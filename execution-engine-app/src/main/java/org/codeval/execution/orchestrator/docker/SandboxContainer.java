package org.codeval.execution.orchestrator.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.codeval.execution.domain.TestCase;
import org.codeval.execution.domain.TestCaseResultEvent;
import org.codeval.execution.domain.Verdict;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SandboxContainer {

    @Getter
    private final String containerId;
    private final DockerClient dockerClient;
    private boolean healthy = true;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SandboxContainer(String containerId, DockerClient dockerClient) {
        this.containerId = containerId;
        this.dockerClient = dockerClient;
    }

    /**
     * Execute source code against test cases inside this container.
     * The sandbox wrapper is invoked via docker exec.
     */
    public List<TestCaseResultEvent> execute(String sourceCode, List<TestCase> testCases, long timeoutMs) {
        try {
            // Build request payload for sandbox
            SandboxRequest request = new SandboxRequest(sourceCode, testCases, timeoutMs);
            String requestJson = objectMapper.writeValueAsString(request);

            // Write request via docker exec (stdin)
            ExecCreateCmdResponse execCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-c",
                            "echo '" + requestJson.replace("'", "'\"'\"'") + "' | java -jar /app/sandbox-wrapper.jar")
                    .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execCmd.getId())
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion(timeoutMs + 5000, TimeUnit.MILLISECONDS);

            String output = stdout.toString().trim();
            if (output.isEmpty()) {
                log.error("Sandbox stderr: {}", stderr);
                healthy = false;
                return buildErrorResult(testCases, Verdict.RUNTIME_ERROR, "Sandbox returned no output");
            }

            SandboxResponse response = objectMapper.readValue(output, SandboxResponse.class);
            return response.results();

        } catch (Exception e) {
            log.error("Execution in container {} failed", containerId, e);
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


