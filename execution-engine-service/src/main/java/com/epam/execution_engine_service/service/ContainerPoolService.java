package com.epam.execution_engine_service.service;

import com.epam.execution_engine_service.exception.ContainerAcquisitionException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ContainerPoolService — Pre-warmed container pool simulation (SRS §4.3)
 * 
 * Simulates a pool of idle sandbox containers.
 * Configuration:
 * - warm-min-size: Number of pre-warmed containers
 * - max-size: Maximum total containers
 * - idle-ttl-seconds: Container recycle threshold
 * 
 * Note: This is a SIMULATION for unit testing. Real container lifecycle is out of scope.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContainerPoolService {

    @Value("${app.execution.pool.warm-min-size:5}")
    private int warmMinSize;

    @Value("${app.execution.pool.max-size:100}")
    private int maxSize;

    @Value("${app.execution.pool.idle-ttl-seconds:300}")
    private int idleTtlSeconds;

    private final BlockingQueue<ContainerHandle> availableContainers = new LinkedBlockingQueue<>();
    private final Set<ContainerHandle> allContainers = Collections.synchronizedSet(new HashSet<>());
    private final Map<ContainerHandle, Long> containerLastUsed = Collections.synchronizedMap(new HashMap<>());

    /**
     * Initialize pool with warm containers
     */
    @PostConstruct
    public void initialize() {

        for (int i = 0; i < warmMinSize; i++) {
            ContainerHandle handle = new ContainerHandle(UUID.randomUUID().toString(), Instant.now());
            availableContainers.offer(handle);
            allContainers.add(handle);
            containerLastUsed.put(handle, System.currentTimeMillis());
        }

        log.info("Container pool initialized with {} containers", warmMinSize);
    }

    /**
     * Acquire a container from the pool
     * 
     * @param timeout Duration to wait for available container
     * @return ContainerHandle
     * @throws ContainerAcquisitionException if no container available within timeout
     */
    public ContainerHandle acquire(java.time.Duration timeout) throws ContainerAcquisitionException {
        try {
            ContainerHandle container = availableContainers.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (container == null) {
                log.warn("Container acquisition timeout: no containers available");
                throw new ContainerAcquisitionException("No container available within timeout");
            }

            containerLastUsed.put(container, System.currentTimeMillis());
            log.debug("Container acquired: {}", container.getId());
            return container;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerAcquisitionException("Container acquisition interrupted", e);
        }
    }

    /**
     * Release a container back to the pool
     * 
     * @param container ContainerHandle to release
     */
    public void release(ContainerHandle container) {
        if (container == null) {
            return;
        }

        try {
            availableContainers.offer(container);
            containerLastUsed.put(container, System.currentTimeMillis());
            log.debug("Container released: {}", container.getId());
        } catch (Exception e) {
            log.error("Error releasing container: {}", container.getId(), e);
        }
    }

    /**
     * Get current pool statistics
     * 
     * @return Map with available and total container counts
     */
    public Map<String, Integer> getPoolStats() {
        return Map.of(
                "available", availableContainers.size(),
                "total", allContainers.size(),
                "max", maxSize
        );
    }

    /**
     * ContainerHandle — Represents a container in the pool
     */
    public static class ContainerHandle {
        private final String id;
        private final Instant createdAt;

        public ContainerHandle(String id, Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        public String getId() {
            return id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContainerHandle that = (ContainerHandle) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "ContainerHandle{" + "id='" + id + '\'' + '}';
        }
    }

}
