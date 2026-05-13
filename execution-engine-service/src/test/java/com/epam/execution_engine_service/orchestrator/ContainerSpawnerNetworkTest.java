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
 * Network Isolation Tests
 * 
 * Verifies network isolation enforcement including --net=none,
 * tmpfs mount security, and read-only filesystem layers.
 * Target: ~12 tests covering network and filesystem isolation
 */
@DisplayName("Network Isolation Tests")
@ExtendWith(MockitoExtension.class)
class ContainerSpawnerNetworkTest {

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

    // ========== Network Isolation Tests ==========

    @Test
    @DisplayName("buildDockerCommand_networkIsolation_noneMode")
    void buildDockerCommand_networkIsolation_noneMode() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--net=none"), 
            "Docker should disable network with --net=none");
    }

    @Test
    @DisplayName("buildDockerCommand_networkIsolation_beforeImage")
    void buildDockerCommand_networkIsolation_beforeImage() {
        List<String> cmd = getDockerCommand("test code");
        
        int netIndex = cmd.indexOf("--net=none");
        int imageIndex = -1;
        for (int i = cmd.size() - 1; i >= 0; i--) {
            if (cmd.get(i).contains("sandbox")) {
                imageIndex = i;
                break;
            }
        }
        assertTrue(netIndex < imageIndex, 
            "Network flag should come before image specification");
    }

    // ========== tmpfs Mount Tests ==========

    @Test
    @DisplayName("buildDockerCommand_tmpfsMounts_totalCount")
    void buildDockerCommand_tmpfsMounts_totalCount() {
        List<String> cmd = getDockerCommand("test code");
        
        int tmpfsCount = 0;
        for (String arg : cmd) {
            if (arg.equals("--tmpfs")) {
                tmpfsCount++;
            }
        }
        assertEquals(2, tmpfsCount, "Should have exactly 2 tmpfs mounts (/tmp and /sandbox)");
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsTmp_sizeLimit")
    void buildDockerCommand_tmpfsTmp_sizeLimit() {
        List<String> cmd = getDockerCommand("test code");
        
        boolean hasSizeLimit = false;
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--tmpfs") && cmd.get(i + 1).startsWith("/tmp")) {
                String tmpfsSpec = cmd.get(i + 1);
                assertTrue(tmpfsSpec.contains("size="), "tmpfs should have size limit");
                assertTrue(tmpfsSpec.contains("64m"), "tmpfs /tmp should be 64m");
                hasSizeLimit = true;
            }
        }
        assertTrue(hasSizeLimit, "Should find /tmp tmpfs with size specification");
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsSandbox_sizeLimit")
    void buildDockerCommand_tmpfsSandbox_sizeLimit() {
        List<String> cmd = getDockerCommand("test code");
        
        boolean hasSizeLimit = false;
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--tmpfs") && cmd.get(i + 1).startsWith("/sandbox")) {
                String tmpfsSpec = cmd.get(i + 1);
                assertTrue(tmpfsSpec.contains("size="), "tmpfs should have size limit");
                assertTrue(tmpfsSpec.contains("128m"), "tmpfs /sandbox should be 128m");
                hasSizeLimit = true;
            }
        }
        assertTrue(hasSizeLimit, "Should find /sandbox tmpfs with size specification");
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsFlags_noexec")
    void buildDockerCommand_tmpfsFlags_noexec() {
        List<String> cmd = getDockerCommand("test code");
        
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--tmpfs")) {
                String tmpfsSpec = cmd.get(i + 1);
                if (tmpfsSpec.startsWith("/tmp") || tmpfsSpec.startsWith("/sandbox")) {
                    assertTrue(tmpfsSpec.contains("noexec"), 
                        "tmpfs mounts should have noexec flag to prevent executable files");
                }
            }
        }
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsFlags_nodev")
    void buildDockerCommand_tmpfsFlags_nodev() {
        List<String> cmd = getDockerCommand("test code");
        
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--tmpfs")) {
                String tmpfsSpec = cmd.get(i + 1);
                if (tmpfsSpec.startsWith("/tmp") || tmpfsSpec.startsWith("/sandbox")) {
                    assertTrue(tmpfsSpec.contains("nodev"), 
                        "tmpfs mounts should have nodev flag to prevent device access");
                }
            }
        }
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsFlags_nosuid")
    void buildDockerCommand_tmpfsFlags_nosuid() {
        List<String> cmd = getDockerCommand("test code");
        
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--tmpfs")) {
                String tmpfsSpec = cmd.get(i + 1);
                if (tmpfsSpec.startsWith("/tmp") || tmpfsSpec.startsWith("/sandbox")) {
                    assertTrue(tmpfsSpec.contains("nosuid"), 
                        "tmpfs mounts should have nosuid flag to prevent setuid execution");
                }
            }
        }
    }

    // ========== Read-only Filesystem Tests ==========

    @Test
    @DisplayName("buildDockerCommand_readOnlyRoot_enforced")
    void buildDockerCommand_readOnlyRoot_enforced() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--read-only"), 
            "Root filesystem should be read-only to prevent modifications");
    }

    @Test
    @DisplayName("buildDockerCommand_readOnlyBeforeTmpfs")
    void buildDockerCommand_readOnlyBeforeTmpfs() {
        List<String> cmd = getDockerCommand("test code");
        
        int readOnlyIndex = cmd.indexOf("--read-only");
        int tmpfsIndex = cmd.indexOf("--tmpfs");
        assertTrue(readOnlyIndex < tmpfsIndex, 
            "Read-only flag should come before tmpfs mounts (applied in order)");
    }

    // ========== Layered Isolation Verification ==========

    @Test
    @DisplayName("buildDockerCommand_multipleIsolationLayers_present")
    void buildDockerCommand_multipleIsolationLayers_present() {
        List<String> cmd = getDockerCommand("test code");
        
        assertAll(
            () -> assertTrue(cmd.contains("--net=none"), "Network isolation present"),
            () -> assertTrue(cmd.contains("--read-only"), "Filesystem read-only present"),
            () -> assertTrue(cmd.contains("--tmpfs"), "tmpfs mounts present"),
            () -> assertTrue(cmd.contains("--cap-drop=ALL"), "Capability drop present"),
            () -> assertTrue(cmd.contains("--user"), "User isolation present")
        );
    }

    @Test
    @DisplayName("buildDockerCommand_commandOrder_logical")
    void buildDockerCommand_commandOrder_logical() {
        List<String> cmd = getDockerCommand("test code");
        
        // Verify command structure: docker run [flags] [image]
        assertEquals("docker", cmd.get(0), "First should be docker binary");
        assertEquals("run", cmd.get(1), "Second should be run command");
        
        // Image should be near the end
        String lastOrSecondLast = cmd.get(cmd.size() - 1);
        assertTrue(lastOrSecondLast.contains("sandbox"), "Image should be specified");
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
