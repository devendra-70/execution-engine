package com.epam.execution_engine_service.orchestrator.docker;

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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class DockerContainerPool {

    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    @Value("${app.execution.sandbox.memory-limit-mb:256}")
    private long memoryLimitMb;

    @Value("${app.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${app.docker.sandbox-image:codeval/sandbox-wrapper:latest}")
    private String sandboxImage;

    @Value("${app.docker.sandbox-host:localhost}")
    private String sandboxHost;

    private static final int SANDBOX_INNER_PORT   = 5000;
    private static final int READY_POLL_MS        = 500;
    private static final int READY_MAX_ATTEMPTS   = 30;   // 15 s max
    private static final int PING_TIMEOUT_MS      = 2_000;

    private DockerClient dockerClient;

    /** Only READY containers live here — BlockingQueue is thread-safe */
    private final BlockingQueue<SandboxContainer> pool = new ArrayBlockingQueue<>(200);

    /** All containers we ever spawned — used for cleanup on shutdown */
    private final List<SandboxContainer> allContainers = new CopyOnWriteArrayList<>();

    /** How many containers are currently live (idle + busy) */
    private final AtomicInteger liveCount = new AtomicInteger(0);

    // ──────────────────────────────────────────────────────────────────
    // Startup
    // ──────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();

            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(URI.create(dockerHost))
                    .maxConnections(50)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            dockerClient.pingCmd().exec();   // throws if Docker unreachable

            log.info("[Pool] Docker available at {}. Pre-warming {} sandbox containers (image={})...",
                    dockerHost, warmMinSize, sandboxImage);

            int spawned = 0;
            for (int i = 0; i < warmMinSize; i++) {
                spawned += spawnAndRegisterContainer(i, warmMinSize);
            }

            log.info("[Pool] Initialized: {}/{} containers ready", spawned, warmMinSize);

        } catch (Throwable e) {
            log.warn("[Pool] Docker not available — running in STUB mode: {}", e.getMessage());
            dockerClient = null;
        }
    }

    /**
     * Spawn a single container and register it in the pool.
     * @return 1 if successful, 0 if failed
     */
    private int spawnAndRegisterContainer(int index, int warmMinSize) {
        try {
            SandboxContainer c = createAndStartContainer();
            if (!pool.offer(c)) {
                log.warn("[Pool] Failed to add container to warm pool - queue full");
            }
            allContainers.add(c);
            liveCount.incrementAndGet();
            log.info("[Pool] Container {}/{} ready — id={} port={}",
                    index + 1, warmMinSize, c.getContainerId(), c.getSandboxPort());
            return 1;
        } catch (Exception e) {
            log.error("[Pool] Failed to spawn container {}/{}: {}", index + 1, warmMinSize, e.getMessage());
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Container creation
    // ──────────────────────────────────────────────────────────────────

    private SandboxContainer createAndStartContainer() throws Exception {
        ExposedPort containerPort = ExposedPort.tcp(SANDBOX_INNER_PORT);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(0)); // ephemeral host port

        CreateContainerResponse created = dockerClient.createContainerCmd(sandboxImage)
                .withExposedPorts(containerPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withMemory(memoryLimitMb * 1024 * 1024)
                        .withMemorySwap(memoryLimitMb * 1024 * 1024)
                        .withAutoRemove(false))   // we manage removal ourselves
                .withEnv("SANDBOX_PORT=" + SANDBOX_INNER_PORT)
                .exec();

        String id = created.getId();
        log.debug("[Pool] Starting container {} ...", id);
        dockerClient.startContainerCmd(id).exec();

        // Discover actual host port assigned by Docker
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(id).exec();
        Ports.Binding[] bindings = inspect.getNetworkSettings().getPorts()
                .getBindings().get(containerPort);
        if (bindings == null || bindings.length == 0) {
            dockerClient.removeContainerCmd(id).withForce(true).exec();
            throw new IllegalStateException("No port binding for container " + id);
        }
        int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());

        // Block until the sandbox socket is truly accepting AND responding
        waitForSandboxReady(hostPort, id);

        log.debug("[Pool] Container {} ready on {}:{}", id, sandboxHost, hostPort);
        return new SandboxContainer(id, dockerClient, sandboxHost, hostPort);
    }

    /**
     * Readiness probe: attempt a TCP connect + write a minimal byte + read one byte.
     * The sandbox wrapper reads until EOF, so we don't do a real request here — we
     * just verify the TCP port is open and the JVM is responding.
     * We use a separate connection attempt-only probe (no payload) to avoid
     * interfering with the actual protocol.
     */
    private void waitForSandboxReady(int hostPort, String containerId) throws Exception {
        log.debug("[Pool] Waiting for container {} to be ready on port {} ...", containerId, hostPort);
        for (int attempt = 1; attempt <= READY_MAX_ATTEMPTS; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(sandboxHost, hostPort), PING_TIMEOUT_MS);
                // TCP handshake succeeded — sandbox JVM is listening
                log.debug("[Pool] Container {} ready after {} attempts", containerId, attempt);
                return;
            } catch (Exception ignored) {
                if (attempt % 5 == 0) {
                    log.debug("[Pool] Still waiting for {} (attempt {}/{})", containerId, attempt, READY_MAX_ATTEMPTS);
                }
                Thread.sleep(READY_POLL_MS);
            }
        }
        // Container never became ready — force-remove it
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove unready container {}", containerId, e);
        }
        throw new IllegalStateException(
                "Sandbox " + containerId + " did not become ready within " +
                (READY_POLL_MS * READY_MAX_ATTEMPTS / 1000) + "s");
    }

    // ──────────────────────────────────────────────────────────────────
    // Acquire / Release  (used by Orchestrator)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Acquire a ready container from the pool.
     * Validates liveness before returning. Stale containers are discarded and replaced.
     */
    public SandboxContainer acquire(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            SandboxContainer candidate = pool.poll(500, TimeUnit.MILLISECONDS);
            if (candidate == null) {
                continue; // try again until timeout
            }
            if (isAlive(candidate)) {
                log.debug("[Pool] Acquired container {} (pool size now ~{})", candidate.getContainerId(), pool.size());
                return candidate;
            } else {
                // Dead container — discard and spawn replacement asynchronously
                log.warn("[Pool] Container {} is dead — discarding and replacing", candidate.getContainerId());
                discardAndReplace(candidate);
            }
        }
        // Pool exhausted — spawn on-demand as last resort
        log.warn("[Pool] Pool empty after {}ms — spawning on-demand container", timeoutMs);
        SandboxContainer onDemand = createAndStartContainer();
        allContainers.add(onDemand);
        liveCount.incrementAndGet();
        return onDemand;
    }

    /**
     * Release a container back to the pool.
     * Guaranteed to be called from a finally block in the orchestrator.
     */
    public void release(SandboxContainer container) {
        if (!isAlive(container)) {
            log.warn("[Pool] Container {} failed liveness on release — replacing", container.getContainerId());
            discardAndReplace(container);
            return;
        }
        if (!pool.offer(container)) {
            log.warn("[Pool] Failed to return container {} to pool - queue full", container.getContainerId());
        }
        log.debug("[Pool] Released container {} back (pool size now ~{})",
                container.getContainerId(), pool.size());
    }

    // ──────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────

    /** TCP liveness ping — fast, doesn't interfere with protocol */
    private boolean isAlive(SandboxContainer c) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(sandboxHost, c.getSandboxPort()), PING_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void discardAndReplace(SandboxContainer dead) {
        // Remove from tracking
        allContainers.remove(dead);
        liveCount.decrementAndGet();

        // Force-remove Docker container (best effort)
        if (dockerClient != null) {
            try { dockerClient.removeContainerCmd(dead.getContainerId()).withForce(true).exec(); }
            catch (Exception e) { log.warn("[Pool] Could not remove container {}: {}", dead.getContainerId(), e.getMessage()); }
        }

        // Spawn replacement in background so we don't block the caller
        Thread.ofVirtual().name("pool-replacer").start(() -> {
            try {
                SandboxContainer fresh = createAndStartContainer();
                allContainers.add(fresh);
                if (!pool.offer(fresh)) {
                    log.warn("[Pool] Failed to add replacement container to pool - queue full");
                }
                liveCount.incrementAndGet();
                log.info("[Pool] Replacement container {} ready (pool size ~{})",
                        fresh.getContainerId(), pool.size());
            } catch (Exception e) {
                log.error("[Pool] Failed to spawn replacement container: {}", e.getMessage());
            }
        });
    }

    public boolean isDockerAvailable() {
        return dockerClient != null;
    }

    public int getPoolSize() {
        return pool.size();
    }

    // ──────────────────────────────────────────────────────────────────
    // Shutdown
    // ──────────────────────────────────────────────────────────────────

    @PreDestroy
    public void shutdown() {
        if (dockerClient == null) return;
        log.info("[Pool] Shutting down — stopping {} containers", allContainers.size());
        allContainers.forEach(c -> {
            try {
                dockerClient.stopContainerCmd(c.getContainerId()).withTimeout(5).exec();
                dockerClient.removeContainerCmd(c.getContainerId()).exec();
                log.debug("[Pool] Stopped container {}", c.getContainerId());
            } catch (Exception e) {
                log.warn("[Pool] Error stopping container {}: {}", c.getContainerId(), e.getMessage());
            }
        });
    }
}

