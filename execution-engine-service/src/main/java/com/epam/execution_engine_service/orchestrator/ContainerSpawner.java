package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.config.SandboxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Container Orchestration and Sandbox Spawner
 *
 * <p>EPMICMPCOD-349: Enforce Sandbox Runtime Isolation — builds and executes Docker containers
 * with complete isolation enforcement.
 *
 * <p>EPMICMPCOD-530 / EPMICMPCOD-531 / EPMICMPCOD-532: Pool lifecycle operations —
 * {@link #startDetachedContainer(int)}, {@link #killContainer(String)},
 * {@link #stopContainer(String)} support the persistent container pool managed by
 * {@code ContainerPoolService}.
 *
 * <p>Subtasks:
 * <ul>
 *   <li>EPMICMPCOD-451: JVM runtime flags injection</li>
 *   <li>EPMICMPCOD-453: Non-root container security and read-only filesystem</li>
 *   <li>EPMICMPCOD-456: Network isolation and seccomp enforcement</li>
 *   <li>EPMICMPCOD-457: CPU, memory, and PID limits</li>
 * </ul>
 */
@Component
public class ContainerSpawner {

    private static final Logger logger = LoggerFactory.getLogger(ContainerSpawner.class);

    private static final String DOCKER_IMAGE = "sandbox:latest";
    private static final String SANDBOX_USER_UID = "65534";
    private static final String SANDBOX_USER_GID = "65534";
    private static final int DEFAULT_TIMEOUT_SECONDS = 3;

    /** Port inside the sandbox container that the sandbox-wrapper JAR listens on. */
    private static final int SANDBOX_WRAPPER_CONTAINER_PORT = 9999;

    /** Milliseconds to wait between socket-ready polling attempts. */
    private static final int SOCKET_READY_POLL_MS = 200;

    /** Maximum number of socket-ready polling attempts (~10 s at 200 ms intervals). */
    private static final int SOCKET_READY_MAX_ATTEMPTS = 50;

    private final SandboxConfig sandboxConfig;

    private static final java.util.concurrent.atomic.AtomicInteger availablePoolSize =
            new java.util.concurrent.atomic.AtomicInteger(10);

    public ContainerSpawner(SandboxConfig sandboxConfig) {
        this.sandboxConfig = sandboxConfig;
    }

    /**
     * Returns the current number of available (idle) sandbox containers in the pool.
     *
     * <p><b>SRS §12 Compliance:</b> Signal 5 — Sandbox Pool Exhaustion Monitoring.
     * Kept for backward compatibility; the authoritative pool size is owned by
     * {@code ContainerPoolService}.
     *
     * @return count of currently available containers (≥0)
     */
    public int getPoolSize() {
        return availablePoolSize.get();
    }

    // -----------------------------------------------------------------------
    // Pool container lifecycle  (EPMICMPCOD-530 / -531 / -532)
    // -----------------------------------------------------------------------

    /**
     * Start a long-lived (detached) sandbox container that persistently runs the
     * sandbox-wrapper JAR, mapping {@code hostPort → SANDBOX_WRAPPER_CONTAINER_PORT}.
     *
     * <p>This method blocks until the container's socket is accepting connections or the
     * maximum wait time is exceeded (SRS §12, Acceptance Criterion 1: containers must be
     * ready before any submission is processed).
     *
     * @param hostPort the host port to map to the container's sandbox-wrapper socket
     * @return the Docker container ID of the started container
     * @throws RuntimeException if the container fails to start or does not become ready
     */
    public String startDetachedContainer(int hostPort) {
        logger.info("Starting detached pool container on host port {}", hostPort);

        // buildDetachedDockerCommand stores resource-limit strings as single tokens;
        // expand them here before handing to ProcessBuilder.
        List<String> rawCmd = buildDetachedDockerCommand(hostPort);
        List<String> cmd = expandTokens(rawCmd);
        logger.debug("Detached docker command: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Read the container ID printed by 'docker run -d' on stdout
            String containerId;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                containerId = reader.readLine();
            }

            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            if (!exited || process.exitValue() != 0) {
                throw new RuntimeException("docker run -d failed for host port " + hostPort);
            }

            if (containerId == null || containerId.isBlank()) {
                throw new RuntimeException("docker run -d returned empty container ID for port " + hostPort);
            }

            containerId = containerId.trim();
            logger.info("Detached container started: containerId={}, hostPort={}", containerId, hostPort);

            // Wait for the sandbox-wrapper socket to become ready
            waitForSocketReady("localhost", hostPort, SOCKET_READY_MAX_ATTEMPTS);

            return containerId;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while starting detached container on port " + hostPort, e);
        } catch (IOException e) {
            throw new RuntimeException("IO error starting detached container on port " + hostPort, e);
        }
    }

    /**
     * Send SIGKILL to a container (TLE termination — SRS §10, EPMICMPCOD-532).
     *
     * <p>The terminated container is never returned to the pool.
     *
     * @param containerId Docker container ID to kill
     */
    public void killContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        logger.warn("Sending SIGKILL to container: containerId={}", containerId);
        try {
            Process process = new ProcessBuilder("docker", "kill", "--signal=KILL", containerId)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while killing container {}", containerId);
        } catch (IOException e) {
            logger.warn("IO error killing container {}: {}", containerId, e.getMessage());
        }
        // Remove container (best-effort)
        removeContainer(containerId);
    }

    /**
     * Gracefully stop a container and remove it (idle TTL recycle / shutdown — SRS §12,
     * EPMICMPCOD-531).
     *
     * @param containerId Docker container ID to stop
     */
    public void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        logger.info("Stopping container: containerId={}", containerId);
        try {
            Process process = new ProcessBuilder("docker", "stop", containerId)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while stopping container {}", containerId);
        } catch (IOException e) {
            logger.warn("IO error stopping container {}: {}", containerId, e.getMessage());
        }
        removeContainer(containerId);
    }

    // -----------------------------------------------------------------------
    // One-shot execution  (existing — unchanged)
    // -----------------------------------------------------------------------

    /**
     * Spawns a sandbox container with complete runtime isolation enforcement.
     *
     * <p>Security layers enforced:
     * <ol>
     *   <li>Non-root execution (UID 65534)</li>
     *   <li>Read-only filesystem with tmpfs overlays</li>
     *   <li>Network isolation (no eth0)</li>
     *   <li>Seccomp profile (syscall filtering)</li>
     *   <li>Capability dropping (minimal privilege)</li>
     *   <li>Resource limits (memory, CPU, PID)</li>
     *   <li>JVM-level hardening (GC, JIT, heap)</li>
     * </ol>
     *
     * @param code           user code to execute
     * @param timeoutSeconds execution timeout
     * @return container execution result
     */
    public ContainerExecutionResult spawn(String code, int timeoutSeconds) {
        logger.info("Spawning sandbox container with code ({} bytes), timeout: {} seconds",
                code.length(), timeoutSeconds);

        Process process = null;
        try {
            // buildDockerCommand stores resource-limit strings as single tokens;
            // expand them here before handing to ProcessBuilder (each arg must be separate).
            List<String> rawCmd = buildDockerCommand(code);
            List<String> dockerCmd = expandTokens(rawCmd);

            logger.debug("Docker command: {}", String.join(" ", dockerCmd));

            ProcessBuilder pb = new ProcessBuilder(dockerCmd);
            pb.redirectErrorStream(true);
            process = pb.start();

            // Capture output with proper resource management
            StringBuilder output = new StringBuilder();
            try (InputStreamReader isr = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                logger.warn("Failed to read container output stream", e);
            }

            // Wait for completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                logger.warn("Container execution timeout after {} seconds, terminating forcibly", timeoutSeconds);
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for process termination", e);
                    Thread.currentThread().interrupt();
                }
                return ContainerExecutionResult.timeout(output.toString(), timeoutSeconds);
            }

            int exitCode = process.exitValue();
            logger.info("Container execution completed with exit code: {}", exitCode);

            return ContainerExecutionResult.success(output.toString(), exitCode);

        } catch (InterruptedException e) {
            logger.error("Container spawning interrupted", e);
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return ContainerExecutionResult.error("Container execution interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Container spawning failed with unexpected error", e);
            if (process != null) {
                try {
                    process.destroyForcibly();
                } catch (Exception destroyError) {
                    logger.warn("Error destroying process after failure", destroyError);
                }
            }
            return ContainerExecutionResult.error("Container spawning failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Build the {@code docker run -d} command for a long-lived pool container.
     *
     * <p>Applies the same security/resource flags as the one-shot command but runs
     * in detached mode and maps {@code hostPort} to the sandbox-wrapper socket port.
     *
     * @param hostPort host port to bind
     * @return docker run -d command tokens
     */
    private List<String> buildDetachedDockerCommand(int hostPort) {
        List<String> cmd = new ArrayList<>();

        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");  // detached — persistent pool container

        // Port mapping: hostPort → container SANDBOX_WRAPPER_CONTAINER_PORT
        cmd.add("-p");
        cmd.add(hostPort + ":" + SANDBOX_WRAPPER_CONTAINER_PORT);

        // Security: non-root
        cmd.add("--user");
        cmd.add(SANDBOX_USER_UID + ":" + SANDBOX_USER_GID);

        // Filesystem isolation
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:noexec,nodev,nosuid,size=64m");
        cmd.add("--tmpfs");
        cmd.add("/sandbox:noexec,nodev,nosuid,size=128m");

        // Network isolation
        cmd.add("--net=none");

        // Capability restrictions
        cmd.add("--cap-drop=ALL");
        cmd.add("--cap-add=KILL");

        // Seccomp
        if (sandboxConfig.isSeccompEnabled()) {
            cmd.add("--security-opt");
            cmd.add("seccomp=" + sandboxConfig.getSeccompProfilePath());
        } else {
            cmd.add("--security-opt");
            cmd.add("seccomp=unconfined");
        }

        cmd.add("--security-opt");
        cmd.add("no-new-privileges:true");

        // Resource limits — stored as single tokens; expanded in startDetachedContainer before ProcessBuilder
        cmd.add(sandboxConfig.buildDockerMemoryLimit());
        cmd.add(sandboxConfig.buildDockerCpuLimit());
        cmd.add(sandboxConfig.buildDockerPidLimit());

        // JVM flags
        cmd.add("-e");
        cmd.add("JVM_FLAGS=" + sandboxConfig.buildJvmFlags());

        // Container image — runs the sandbox-wrapper as the default CMD/ENTRYPOINT
        cmd.add(DOCKER_IMAGE);

        return cmd;
    }

    /**
     * Poll until the sandbox-wrapper socket on {@code localhost:port} is accepting connections,
     * or throw if not ready within {@code maxAttempts × SOCKET_READY_POLL_MS} ms.
     *
     * @param host        target host
     * @param port        target port
     * @param maxAttempts maximum polling attempts
     */
    private void waitForSocketReady(String host, int port, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try (Socket s = new Socket(host, port)) {
                logger.debug("Sandbox socket ready on {}:{} after {} attempts", host, port, attempt + 1);
                return;
            } catch (IOException ignored) {
                // Not ready yet — wait and retry
            }
            try {
                Thread.sleep(SOCKET_READY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for sandbox socket on port " + port);
            }
        }
        throw new RuntimeException(
                "Sandbox socket on " + host + ":" + port + " not ready after " +
                        (maxAttempts * SOCKET_READY_POLL_MS) + " ms");
    }

    /** Best-effort {@code docker rm -f} to clean up after kill/stop. */
    private void removeContainer(String containerId) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerId)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("Could not remove container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Builds complete {@code docker run} command for one-shot code execution with all
     * isolation flags.
     *
     * @param code user code to execute
     * @return complete docker run command
     */
    private List<String> buildDockerCommand(String code) {
        List<String> cmd = new ArrayList<>();

        // Docker binary
        cmd.add("docker");
        cmd.add("run");

        // === SUBTASK 453: Non-root container security ===
        cmd.add("--user");
        cmd.add(SANDBOX_USER_UID + ":" + SANDBOX_USER_GID);

        // === SUBTASK 453: Read-only filesystem ===
        cmd.add("--read-only");

        // Temporary filesystem mounts with security flags (noexec, nodev, nosuid)
        cmd.add("--tmpfs");
        cmd.add("/tmp:noexec,nodev,nosuid,size=64m");
        cmd.add("--tmpfs");
        cmd.add("/sandbox:noexec,nodev,nosuid,size=128m");

        // === SUBTASK 456: Network isolation ===
        cmd.add("--net=none");

        // === SUBTASK 453: Capability dropping ===
        cmd.add("--cap-drop=ALL");
        cmd.add("--cap-add=KILL");  // Needed for thread signaling

        // === SUBTASK 456: Seccomp profile ===
        if (sandboxConfig.isSeccompEnabled()) {
            cmd.add("--security-opt");
            cmd.add("seccomp=" + sandboxConfig.getSeccompProfilePath());
        } else {
            cmd.add("--security-opt");
            cmd.add("seccomp=unconfined");
        }

        // === SUBTASK 453: Privilege escalation prevention ===
        cmd.add("--security-opt");
        cmd.add("no-new-privileges:true");

        // === SUBTASK 457: Resource limits ===
        // Stored as single tokens here; expanded into separate ProcessBuilder args in spawn().
        cmd.add(sandboxConfig.buildDockerMemoryLimit());
        cmd.add(sandboxConfig.buildDockerCpuLimit());
        cmd.add(sandboxConfig.buildDockerPidLimit());

        // === SUBTASK 451: JVM flags injection ===
        cmd.add("-e");
        cmd.add("JVM_FLAGS=" + sandboxConfig.buildJvmFlags());

        // Code injection (environment variable or volume mount)
        cmd.add("-e");
        cmd.add("USER_CODE=" + encodeForShell(code));

        // Container image
        cmd.add(DOCKER_IMAGE);

        return cmd;
    }

    /**
     * Expands a list of possibly multi-word tokens into a flat list of single-word tokens
     * suitable for {@link ProcessBuilder}.
     *
     * <p>Example: {@code ["--memory 288m"]} → {@code ["--memory", "288m"]}
     *
     * @param tokens command tokens, some of which may contain spaces
     * @return flat list with each token as a separate element
     */
    private List<String> expandTokens(List<String> tokens) {
        List<String> expanded = new ArrayList<>();
        for (String token : tokens) {
            String[] parts = token.split(" ");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    expanded.add(part);
                }
            }
        }
        return expanded;
    }

    /**
     * Encodes code for safe shell execution using Base64.
     * Prevents shell injection attacks by encoding the user code.
     */
    private String encodeForShell(String code) {
        byte[] encoded = Base64.getEncoder().encode(code.getBytes(StandardCharsets.UTF_8));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    /**
     * Container execution result wrapper.
     */
    public static class ContainerExecutionResult {
        private final boolean success;
        private final String output;
        private final int exitCode;
        private final String error;
        private final boolean timeout;

        private ContainerExecutionResult(boolean success, String output, int exitCode,
                                         String error, boolean timeout) {
            this.success = success;
            this.output = output;
            this.exitCode = exitCode;
            this.error = error;
            this.timeout = timeout;
        }

        public static ContainerExecutionResult success(String output, int exitCode) {
            return new ContainerExecutionResult(true, output, exitCode, null, false);
        }

        public static ContainerExecutionResult timeout(String output, int timeoutSeconds) {
            return new ContainerExecutionResult(false, output, -1,
                    "Execution timeout after " + timeoutSeconds + " seconds", true);
        }

        public static ContainerExecutionResult error(String error) {
            return new ContainerExecutionResult(false, "", -1, error, false);
        }

        public boolean isSuccess() { return success; }
        public boolean isTimeout() { return timeout; }
        public String  getOutput() { return output; }
        public int     getExitCode() { return exitCode; }
        public String  getError()  { return error; }

        @Override
        public String toString() {
            return "ContainerExecutionResult{" +
                    "success=" + success +
                    ", timeout=" + timeout +
                    ", exitCode=" + exitCode +
                    ", output='" + (output.length() > 100 ? output.substring(0, 100) + "..." : output) + '\'' +
                    ", error='" + error + '\'' +
                    '}';
        }
    }
}
