package com.epam.execution_engine_service.orchestrator.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * SRP: Manages sandbox container lifecycle and pool operations.
 * Docker client creation → {@link DockerClientFactory}; readiness probing → {@link ContainerReadinessProbe}.
 * Implements {@link ContainerPool} so consumers depend on the interface (DIP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerContainerPool implements ContainerPool {

    private final DockerClientFactory dockerClientFactory;
    private final ContainerReadinessProbe readinessProbe;
    private final ObjectMapper objectMapper;

    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    @Value("${app.execution.sandbox.memory-limit-mb:256}")
    private long memoryLimitMb;

    @Value("${app.docker.sandbox-image:codeval/sandbox-wrapper:latest}")
    private String sandboxImage;

    @Value("${app.docker.sandbox-host:localhost}")
    private String sandboxHost;

    private static final int SANDBOX_INNER_PORT = 5000;

    private DockerClient dockerClient;

    private final BlockingQueue<SandboxContainer> pool         = new ArrayBlockingQueue<>(200);
    private final List<SandboxContainer>          allContainers = new CopyOnWriteArrayList<>();
    private Semaphore containerSlots;

    // ─────────────────────────────────────────────────────────────────────────
    // Startup
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() {
        try {
            dockerClient = dockerClientFactory.createClient();
            log.info("[Pool] Docker available. Pre-warming {} sandbox containers (image={})...",
                    warmMinSize, sandboxImage);
            containerSlots = new Semaphore(warmMinSize, true);
            int spawned = 0;
            for (int i = 0; i < warmMinSize; i++) {
                try {
                    SandboxContainer c = createAndStartContainer();
                    pool.offer(c);
                    allContainers.add(c);
                    spawned++;
                    log.info("[Pool] Container {}/{} ready — id={} port={}",
                            spawned, warmMinSize, c.getContainerId(), c.getSandboxPort());
                } catch (Exception e) {
                    log.error("[Pool] Failed to spawn container {}/{}: {}", i + 1, warmMinSize, e.getMessage());
                }
            }
            log.info("[Pool] Initialized: {}/{} containers ready", spawned, warmMinSize);
        } catch (Throwable e) {
            log.warn("[Pool] Docker not available — running in STUB mode: {}", e.getMessage());
            dockerClient = null;
            containerSlots = new Semaphore(0); // prevent NPE if acquire() is mistakenly called
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Container creation
    // ─────────────────────────────────────────────────────────────────────────

    private SandboxContainer createAndStartContainer() throws Exception {
        ExposedPort containerPort = ExposedPort.tcp(SANDBOX_INNER_PORT);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(0));

        // Labels: override com.docker.compose.project so Rancher Desktop / Docker Desktop
        // shows sandbox containers in their own standalone group, not under execution-engine-service.
        Map<String, String> labels = Map.of(
                "com.docker.compose.project", "codeval-sandboxes",
                "codeval.role", "sandbox"
        );

        CreateContainerResponse created = dockerClient.createContainerCmd(sandboxImage)
                .withExposedPorts(containerPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withMemory(memoryLimitMb * 1024 * 1024)
                        .withMemorySwap(memoryLimitMb * 1024 * 1024)
                        .withAutoRemove(false))
                .withEnv("SANDBOX_PORT=" + SANDBOX_INNER_PORT)
                .withLabels(labels)
                .exec();

        String id = created.getId();
        dockerClient.startContainerCmd(id).exec();

        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(id).exec();
        Ports.Binding[] bindings = inspect.getNetworkSettings().getPorts()
                .getBindings().get(containerPort);
        if (bindings == null || bindings.length == 0) {
            dockerClient.removeContainerCmd(id).withForce(true).exec();
            throw new IllegalStateException("No port binding for container " + id);
        }
        int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());
        readinessProbe.waitUntilReady(hostPort, id);
        log.debug("[Pool] Container {} ready on {}:{}", id, sandboxHost, hostPort);
        return new SandboxContainer(id, sandboxHost, hostPort, objectMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ContainerPool interface implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Acquire a ready container.
     * Bug-fix: removed the outer catch(IllegalStateException) that caused a double semaphore release.
     * The semaphore is now released only on the ONE path that did NOT return a container.
     */
    @Override
    public SandboxContainer acquire(long timeoutMs) throws Exception {
        boolean slotAcquired = containerSlots.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        if (!slotAcquired) {
            throw new IllegalStateException(
                "[Pool] All " + warmMinSize + " sandbox containers are busy — timed out after " + timeoutMs + "ms");
        }
        // Slot acquired — release it only if we do NOT successfully return a container
        boolean returned = false;
        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                SandboxContainer candidate = pool.poll(500, TimeUnit.MILLISECONDS);
                if (candidate == null) continue;
                if (isAlive(candidate)) {
                    log.debug("[Pool] Acquired container {} (pool size ~{})", candidate.getContainerId(), pool.size());
                    returned = true;
                    return candidate; // caller MUST call release() — semaphore released there
                } else {
                    log.warn("[Pool] Container {} is dead — discarding and replacing", candidate.getContainerId());
                    containerSlots.release(); // free the slot for the dead container
                    discardAndReplace(candidate);
                    // re-acquire a fresh slot for the replacement
                    long remaining = deadline - System.currentTimeMillis();
                    slotAcquired = containerSlots.tryAcquire(remaining, TimeUnit.MILLISECONDS);
                    if (!slotAcquired) {
                        returned = true; // semaphore not held — don't release again
                        throw new IllegalStateException("[Pool] Timed out waiting for replacement container");
                    }
                }
            }
            throw new IllegalStateException("[Pool] No container available after " + timeoutMs + "ms");
        } finally {
            // Only release if we acquired a slot but are leaving without handing off a container
            if (!returned) {
                containerSlots.release();
            }
        }
    }

    @Override
    public void release(SandboxContainer container) {
        try {
            if (!isAlive(container)) {
                log.warn("[Pool] Container {} failed liveness on release — replacing", container.getContainerId());
                discardAndReplace(container);
                return;
            }
            pool.offer(container);
            log.debug("[Pool] Released container {} (pool size ~{})", container.getContainerId(), pool.size());
        } finally {
            containerSlots.release();
        }
    }

    @Override
    public boolean isDockerAvailable() {
        return dockerClient != null;
    }

    @Override
    public int getPoolSize() {
        return pool.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isAlive(SandboxContainer c) {
        return readinessProbe.isAlive(c.getSandboxPort());
    }

    private void discardAndReplace(SandboxContainer dead) {
        allContainers.remove(dead);
        if (dockerClient != null) {
            try { dockerClient.removeContainerCmd(dead.getContainerId()).withForce(true).exec(); }
            catch (Exception e) { log.warn("[Pool] Could not remove container {}: {}", dead.getContainerId(), e.getMessage()); }
        }
        Thread.ofVirtual().name("pool-replacer").start(() -> {
            try {
                SandboxContainer fresh = createAndStartContainer();
                allContainers.add(fresh);
                pool.offer(fresh);
                log.info("[Pool] Replacement container {} ready (pool size ~{})", fresh.getContainerId(), pool.size());
            } catch (Exception e) {
                log.error("[Pool] Failed to spawn replacement container: {}", e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shutdown
    // ─────────────────────────────────────────────────────────────────────────

    @PreDestroy
    public void shutdown() {
        if (dockerClient == null) return;
        log.info("[Pool] Shutting down — stopping {} containers", allContainers.size());
        allContainers.forEach(c -> {
            try {
                dockerClient.stopContainerCmd(c.getContainerId()).withTimeout(5).exec();
                dockerClient.removeContainerCmd(c.getContainerId()).exec();
            } catch (Exception e) {
                log.warn("[Pool] Error stopping container {}: {}", c.getContainerId(), e.getMessage());
            }
        });
    }
}

