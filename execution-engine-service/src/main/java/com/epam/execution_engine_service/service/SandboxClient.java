package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.dto.TestCaseResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SandboxClient — Socket communication with sandbox wrapper (SRS §6.2, §10)
 * 
 * Implements the wire protocol via line-delimited JSON frames:
 * 1. Connect to sandbox container via socket
 * 2. Send source code (compile once)
 * 3. Feed test cases iteratively  
 * 4. Collect results
 * 5. Close connection
 * 
 * Frame Format (SRS §6.2, sandbox protocol):
 * 
 * Frames sent by Orchestrator:
 *   {"type":"source",   "executionId":"...","className":"Solution","sourceCode":"..."}
 *   {"type":"testcase", "id":"tc1","stdin":"...","timeoutMs":3000}
 *   {"type":"end",      "executionId":"..."}
 * 
 * Frames received from Wrapper:
 *   {"type":"compiled",       "executionId":"..."}
 *   {"type":"compile_error",  "executionId":"...","message":"..."}
 *   {"type":"result",         "id":"tc1","status":"OK|RUNTIME_ERROR|TIME_LIMIT_EXCEEDED",
 *                             "stdout":"...","stderr":"...","runtimeMs":12}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SandboxClient {

    private final ObjectMapper objectMapper;

    private static final int SOCKET_CONNECT_RETRIES = 3;
    private static final int SOCKET_INITIAL_BACKOFF_MS = 500;
    private static final int SOCKET_MAX_BACKOFF_MS = 5000;
    private static final int SOCKET_READ_TIMEOUT_MS = 5000;

    /**
     * Execute source code against test cases via sandbox socket
     * 
     * @param containerHost Sandbox container hostname/IP
     * @param containerPort Sandbox container port (typically 9999)
     * @param executionId Unique execution identifier
     * @param className Compiled class name (e.g., "Solution")
     * @param sourceCode Java source code to execute
     * @param testCases Test cases to run
     * @return List of TestCaseResultDto with results
     */
    public List<TestCaseResultDto> executeViaSocket(
            String containerHost,
            int containerPort,
            UUID executionId,
            String className,
            String sourceCode,
            List<SandboxTestCase> testCases) {

        List<TestCaseResultDto> results = new ArrayList<>();

        Socket socket = null;
        try {
            // Connect with retry logic
            socket = connectWithRetry(containerHost, containerPort, executionId);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            log.info("Connected to sandbox: {}:{}, executionId={}", containerHost, containerPort, executionId);

            // Step 1: Send source code
            Map<String, Object> sourceFrame = new HashMap<>();
            sourceFrame.put("type", "source");
            sourceFrame.put("executionId", executionId.toString());
            sourceFrame.put("className", className);
            sourceFrame.put("sourceCode", sourceCode);
            
            String sourceJson = objectMapper.writeValueAsString(sourceFrame);
            out.println(sourceJson);
            log.debug("Sent source frame: executionId={}", executionId);

            // Step 2: Wait for compilation response
            String compiledLine = in.readLine();
            if (compiledLine == null) {
                throw new IOException("No response from sandbox after sending source");
            }

            Map<String, Object> compiled = objectMapper.readValue(compiledLine, Map.class);
            String frameType = (String) compiled.get("type");

            if ("compile_error".equals(frameType)) {
                String compileError = (String) compiled.get("message");
                log.error("Compile error: {}", compileError);
                
                // Return single result indicating compilation failed
                TestCaseResultDto errorResult = TestCaseResultDto.builder()
                        .testCaseId(-1L)
                        .status("COMPILE_ERROR")
                        .actualOutput(compileError)
                        .expectedOutput("")
                        .executionTimeMs(0L)
                        .memoryBytes(0L)
                        .build();
                results.add(errorResult);
                return results;
            }

            if (!"compiled".equals(frameType)) {
                throw new IOException("Unexpected response type: " + frameType);
            }

            log.debug("Source compiled successfully: executionId={}", executionId);

            // Step 3: Send test cases and collect results
            for (SandboxTestCase testCase : testCases) {
                Map<String, Object> tcFrame = new HashMap<>();
                tcFrame.put("type", "testcase");
                tcFrame.put("id", testCase.getId());
                tcFrame.put("stdin", testCase.getInput());
                tcFrame.put("timeoutMs", testCase.getTimeoutMs());

                String tcJson = objectMapper.writeValueAsString(tcFrame);
                out.println(tcJson);
                log.debug("Sent test case: id={}", testCase.getId());

                // Receive result
                String resultLine = in.readLine();
                if (resultLine == null) {
                    throw new IOException("No response from sandbox for test case: " + testCase.getId());
                }

                Map<String, Object> resultFrame = objectMapper.readValue(resultLine, Map.class);
                if ("result".equals(resultFrame.get("type"))) {
                    TestCaseResultDto result = parseResultFrame(resultFrame);
                    results.add(result);
                    log.debug("Test case result: id={}, status={}", testCase.getId(), result.getStatus());
                } else {
                    throw new IOException("Unexpected frame type for result: " + resultFrame.get("type"));
                }
            }

            // Step 4: Send end frame
            Map<String, Object> endFrame = new HashMap<>();
            endFrame.put("type", "end");
            endFrame.put("executionId", executionId.toString());
            String endJson = objectMapper.writeValueAsString(endFrame);
            out.println(endJson);
            log.debug("Sent end frame: executionId={}", executionId);

            // Receive ack
            String ackLine = in.readLine();
            if (ackLine != null) {
                Map<String, Object> ack = objectMapper.readValue(ackLine, Map.class);
                if ("ack".equals(ack.get("type"))) {
                    log.debug("Received ack from sandbox");
                }
            }

        } catch (IOException e) {
            log.error("Socket communication failed: executionId={}", executionId, e);
            
            // Return error result
            TestCaseResultDto errorResult = TestCaseResultDto.builder()
                    .testCaseId(-1L)
                    .status("RUNTIME_ERROR")
                    .actualOutput(e.getMessage())
                    .expectedOutput("")
                    .executionTimeMs(0L)
                    .memoryBytes(0L)
                    .build();
            results.add(errorResult);
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                log.debug("Error closing socket: {}", e.getMessage());
            }
        }

        log.info("Execution completed via sandbox: executionId={}, results={}", executionId, results.size());
        return results;
    }

    /**
     * Connect to sandbox socket with exponential backoff retry (SRS §10 Error Handling)
     */
    private Socket connectWithRetry(String host, int port, UUID executionId) throws IOException {
        int backoffMs = SOCKET_INITIAL_BACKOFF_MS;
        IOException lastException = null;

        for (int attempt = 0; attempt < SOCKET_CONNECT_RETRIES; attempt++) {
            try {
                log.debug("Connecting to sandbox: {}:{}, attempt={}/{}", host, port, attempt + 1, SOCKET_CONNECT_RETRIES);
                Socket socket = new Socket(host, port);
                socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);  // Set read timeout per SRS §6.2
                log.debug("Successfully connected to sandbox on attempt {}: executionId={}", attempt + 1, executionId);
                return socket;
            } catch (IOException e) {
                lastException = e;
                if (attempt < SOCKET_CONNECT_RETRIES - 1) {
                    log.warn("Connection failed (attempt {}/{}), retrying in {}ms: executionId={}", 
                            attempt + 1, SOCKET_CONNECT_RETRIES, backoffMs, executionId);
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, SOCKET_MAX_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Connection retry interrupted", ie);
                    }
                } else {
                    log.error("All connection attempts failed: executionId={}", executionId);
                }
            }
        }
        throw lastException;
    }

    /**
     * Parse result frame from sandbox wrapper
     */
    private TestCaseResultDto parseResultFrame(Map<String, Object> frame) {
        String id = (String) frame.get("id");
        String status = (String) frame.get("status");
        String stdout = (String) frame.get("stdout");
        String stderr = (String) frame.get("stderr");
        String errorMsg = (String) frame.get("errorMessage");
        
        Object runtimeMs = frame.get("runtimeMs");
        Object memoryBytes = frame.get("memoryBytes");

        return TestCaseResultDto.builder()
                .testCaseId(Long.parseLong(id))
                .status(status)
                .actualOutput(stdout != null ? stdout : (errorMsg != null ? errorMsg : ""))
                .expectedOutput("")  // Will be compared at aggregator level
                .executionTimeMs(runtimeMs instanceof Number ? ((Number) runtimeMs).longValue() : 0L)
                .memoryBytes(memoryBytes instanceof Number ? ((Number) memoryBytes).longValue() : 0L)
                .build();
    }

    /**
     * Test case input/config for sandbox execution
     */
    @Data
    @Builder
    public static class SandboxTestCase {
        private String id;
        private String input;
        private long timeoutMs;
    }

}
