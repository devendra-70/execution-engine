package com.epam.execution_engine_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JVM Flags Verification Tests
 * 
 * Tests the SandboxConfig JVM flags builder and default properties.
 * Covers: Xms, Xmx, TieredStopLevel, UseEpsilonGC, CDS, AlwaysPreTouch, thread stack
 * Target: ~20 tests covering JVM configuration and property defaults
 */
@DisplayName("JVM Flags Verification Tests")
class SandboxConfigTest {

    private final SandboxConfig config = new SandboxConfig();

    // ========== JVM Heap Configuration Tests ==========

    @Test
    @DisplayName("buildJvmFlags_defaultHeap_includesXmsAndXmx")
    void buildJvmFlags_defaultHeap_includesXmsAndXmx() {
        String flags = config.buildJvmFlags();
        
        assertAll(
            () -> assertTrue(flags.contains("-Xms128m"), "Default Xms should be 128m"),
            () -> assertTrue(flags.contains("-Xmx256m"), "Default Xmx should be 256m")
        );
    }

    @Test
    @DisplayName("buildJvmFlags_customHeap_includesCustomValues")
    void buildJvmFlags_customHeap_includesCustomValues() {
        config.setJvmXms("256m");
        config.setJvmXmx("512m");
        String flags = config.buildJvmFlags();
        
        assertAll(
            () -> assertTrue(flags.contains("-Xms256m"), "Custom Xms should be included"),
            () -> assertTrue(flags.contains("-Xmx512m"), "Custom Xmx should be included")
        );
    }

    @Test
    @DisplayName("setJvmXms_nullValue_usesDefault")
    void setJvmXms_nullValue_usesDefault() {
        config.setJvmXms(null);
        
        assertEquals("128m", config.getJvmXms(), "Null should result in default 128m");
    }

    @Test
    @DisplayName("setJvmXmx_nullValue_usesDefault")
    void setJvmXmx_nullValue_usesDefault() {
        config.setJvmXmx(null);
        
        assertEquals("256m", config.getJvmXmx(), "Null should result in default 256m");
    }

    // ========== JVM Compilation Configuration Tests ==========

