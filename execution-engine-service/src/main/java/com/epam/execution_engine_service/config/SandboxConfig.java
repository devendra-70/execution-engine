package com.epam.execution_engine_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sandbox Runtime Isolation Configuration
 * 
 * EPMICMPCOD-349: Enforce Sandbox Runtime Isolation
 * Maps all sandbox properties from application.properties
 * Provides JVM flags construction and safe defaults
 * 
 * Subtasks:
 * - EPMICMPCOD-451: JVM runtime flags and heap limits
 * - EPMICMPCOD-457: CPU, memory, and PID limits
 */
@Component
@ConfigurationProperties(prefix = "app.execution.sandbox")
public class SandboxConfig {

    private static final Logger logger = LoggerFactory.getLogger(SandboxConfig.class);

    // ==================== JVM Configuration ====================
    
    /**
     * JVM heap minimum size (Xms flag)
     * Default: 128m (minimal heap for startup)
     */
    private String jvmXms = "128m";
    
    /**
     * JVM heap maximum size (Xmx flag)
     * Default: 256m (fixed heap for sandbox)
     * Must be >= jvm-xms
     */
    private String jvmXmx = "256m";
    
    /**
     * Tiered compilation stop level
     * Default: 1 (disable C2 JIT, use only C1)
     * Reduces memory footprint, suitable for short-lived processes
     */
    private int jvmTieredStopLevel = 1;
    
    /**
     * Use Epsilon GC (No-Op GC)
     * Default: true
     * Suitable for 3000ms sandbox timeout (GC overhead not needed)
     */
    private boolean jvmUseEpsilonGc = true;
    
    /**
     * Shared Class Data Archive file path
     * Default: /sandbox/shared.jsa
     * CDS optimization for startup performance (~50ms reduction)
     */
    private String jvmSharedArchiveFile = "/sandbox/shared.jsa";
    
    /**
     * Always pre-touch heap pages
     * Default: true
     * Avoids page fault latency during execution
     */
    private boolean jvmAlwaysPreTouch = true;
    
    /**
     * JVM thread stack size in kilobytes
     * Default: 256 (reduces per-thread memory overhead)
     * Format: -Xss${jvm-thread-stack-kb}k
     */
    private int jvmThreadStackKb = 256;
    
    // ==================== Resource Limits ====================
    
    /**
     * Total memory limit for sandbox container (MB)
     * Default: 288 (256 MB JVM heap + 32 MB overhead)
     * Docker: --memory ${memory-limit-mb}m
     */
    private int memoryLimitMb = 288;
    
    /**
     * CPU limit in milliseconds per second
     * Default: 1000 (100% utilization if 1 core)
     * Docker: --cpus (1000ms = 1 CPU)
     */
    private long cpuLimitMillis = 1000;
    
    /**
     * Maximum number of processes per sandbox
     * Default: 512 (allow multiple threads + child processes)
     * Docker: --pids-limit ${pid-limit}
     */
    private int pidLimit = 512;
    
    /**
     * Host path to the seccomp profile JSON.
     * Default: empty string — means "unconfined" (no seccomp filtering).
     * Set via SECCOMP_PROFILE_PATH env var in production.
     */
    private String seccompProfilePath = "";
    
    // ==================== Getters and Setters ====================
    
    public String getJvmXms() {
        return jvmXms;
    }
    
    public void setJvmXms(String jvmXms) {
        this.jvmXms = jvmXms != null ? jvmXms : "128m";
    }
    
    public String getJvmXmx() {
        return jvmXmx;
    }
    
    public void setJvmXmx(String jvmXmx) {
        this.jvmXmx = jvmXmx != null ? jvmXmx : "256m";
    }
    
    public int getJvmTieredStopLevel() {
        return jvmTieredStopLevel;
    }
    
    public void setJvmTieredStopLevel(int jvmTieredStopLevel) {
        if (jvmTieredStopLevel < 0 || jvmTieredStopLevel > 4) {
            throw new IllegalArgumentException("JVM tiered stop level must be between 0 and 4, got: " + jvmTieredStopLevel);
        }
        this.jvmTieredStopLevel = jvmTieredStopLevel;
    }
    
