package com.epam.sandbox;

import com.epam.sandbox.execute.SubmissionRunner;
import com.epam.sandbox.json.Json;
import com.epam.sandbox.protocol.Frame;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sandbox Wrapper main entry point.
 *
 * SRS §6:    standalone, dependency-free Java application running inside an
 *            isolated container.
 * SRS §6.2:  receives source code, compiles in-memory, then iteratively
 *            evaluates test cases, streaming results back. Stays alive
 *            for the next user's source code.
 * SRS §13:   shaded JAR with this class as {@code Main-Class}.
 *
 * Subtask EPMICMPCOD-452: socket/stdin listener and JSON input protocol parsing.
 *
 * Transport (chosen at startup):
 *   - Default: stdin/stdout (line-delimited JSON; many submissions per process).
 *   - {@code --socket=<port>}: TCP listener; one submission per accepted
 *     connection (SRS §4.3 — one container per submission).
 */
public final class SandboxWrapper {

    private SandboxWrapper() {}

    public static void main(String[] args) {
        Integer socketPort = null;
        for (String a : args) {
            if (a.startsWith("--socket=")) {
                socketPort = Integer.parseInt(a.substring("--socket=".length()));
            }
        }

        try (SubmissionRunner runner = new SubmissionRunner()) {
            if (socketPort != null) {
                runSocketLoop(socketPort, runner);
            } else {
                runStdinLoop(runner, System.in, System.out);
            }
        } catch (IOException e) {
            System.err.println("Sandbox Wrapper fatal I/O error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Read line-delimited JSON frames from {@code in}, write line-delimited
     * JSON frames to {@code out}. Loops until EOF (SRS §6.2 step 3).
     */
    static void runStdinLoop(SubmissionRunner runner, InputStream in, PrintStream out)
            throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String response = handleLine(runner, line);
                if (response != null) {
                    out.println(response);
                    out.flush();
                }
            }
        }
    }

    private static void runSocketLoop(int port, SubmissionRunner runner) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.err.println("Sandbox Wrapper listening on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = server.accept();
                handleSocketConnection(client, runner);
            }
        }
    }

    private static void handleSocketConnection(Socket client, SubmissionRunner runner) {
        try (Socket s = client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String response = handleLine(runner, line);
                if (response != null) {
                    out.write(response);
                    out.newLine();
                    out.flush();
                }
                // One submission per connection: close after we ACK an `end` frame.
                if (response != null && response.contains("\"type\":\"ack\"")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Sandbox Wrapper socket I/O error: " + e.getMessage());
        }
    }

    /**
     * Dispatch a single inbound JSON frame and return the response frame
     * (or {@code null} if no response is required). Never throws.
     */
    static String handleLine(SubmissionRunner runner, String json) {
        try {
            Frame.ParsedFrame f = Frame.parse(json);
            return switch (f.type) {
                case SOURCE   -> handleSource(runner, f);
                case TESTCASE -> handleTestCase(runner, f);
                case END      -> handleEnd(runner, f);
                default       -> Frame.protocolError(
                        "Unexpected frame type from orchestrator: " + f.type);
            };
        } catch (Json.JsonException je) {
            return Frame.protocolError("Bad JSON: " + je.getMessage());
        } catch (RuntimeException re) {
            return Frame.protocolError(
                    re.getClass().getSimpleName()
                            + (re.getMessage() == null ? "" : ": " + re.getMessage()));
        }
    }

    private static String handleSource(SubmissionRunner runner, Frame.ParsedFrame f) {
        String executionId = f.getString("executionId");
        String className   = f.getString("className");
        String sourceCode  = f.getString("sourceCode");
        if (executionId == null || sourceCode == null) {
            return Frame.protocolError("'source' frame requires 'executionId' and 'sourceCode'");
        }
        if (className == null || className.isBlank()) {
            // SRS §6.2 step 2b mentions the `Solution` class explicitly.
            className = "Solution";
        }
        var result = runner.compile(className, sourceCode);
        if (!result.isSuccess()) {
            // SRS §10: COMPILE_ERROR short-circuits the submission.
            return Frame.compileError(executionId, result.getFormattedErrorMessage());
        }
        return Frame.compiled(executionId);
    }

    private static String handleTestCase(SubmissionRunner runner, Frame.ParsedFrame f) {
        String id = f.getString("id");
        if (id == null) id = "tc";
        if (!runner.isReady()) {
            return Frame.protocolError("'testcase' received before successful 'source'");
        }
        String stdin = f.getString("stdin");
        long timeoutMs = f.getLong("timeoutMs", 0L);
        return runner.runTestCase(id, stdin == null ? "" : stdin, timeoutMs);
    }

    private static String handleEnd(SubmissionRunner runner, Frame.ParsedFrame f) {
        String executionId = f.getString("executionId");
        runner.reset();
        return Frame.ack(executionId == null ? "" : executionId);
    }

    // visible for tests
    static void writeUtf8(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }
}