    @Test
    @DisplayName("buildJvmFlags_tieredCompilation_disabledByDefault")
    void buildJvmFlags_tieredCompilation_disabledByDefault() {
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:TieredStopAtLevel=1"), 
            "TieredStopAtLevel should be set to 1 by default");
    }

    @Test
    @DisplayName("buildJvmFlags_customTieredLevel_appliedCorrectly")
    void buildJvmFlags_customTieredLevel_appliedCorrectly() {
        config.setJvmTieredStopLevel(4);
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:TieredStopAtLevel=4"), 
            "Custom TieredStopAtLevel should be applied");
    }

    // ========== Garbage Collection Configuration Tests ==========

    @Test
    @DisplayName("buildJvmFlags_epsilonGc_enabledByDefault")
    void buildJvmFlags_epsilonGc_enabledByDefault() {
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:+UseEpsilonGC"), 
            "Epsilon GC should be enabled by default for short-lived processes");
    }

    @Test
    @DisplayName("buildJvmFlags_epsilonGc_disabledWhenSet")
    void buildJvmFlags_epsilonGc_disabledWhenSet() {
        config.setJvmUseEpsilonGc(false);
        String flags = config.buildJvmFlags();
        
        assertFalse(flags.contains("-XX:+UseEpsilonGC"), 
            "Epsilon GC should not appear when disabled");
    }

    // ========== CDS (Shared Class Data Archive) Tests ==========

    @Test
    @DisplayName("buildJvmFlags_cdsArchive_defaultPath")
    void buildJvmFlags_cdsArchive_defaultPath() {
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:SharedArchiveFile=/sandbox/shared.jsa"), 
            "CDS archive should default to /sandbox/shared.jsa");
    }

    @Test
    @DisplayName("buildJvmFlags_cdsArchive_customPath")
    void buildJvmFlags_cdsArchive_customPath() {
        config.setJvmSharedArchiveFile("/custom/cds.jsa");
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:SharedArchiveFile=/custom/cds.jsa"), 
            "Custom CDS path should be applied");
    }

    @Test
    @DisplayName("setJvmSharedArchiveFile_nullValue_usesDefault")
    void setJvmSharedArchiveFile_nullValue_usesDefault() {
        config.setJvmSharedArchiveFile(null);
        
        assertEquals("/sandbox/shared.jsa", config.getJvmSharedArchiveFile(), 
            "Null should result in default CDS path");
    }

    // ========== Heap Pre-touching Tests ==========

    @Test
    @DisplayName("buildJvmFlags_heapPreTouch_enabledByDefault")
    void buildJvmFlags_heapPreTouch_enabledByDefault() {
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-XX:+AlwaysPreTouch"), 
            "Heap pre-touching should be enabled by default");
    }

    @Test
    @DisplayName("buildJvmFlags_heapPreTouch_disabledWhenSet")
    void buildJvmFlags_heapPreTouch_disabledWhenSet() {
        config.setJvmAlwaysPreTouch(false);
        String flags = config.buildJvmFlags();
        
        assertFalse(flags.contains("-XX:+AlwaysPreTouch"), 
            "Pre-touching should not appear when disabled");
    }

    // ========== Thread Stack Configuration Tests ==========

    @Test
    @DisplayName("buildJvmFlags_threadStack_defaultSize")
    void buildJvmFlags_threadStack_defaultSize() {
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-Xss256k"), 
            "Thread stack should default to 256k");
    }

    @Test
    @DisplayName("buildJvmFlags_threadStack_customSize")
    void buildJvmFlags_threadStack_customSize() {
        config.setJvmThreadStackKb(512);
        String flags = config.buildJvmFlags();
        
        assertTrue(flags.contains("-Xss512k"), 
            "Custom thread stack size should be applied");
    }

    // ========== Docker Memory Limit Tests ==========

    @Test
    @DisplayName("buildDockerMemoryLimit_defaultValue_288m")
    void buildDockerMemoryLimit_defaultValue_288m() {
        String limit = config.buildDockerMemoryLimit();
        
        assertEquals("--memory 288m", limit, 
            "Memory limit should default to 288m");
    }

    @Test
    @DisplayName("buildDockerMemoryLimit_customValue_formatted")
    void buildDockerMemoryLimit_customValue_formatted() {
        config.setMemoryLimitMb(512);
        String limit = config.buildDockerMemoryLimit();
        
        assertEquals("--memory 512m", limit, 
            "Memory limit should be formatted as --memory <value>m");
    }

    // ========== Docker CPU Limit Tests ==========

    @Test
    @DisplayName("buildDockerCpuLimit_defaultValue_1cpu")
    void buildDockerCpuLimit_defaultValue_1cpu() {
        String limit = config.buildDockerCpuLimit();
        
        assertEquals("--cpus 1.0", limit, 
            "CPU limit should default to 1.0 (1000ms = 1 CPU)");
    }

    @Test
    @DisplayName("buildDockerCpuLimit_customValue_converted")
    void buildDockerCpuLimit_customValue_converted() {
        config.setCpuLimitMillis(500);
        String limit = config.buildDockerCpuLimit();
        
        assertEquals("--cpus 0.5", limit, 
            "CPU limit should convert milliseconds to CPU cores (500ms = 0.5 CPUs)");
    }

    // ========== Docker PID Limit Tests ==========

    @Test
    @DisplayName("buildDockerPidLimit_defaultValue_512")
    void buildDockerPidLimit_defaultValue_512() {
        String limit = config.buildDockerPidLimit();
        
        assertEquals("--pids-limit 512", limit, 
            "PID limit should default to 512");
    }

    @Test
    @DisplayName("buildDockerPidLimit_customValue_formatted")
    void buildDockerPidLimit_customValue_formatted() {
        config.setPidLimit(256);
        String limit = config.buildDockerPidLimit();
        
        assertEquals("--pids-limit 256", limit, 
            "PID limit should be formatted as --pids-limit <value>");
    }

    // ========== Config Integration Tests ==========

    @Test
    @DisplayName("buildJvmFlags_allDefaults_completeString")
    void buildJvmFlags_allDefaults_completeString() {
        String flags = config.buildJvmFlags();
        
        assertAll(
            () -> assertFalse(flags.isBlank(), "JVM flags should not be blank"),
            () -> assertTrue(flags.contains("-Xms"), "Should contain heap min"),
            () -> assertTrue(flags.contains("-Xmx"), "Should contain heap max"),
            () -> assertTrue(flags.contains("-XX:"), "Should contain XX options"),
            () -> assertTrue(flags.contains("-Xss"), "Should contain thread stack")
        );
    }
}
