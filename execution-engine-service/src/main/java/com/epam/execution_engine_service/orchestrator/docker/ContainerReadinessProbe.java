package com.epam.execution_engine_service.orchestrator.docker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Socket;

/**
 * SRP: Single responsibility — probe a sandbox container until its TCP port is ready.
 * Extracted from DockerContainerPool.
 */
@Slf4j
@Component
public class ContainerReadinessProbe {

    private static final int READY_POLL_MS      = 500;
    private static final int READY_MAX_ATTEMPTS = 30;   // 15 s max
    private static final int PING_TIMEOUT_MS    = 2_000;

    @Value("${app.docker.sandbox-host:localhost}")
    private String sandboxHost;

    /**
     * Block until the sandbox at {@code hostPort} accepts TCP connections,
     * or throw if it does not become ready within the configured timeout.
     *
     * @param hostPort    the host-side port mapped to the sandbox container
     * @param containerId container ID used only for log messages
     * @throws Exception if the container never becomes ready
     */
    public void waitUntilReady(int hostPort, String containerId) throws Exception {
        log.debug("[Probe] Waiting for container {} to be ready on port {} ...", containerId, hostPort);
        for (int attempt = 1; attempt <= READY_MAX_ATTEMPTS; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(sandboxHost, hostPort), PING_TIMEOUT_MS);
                log.debug("[Probe] Container {} ready after {} attempt(s)", containerId, attempt);
                return;
            } catch (Exception ignored) {
                if (attempt % 5 == 0) {
                    log.debug("[Probe] Still waiting for {} (attempt {}/{})", containerId, attempt, READY_MAX_ATTEMPTS);
                }
                Thread.sleep(READY_POLL_MS);
            }
        }
        throw new IllegalStateException(
                "Sandbox " + containerId + " did not become ready within " +
                (READY_POLL_MS * READY_MAX_ATTEMPTS / 1000) + "s");
    }

    /**
     * Single-attempt TCP liveness check — no retries.
     * Used by the pool to verify a container is still alive before/after use.
     *
     * @return {@code true} if the TCP handshake succeeds within {@code PING_TIMEOUT_MS}
     */
    public boolean isAlive(int hostPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(sandboxHost, hostPort), PING_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


