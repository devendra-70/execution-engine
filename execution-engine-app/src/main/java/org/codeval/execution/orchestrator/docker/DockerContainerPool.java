package org.codeval.execution.orchestrator.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerContainerPool {

    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    @Value("${app.execution.sandbox.memory-limit-mb:256}")
    private long memoryLimitMb;

    @Value("${app.execution.sandbox.jvm-xms:128m}")
    private String jvmXms;

    @Value("${app.execution.sandbox.jvm-xmx:256m}")
    private String jvmXmx;

    @Value("${app.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${app.docker.sandbox-image:codeval/sandbox-wrapper:latest}")
    private String sandboxImage;

    /** Host that the engine uses to reach sandbox containers (localhost when port-mapped) */
    @Value("${app.docker.sandbox-host:localhost}")
    private String sandboxHost;

    private static final int SANDBOX_INNER_PORT = 5000;
    private static final int READY_POLL_INTERVAL_MS = 500;
    private static final int READY_MAX_ATTEMPTS = 20; // 10 seconds

    private DockerClient dockerClient;
    private final BlockingQueue<SandboxContainer> pool = new ArrayBlockingQueue<>(100);
    private final List<SandboxContainer> allContainers = new ArrayList<>();

    @PostConstruct
    public void initialize() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(URI.create(dockerHost))
                    .maxConnections(50)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .responseTimeout(Duration.ofSeconds(30))
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, httpClient);

            // Verify Docker is reachable
            dockerClient.pingCmd().exec();

            log.info("Pre-warming {} sandbox containers (image: {})...", warmMinSize, sandboxImage);
            for (int i = 0; i < warmMinSize; i++) {
                SandboxContainer container = createAndStartContainer();
                pool.offer(container);
                allContainers.add(container);
            }
            log.info("Container pool initialized with {} containers", warmMinSize);
        } catch (Exception e) {
            log.warn("Docker not available — container pool running in STUB mode: {}", e.getMessage());
            dockerClient = null;
        }
    }

    private SandboxContainer createAndStartContainer() throws Exception {
        ExposedPort sandboxPort = ExposedPort.tcp(SANDBOX_INNER_PORT);
        Ports portBindings = new Ports();
        // Bind to ephemeral host port (0 = OS assigns a free port)
        portBindings.bind(sandboxPort, Ports.Binding.bindPort(0));

        CreateContainerResponse created = dockerClient.createContainerCmd(sandboxImage)
                .withExposedPorts(sandboxPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withMemory(memoryLimitMb * 1024 * 1024)
                        .withMemorySwap(memoryLimitMb * 1024 * 1024))
                .withEnv("JAVA_OPTS=-Xms" + jvmXms + " -Xmx" + jvmXmx,
                         "SANDBOX_PORT=" + SANDBOX_INNER_PORT)
                .exec();

        dockerClient.startContainerCmd(created.getId()).exec();

        // Discover the assigned host port
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(created.getId()).exec();
        Ports.Binding[] bindings = inspect.getNetworkSettings().getPorts()
                .getBindings().get(sandboxPort);
        if (bindings == null || bindings.length == 0) {
            throw new IllegalStateException("No port binding found for container " + created.getId());
        }
        int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());

        // Wait until the sandbox TCP server is accepting connections
        waitForSandboxReady(hostPort, created.getId());

        log.debug("Sandbox container {} ready on {}:{}", created.getId(), sandboxHost, hostPort);
        return new SandboxContainer(created.getId(), dockerClient, sandboxHost, hostPort);
    }

    private void waitForSandboxReady(int hostPort, String containerId) throws Exception {
        for (int attempt = 1; attempt <= READY_MAX_ATTEMPTS; attempt++) {
            try (Socket socket = new Socket(sandboxHost, hostPort)) {
                // Connection succeeded — sandbox is ready
                return;
            } catch (Exception ignored) {
                log.debug("Waiting for sandbox container {} to be ready (attempt {}/{})",
                        containerId, attempt, READY_MAX_ATTEMPTS);
                Thread.sleep(READY_POLL_INTERVAL_MS);
            }
        }
        throw new IllegalStateException(
                "Sandbox container " + containerId + " did not become ready within " +
                (READY_POLL_INTERVAL_MS * READY_MAX_ATTEMPTS / 1000) + "s");
    }

    public SandboxContainer acquire(long timeoutMs) throws Exception {
        SandboxContainer container = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (container == null) {
            log.warn("No container available in pool within {}ms — creating on-demand", timeoutMs);
            container = createAndStartContainer();
        }
        return container;
    }

    public void release(SandboxContainer container) {
        if (container.isHealthy()) {
            pool.offer(container);
        } else {
            log.warn("Container {} unhealthy — replacing", container.getContainerId());
            try {
                dockerClient.removeContainerCmd(container.getContainerId()).withForce(true).exec();
                SandboxContainer fresh = createAndStartContainer();
                pool.offer(fresh);
                allContainers.add(fresh);
            } catch (Exception e) {
                log.error("Failed to replace unhealthy container", e);
            }
        }
    }

    public boolean isDockerAvailable() {
        return dockerClient != null;
    }

    @PreDestroy
    public void shutdown() {
        if (dockerClient != null) {
            allContainers.forEach(c -> {
                try {
                    dockerClient.stopContainerCmd(c.getContainerId()).exec();
                    dockerClient.removeContainerCmd(c.getContainerId()).exec();
                } catch (Exception e) {
                    log.warn("Error stopping container {}: {}", c.getContainerId(), e.getMessage());
                }
            });
        }
    }
}