    public boolean isJvmUseEpsilonGc() {
        return jvmUseEpsilonGc;
    }
    
    public void setJvmUseEpsilonGc(boolean jvmUseEpsilonGc) {
        this.jvmUseEpsilonGc = jvmUseEpsilonGc;
    }
    
    public String getJvmSharedArchiveFile() {
        return jvmSharedArchiveFile;
    }
    
    public void setJvmSharedArchiveFile(String jvmSharedArchiveFile) {
        // Validate path safety to prevent path traversal attacks
        if (jvmSharedArchiveFile != null) {
            if (jvmSharedArchiveFile.contains("..")) {
                throw new IllegalArgumentException(
                    "JVM shared archive file path cannot contain '..', got: " + jvmSharedArchiveFile);
            }
        }
        this.jvmSharedArchiveFile = jvmSharedArchiveFile != null ? jvmSharedArchiveFile : "/sandbox/shared.jsa";
    }
    
    public boolean isJvmAlwaysPreTouch() {
        return jvmAlwaysPreTouch;
    }
    
    public void setJvmAlwaysPreTouch(boolean jvmAlwaysPreTouch) {
        this.jvmAlwaysPreTouch = jvmAlwaysPreTouch;
    }
    
    public int getJvmThreadStackKb() {
        return jvmThreadStackKb;
    }
    
