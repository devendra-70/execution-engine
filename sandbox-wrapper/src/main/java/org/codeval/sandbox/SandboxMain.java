package org.codeval.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * Standalone sandbox wrapper — runs as a persistent TCP socket server.
 *
 * Protocol (per connection):
 *  1. Client sends JSON SandboxRequest, then shuts down its output (EOF).
 *  2. Server reads all bytes until EOF, deserializes, runs the code.
 *  3. Server writes JSON SandboxResponse and closes the connection.
 *  4. Loop: server waits for the next connection (next submission).
 *
 * This allows: compile-once + new ClassLoader per test case (state-bleed prevention).
 */
public class SandboxMain {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("SANDBOX_PORT", "5000"));

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        SandboxRunner runner = new SandboxRunner();

        System.out.println("Sandbox wrapper listening on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // noinspection InfiniteLoopStatement — wrapper stays alive for the container lifetime
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Accepted connection from " + clientSocket.getRemoteSocketAddress());
                    try {
                        // Read full request (client shuts output after sending)
                        byte[] requestBytes = clientSocket.getInputStream().readAllBytes();

                        SandboxRequest request = mapper.readValue(requestBytes, SandboxRequest.class);
                        List<TestCaseResult> results = runner.run(request);

                        // Write response
                        String responseJson = mapper.writeValueAsString(new SandboxResponse(results));
                        OutputStream out = clientSocket.getOutputStream();
                        out.write(responseJson.getBytes());
                        out.flush();
                    } catch (Exception e) {
                        System.err.println("Error processing request: " + e.getMessage());
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    System.err.println("Connection error: " + e.getMessage());
                }
            }
        }
    }
}
