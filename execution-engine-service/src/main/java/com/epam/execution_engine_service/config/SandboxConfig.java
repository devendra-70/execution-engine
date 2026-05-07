package com.epam.execution_engine_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
        this.jvmThreadStackKb = jvmThreadStackKb;
    }
    
    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }
    
    public void setMemoryLimitMb(int memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }
    
    public long getCpuLimitMillis() {
        return cpuLimitMillis;
    }
    
    public void setCpuLimitMillis(long cpuLimitMillis) {
        this.cpuLimitMillis = cpuLimitMillis;
    }
    
    public int getPidLimit() {
        return pidLimit;
    }
    
    public void setPidLimit(int pidLimit) {
        this.pidLimit = pidLimit;
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
            flags.append("-XX:+UseEpsilonGC").append(" ");
        }
        
        // Shared class data archive
        flags.append("-XX:SharedArchiveFile=").append(jvmSharedArchiveFile).append(" ");
        
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