    public void setJvmThreadStackKb(int jvmThreadStackKb) {
        if (jvmThreadStackKb <= 0) {
            throw new IllegalArgumentException("JVM thread stack size must be positive, got: " + jvmThreadStackKb);
        }
        this.jvmThreadStackKb = jvmThreadStackKb;
    }
    
    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }
    
    public void setMemoryLimitMb(int memoryLimitMb) {
        if (memoryLimitMb <= 0) {
            throw new IllegalArgumentException("Memory limit must be positive, got: " + memoryLimitMb);
        }
        this.memoryLimitMb = memoryLimitMb;
    }
    
    public long getCpuLimitMillis() {
        return cpuLimitMillis;
    }
    
    public void setCpuLimitMillis(long cpuLimitMillis) {
        if (cpuLimitMillis <= 0) {
            throw new IllegalArgumentException("CPU limit must be positive, got: " + cpuLimitMillis);
        }
        this.cpuLimitMillis = cpuLimitMillis;
    }
    
    public int getPidLimit() {
        return pidLimit;
    }
    
    public void setPidLimit(int pidLimit) {
        if (pidLimit <= 0) {
            throw new IllegalArgumentException("PID limit must be positive, got: " + pidLimit);
        }
        this.pidLimit = pidLimit;
    }

    public String getSeccompProfilePath() {
        return seccompProfilePath;
    }

    public void setSeccompProfilePath(String seccompProfilePath) {
        this.seccompProfilePath = seccompProfilePath != null ? seccompProfilePath : "";
    }

    public boolean isSeccompEnabled() {
        return seccompProfilePath != null && !seccompProfilePath.isBlank();
    }
    
    // ==================== JVM Flags Builder ====================
    
    /**
     * Constructs complete JVM_FLAGS string from configured properties
     * 
     * Example output:
     * -Xms128m -Xmx256m -XX:TieredStopAtLevel=1 -XX:+UseEpsilonGC 
     * -XX:SharedArchiveFile=/sandbox/shared.jsa -XX:+AlwaysPreTouch -Xss256k
     * 
     * @return JVM flags string ready for use in docker run -e JVM_FLAGS="..."
     */
    public String buildJvmFlags() {
        StringBuilder flags = new StringBuilder();
        
        // Memory configuration
        flags.append("-Xms").append(jvmXms).append(" ");
        flags.append("-Xmx").append(jvmXmx).append(" ");
        
        // JIT compilation configuration
        flags.append("-XX:TieredStopAtLevel=").append(jvmTieredStopLevel).append(" ");
        
        // Garbage collection
        if (jvmUseEpsilonGc) {
            flags.append("-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC").append(" ");
        }
        
        // Shared class data archive (append if path is configured; existence checked at runtime)
        if (jvmSharedArchiveFile != null && !jvmSharedArchiveFile.isBlank()) {
            flags.append("-XX:SharedArchiveFile=").append(jvmSharedArchiveFile).append(" ");
        }
        
        // Heap pre-touching
        if (jvmAlwaysPreTouch) {
            flags.append("-XX:+AlwaysPreTouch").append(" ");
        }
        
        // Thread stack size
        flags.append("-Xss").append(jvmThreadStackKb).append("k");
        
        return flags.toString().trim();
    }
    
    /**
     * Constructs Docker memory limit flag
     * Format: --memory ${memory-limit-mb}m
     */
    public String buildDockerMemoryLimit() {
        return "--memory " + memoryLimitMb + "m";
    }
    
    /**
     * Constructs Docker CPU limit flag
     * Format: --cpus ${cpu-limit-millis / 1000.0}
     */
    public String buildDockerCpuLimit() {
        double cpus = cpuLimitMillis / 1000.0;
        return "--cpus " + cpus;
    }
    
    /**
     * Constructs Docker PID limit flag
     * Format: --pids-limit ${pid-limit}
     */
    public String buildDockerPidLimit() {
        return "--pids-limit " + pidLimit;
    }
    
    /**
     * Validates configuration consistency after all properties are bound
     * Ensures JVM heap fits within container memory limit to prevent OOM
     * Logs warnings if configuration is suboptimal but allows tests to proceed
     */
    @PostConstruct
    public void validateHeapConsistency() {
        try {
            // Extract numeric value from heap flags (e.g., "256m" -> 256)
            int heapMb = parseMemoryValue(jvmXmx);
            
            if (heapMb > memoryLimitMb) {
                logger.warn(
                    "Heap size ({} MB) exceeds container memory limit ({} MB). " +
                    "Container may experience OOM. Recommended: heap <= memory * 0.9",
                    heapMb, memoryLimitMb);
            }
        } catch (Exception e) {
            logger.warn("Could not validate heap consistency: {}", e.getMessage());
        }
    }
    
    /**
     * Parses memory value with unit suffix (e.g., "256m" -> 256, "1g" -> 1024)
     * 
     * @param memoryValue memory string with unit (m, g, k)
     * @return parsed value in megabytes
     * @throws IllegalArgumentException if format is invalid
     */
    private int parseMemoryValue(String memoryValue) {
        if (memoryValue == null || memoryValue.isEmpty()) {
            return 0;
        }
        
        String value = memoryValue.toLowerCase().trim();
        try {
            if (value.endsWith("g")) {
                return Integer.parseInt(value.substring(0, value.length() - 1)) * 1024;
            } else if (value.endsWith("m")) {
                return Integer.parseInt(value.substring(0, value.length() - 1));
            } else if (value.endsWith("k")) {
                return Integer.parseInt(value.substring(0, value.length() - 1)) / 1024;
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid memory format: " + memoryValue + ". Expected format like '256m' or '1g'", e);
        }
    }
    
    @Override
    public String toString() {
        return "SandboxConfig{" +
                "jvmXms='" + jvmXms + '\'' +
                ", jvmXmx='" + jvmXmx + '\'' +
                ", jvmTieredStopLevel=" + jvmTieredStopLevel +
                ", jvmUseEpsilonGc=" + jvmUseEpsilonGc +
                ", jvmSharedArchiveFile='" + jvmSharedArchiveFile + '\'' +
                ", jvmAlwaysPreTouch=" + jvmAlwaysPreTouch +
                ", jvmThreadStackKb=" + jvmThreadStackKb +
                ", memoryLimitMb=" + memoryLimitMb +
                ", cpuLimitMillis=" + cpuLimitMillis +
                ", pidLimit=" + pidLimit +
                '}';
    }
}
