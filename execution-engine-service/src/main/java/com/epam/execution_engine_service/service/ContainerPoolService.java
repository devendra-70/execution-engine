package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.exception.ContainerAcquisitionException;
import com.epam.execution_engine_service.orchestrator.ContainerSpawner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ContainerPoolService — Pre-warmed sandbox container pool (SRS §4.3, §12).
 *
 * <p>Manages a {@link BlockingQueue} of idle {@link ContainerHandle}s backed by real Docker
 * containers running the sandbox-wrapper JAR. Implements all lifecycle operations required
 * by EPMICMPCOD-347 and its subtasks:
 *
 * <ul>
 *   <li><b>EPMICMPCOD-530</b> — Pool initialisation, acquire/release lifecycle.</li>
 *   <li><b>EPMICMPCOD-531</b> — Idle TTL sweep: containers idle beyond
 *       {@code app.execution.pool.idle-ttl-seconds} are destroyed and replaced.</li>
 *   <li><b>EPMICMPCOD-532</b> — TLE replacement via {@link #discardAndReplace(ContainerHandle)}:
 *       terminated containers are removed from the pool and a fresh replacement is started.</li>
 * </ul>
 *
 * <p>All configuration values are strictly sourced from {@code application.yml} — never hardcoded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContainerPoolService {

    /** Minimum number of idle containers to maintain (SRS §12). */
    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    /** Idle TTL threshold in seconds; containers idle beyond this are recycled (SRS §12). */
    @Value("${app.execution.pool.idle-ttl-seconds:300}")
    private int idleTtlSeconds;

    /** Base host port for sandbox containers; each container gets a unique port offset. */
    @Value("${app.execution.sandbox.base-port:10000}")
    private int sandboxBasePort;

    private final ContainerSpawner containerSpawner;

    /** Thread-safe queue of available (idle) container handles (SRS §4.3). */
    private final BlockingQueue<ContainerHandle> availableContainers = new LinkedBlockingQueue<>();

    /** All known container handles (available + in-use), used for shutdown. */
    private final Set<ContainerHandle> allContainers = ConcurrentHashMap.newKeySet();

    /** Counter for unique port allocation per container instance. */
    private final AtomicInteger portCounter = new AtomicInteger(0);

    /** Executor for async replacement container startup (non-blocking acquire path). */
    private final ExecutorService replacementExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "container-replacement");
                t.setDaemon(true);
                return t;
            });

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    /**
     * On application startup, initialise exactly {@code warm-min-size} sandbox containers
     * (SRS §12, Acceptance Criterion 1).  All containers must be socket-ready before any
     * submission is processed.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initialising container pool: warm-min-size={}", warmMinSize);
        for (int i = 0; i < warmMinSize; i++) {
            ContainerHandle handle = startNewContainer();
            if (handle != null) {
                availableContainers.offer(handle);
                allContainers.add(handle);
            }
        }
        log.info("Container pool ready with {} containers", availableContainers.size());
    }

    /**
     * On application shutdown, cleanly stop all pool containers (SRS §12, Acceptance Criterion 5).
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down container pool — stopping {} containers", allContainers.size());
        replacementExecutor.shutdownNow();
        for (ContainerHandle handle : allContainers) {
            try {
                containerSpawner.stopContainer(handle.getContainerId());
                log.debug("Stopped container on shutdown: containerId={}", handle.getContainerId());
            } catch (Exception e) {
                log.warn("Error stopping container {} during shutdown: {}", handle.getContainerId(), e.getMessage());
            }
        }
        allContainers.clear();
        availableContainers.clear();
    }

    // ---------------------------------------------------------------------------
    // Acquire / Release  (EPMICMPCOD-530)
    // ---------------------------------------------------------------------------

    /**
     * Remove and return one container from the pool (blocks up to {@code timeout}).
     *
     * <p>Immediately schedules an async replacement so the pool is restored to
     * {@code warm-min-size} as quickly as possible (SRS §12, Acceptance Criterion 2).
     *
     * @param timeout maximum wait time
     * @return acquired {@link ContainerHandle}
     * @throws ContainerAcquisitionException if no container becomes available within timeout
     */
    public ContainerHandle acquire(Duration timeout) throws ContainerAcquisitionException {
        try {
            ContainerHandle container = availableContainers.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (container == null) {
                log.warn("Container acquisition timeout after {}ms — no containers available", timeout.toMillis());
                throw new ContainerAcquisitionException("No container available within timeout");
            }
            log.debug("Container acquired: id={}, containerId={}", container.getId(), container.getContainerId());
            // Proactively replace to restore warm-min-size asynchronously (SRS §12, AC 2)
            scheduleReplacement();
            return container;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerAcquisitionException("Container acquisition interrupted", e);
        }
    }

    /**
     * Return a healthy container to the pool and reset its idle timestamp
     * (SRS §12, Acceptance Criterion 3; EPMICMPCOD-530 AC 3).
     *
     * @param container the container to return; no-op if {@code null}
     */
    public void release(ContainerHandle container) {
        if (container == null) {
            return;
        }
        container.setLastReturnedAt(Instant.now());
        availableContainers.offer(container);
        log.debug("Container released: id={}", container.getId());
    }

    /**
     * Discard a terminated container (e.g. after TLE SIGKILL) and asynchronously start a
     * fresh replacement to restore {@code warm-min-size} (SRS §10, EPMICMPCOD-532).
     *
     * <p>The discarded container is <em>never</em> returned to the pool queue.
     *
     * @param container the container to SIGKILL and discard; no-op if {@code null}
     */
    public void discardAndReplace(ContainerHandle container) {
        if (container == null) {
            return;
        }
        allContainers.remove(container);
        try {
            containerSpawner.killContainer(container.getContainerId());
            log.info("Container SIGKILL'd and removed from pool: containerId={}", container.getContainerId());
        } catch (Exception e) {
            log.warn("Error killing container {}: {}", container.getContainerId(), e.getMessage());
        }
        scheduleReplacement();
    }

    // ---------------------------------------------------------------------------
    // Idle TTL Sweep  (EPMICMPCOD-531)
    // ---------------------------------------------------------------------------

    /**
     * Background sweep that recycles containers idle beyond {@code idle-ttl-seconds}
     * (SRS §4.3, §12; EPMICMPCOD-531).
     *
     * <p>Runs every {@code app.execution.pool.sweep-interval-ms} milliseconds (default 30 s).
     * Only containers in the <em>available</em> queue (not actively serving a submission) are
     * eligible — containers currently acquired are never touched.
     */
    @Scheduled(fixedDelayString = "${app.execution.pool.sweep-interval-ms:30000}")
    public void idleTtlSweep() {
        Instant cutoff = Instant.now().minusSeconds(idleTtlSeconds);
        List<ContainerHandle> staleContainers = new ArrayList<>();

        // Atomically drain the queue and re-insert non-expired containers
        List<ContainerHandle> snapshot = new ArrayList<>();
        availableContainers.drainTo(snapshot);

        for (ContainerHandle container : snapshot) {
            Instant lastReturned = container.getLastReturnedAt();
            if (lastReturned != null && lastReturned.isBefore(cutoff)) {
                staleContainers.add(container);
            } else {
                availableContainers.offer(container);
            }
        }

        for (ContainerHandle stale : staleContainers) {
            long idleSecs = Duration.between(stale.getLastReturnedAt(), Instant.now()).toSeconds();
            log.info("Recycling idle container: containerId={}, idleSecs={}", stale.getContainerId(), idleSecs);
            allContainers.remove(stale);
            try {
                containerSpawner.stopContainer(stale.getContainerId());
            } catch (Exception e) {
                log.warn("Error stopping stale container {}: {}", stale.getContainerId(), e.getMessage());
            }
            // Replace with a fresh container (SRS §12, AC 3)
            ContainerHandle fresh = startNewContainer();
            if (fresh != null) {
                availableContainers.offer(fresh);
                allContainers.add(fresh);
                log.info("Replaced stale container {} with fresh {}", stale.getContainerId(), fresh.getContainerId());
            }
        }

        if (!staleContainers.isEmpty()) {
            log.info("Idle TTL sweep recycled {} containers; pool available={}", staleContainers.size(), availableContainers.size());
        }
    }

    // ---------------------------------------------------------------------------
    // Metrics helpers
    // ---------------------------------------------------------------------------

    /** Returns the number of currently available (idle) containers. */
    public int getAvailableCount() {
        return availableContainers.size();
    }

    /** Returns the total number of tracked containers (available + in-use). */
    public int getTotalCount() {
        return allContainers.size();
    }

    /** Returns a snapshot map of pool statistics. */
    public Map<String, Integer> getPoolStats() {
        return Map.of(
                "available", availableContainers.size(),
                "total", allContainers.size(),
                "warmMinSize", warmMinSize
        );
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Schedule an asynchronous startup of one replacement container.
     * Called after every {@link #acquire(Duration)} and {@link #discardAndReplace(ContainerHandle)}.
     */
    private void scheduleReplacement() {
        replacementExecutor.submit(() -> {
            ContainerHandle fresh = startNewContainer();
            if (fresh != null) {
                availableContainers.offer(fresh);
                allContainers.add(fresh);
                log.debug("Async replacement container added to pool: id={}", fresh.getId());
            }
        });
    }

    /**
     * Start a new sandbox container via {@link ContainerSpawner#startDetachedContainer(int)},
     * assign a unique host port, and return a ready {@link ContainerHandle}.
     *
     * @return ready {@link ContainerHandle}, or {@code null} if startup fails
     */
    private ContainerHandle startNewContainer() {
        int assignedPort = sandboxBasePort + portCounter.getAndIncrement();
        try {
            String containerId = containerSpawner.startDetachedContainer(assignedPort);
            ContainerHandle handle = new ContainerHandle(
                    UUID.randomUUID().toString(),
                    containerId,
                    "localhost",
                    assignedPort,
                    Instant.now()
            );
            log.debug("New pool container started: containerId={}, port={}", containerId, assignedPort);
            return handle;
        } catch (Exception e) {
            log.error("Failed to start new pool container on port {}: {}", assignedPort, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------------
    // ContainerHandle
    // ---------------------------------------------------------------------------

    /**
     * Represents one sandbox container slot in the pool.
     *
     * <p>Carries the Docker container ID (for SIGKILL / stop), host/port (for socket
     * communication), and a {@code lastReturnedAt} timestamp (for idle TTL tracking).
     */
    public static class ContainerHandle {

        private final String id;           // pool-internal UUID
        private final String containerId;  // Docker container ID
        private final String host;
        private final int port;
        private final Instant createdAt;
        private volatile Instant lastReturnedAt;

        public ContainerHandle(String id, String containerId, String host, int port, Instant createdAt) {
            this.id = id;
            this.containerId = containerId;
            this.host = host;
            this.port = port;
            this.createdAt = createdAt;
            this.lastReturnedAt = createdAt;
        }

        public String getId()            { return id; }
        public String getContainerId()   { return containerId; }
        public String getHost()          { return host; }
        public int    getPort()          { return port; }
        public Instant getCreatedAt()    { return createdAt; }
        public Instant getLastReturnedAt() { return lastReturnedAt; }
        public void   setLastReturnedAt(Instant t) { this.lastReturnedAt = t; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(id, ((ContainerHandle) o).id);
        }

        @Override
        public int hashCode() { return Objects.hash(id); }

        @Override
        public String toString() {
            return "ContainerHandle{id='" + id + "', containerId='" + containerId + "', port=" + port + "}";
        }
    }
}
