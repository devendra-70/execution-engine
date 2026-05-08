package com.epam.execution_engine_service.orchestrator;

import com.epam.execution_engine_service.config.SandboxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Resource Limits Tests
 * 
 * Verifies memory, CPU, and PID limits are correctly applied to containers.
 * Tests the resource limit configuration propagation through Docker flags.
 * Target: ~12 tests covering resource limit enforcement
 */
@DisplayName("Resource Limits Tests")
@ExtendWith(MockitoExtension.class)
class ContainerSpawnerResourcesTest {

    @Mock
    private SandboxConfig sandboxConfig;

    @InjectMocks
    private ContainerSpawner containerSpawner;

    @BeforeEach
    void setUp() {
        when(sandboxConfig.buildDockerMemoryLimit()).thenReturn("--memory 288m");
        when(sandboxConfig.buildDockerCpuLimit()).thenReturn("--cpus 1.0");
        when(sandboxConfig.buildDockerPidLimit()).thenReturn("--pids-limit 512");
        when(sandboxConfig.buildJvmFlags()).thenReturn("-Xms128m -Xmx256m");
    }

    // ========== Memory Limit Tests ==========

    @Test
    @DisplayName("buildDockerCommand_memoryLimit_applied")
    void buildDockerCommand_memoryLimit_applied() {
        List<String> cmd = getDockerCommand("test code");
        
        boolean hasMemoryLimit = cmd.stream().anyMatch(arg -> arg.contains("--memory"));
        assertTrue(hasMemoryLimit, "Memory limit flag should be present");
    }

    @Test
    @DisplayName("buildDockerCommand_memoryLimit_value288m")
    void buildDockerCommand_memoryLimit_value288m() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--memory 288m"), 
            "Memory limit should use SandboxConfig's buildDockerMemoryLimit()");
    }

    @Test
    @DisplayName("buildDockerCommand_memoryLimit_custom512m")
    void buildDockerCommand_memoryLimit_custom512m() {
        when(sandboxConfig.buildDockerMemoryLimit()).thenReturn("--memory 512m");
        
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--memory 512m"), 
            "Custom memory limit should be applied from SandboxConfig");
    }

    @Test
    @DisplayName("buildDockerCommand_memoryLimit_callsConfigBuilder")
    void buildDockerCommand_memoryLimit_callsConfigBuilder() {
        getDockerCommand("test code");
        
        verify(sandboxConfig, times(1)).buildDockerMemoryLimit();
    }

    // ========== CPU Limit Tests ==========

    @Test
    @DisplayName("buildDockerCommand_cpuLimit_applied")
    void buildDockerCommand_cpuLimit_applied() {
        List<String> cmd = getDockerCommand("test code");
        
        boolean hasCpuLimit = cmd.stream().anyMatch(arg -> arg.contains("--cpus"));
        assertTrue(hasCpuLimit, "CPU limit flag should be present");
    }

    @Test
    @DisplayName("buildDockerCommand_cpuLimit_value1cpu")
    void buildDockerCommand_cpuLimit_value1cpu() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--cpus 1.0"), 
            "CPU limit should use SandboxConfig's buildDockerCpuLimit()");
    }

    @Test
    @DisplayName("buildDockerCommand_cpuLimit_customValue")
    void buildDockerCommand_cpuLimit_customValue() {
        when(sandboxConfig.buildDockerCpuLimit()).thenReturn("--cpus 0.5");
        
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--cpus 0.5"), 
            "Custom CPU limit should be applied from SandboxConfig");
    }

    @Test
    @DisplayName("buildDockerCommand_cpuLimit_callsConfigBuilder")
    void buildDockerCommand_cpuLimit_callsConfigBuilder() {
        getDockerCommand("test code");
        
        verify(sandboxConfig, times(1)).buildDockerCpuLimit();
    }

    // ========== PID Limit Tests ==========

    @Test
    @DisplayName("buildDockerCommand_pidLimit_value512")
    void buildDockerCommand_pidLimit_value512() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--pids-limit 512"), 
            "PID limit should use SandboxConfig's buildDockerPidLimit()");
    }

    @Test
    @DisplayName("buildDockerCommand_pidLimit_customValue")
    void buildDockerCommand_pidLimit_customValue() {
        when(sandboxConfig.buildDockerPidLimit()).thenReturn("--pids-limit 256");
        
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--pids-limit 256"), 
            "Custom PID limit should be applied from SandboxConfig");
    }

    @Test
    @DisplayName("buildDockerCommand_pidLimit_callsConfigBuilder")
    void buildDockerCommand_pidLimit_callsConfigBuilder() {
        getDockerCommand("test code");
        
        verify(sandboxConfig, times(1)).buildDockerPidLimit();
    }

    // ========== Resource Limits Integration Tests ==========

    @Test
    @DisplayName("buildDockerCommand_allResourceLimits_present")
    void buildDockerCommand_allResourceLimits_present() {
        List<String> cmd = getDockerCommand("test code");
        
        assertAll(
            () -> assertTrue(cmd.contains("--memory 288m"), "Memory limit present"),
            () -> assertTrue(cmd.contains("--cpus 1.0"), "CPU limit present"),
            () -> assertTrue(cmd.contains("--pids-limit 512"), "PID limit present")
        );
    }

    @Test
    @DisplayName("buildDockerCommand_resourceLimits_allFromConfig")
    void buildDockerCommand_resourceLimits_allFromConfig() {
        getDockerCommand("test code");
        
        verify(sandboxConfig, times(1)).buildDockerMemoryLimit();
        verify(sandboxConfig, times(1)).buildDockerCpuLimit();
        verify(sandboxConfig, times(1)).buildDockerPidLimit();
    }

    // ========== Helper Methods ==========

    private List<String> getDockerCommand(String code) {
        try {
            var method = ContainerSpawner.class.getDeclaredMethod("buildDockerCommand", String.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(containerSpawner, code);
        } catch (Exception e) {
            fail("Failed to invoke buildDockerCommand: " + e.getMessage());
            return null;
        }
    }
}
