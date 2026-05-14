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
 * Container Security Tests
 * 
 * Verifies non-root user execution, read-only filesystem, and capability restrictions.
 * Tests the Docker command builder for security layer enforcement.
 * Target: ~15 tests covering all security layers
 */
@DisplayName("Container Security Tests")
@ExtendWith(MockitoExtension.class)
class ContainerSpawnerSecurityTest {

    @Mock
    private SandboxConfig sandboxConfig;

    @InjectMocks
    private ContainerSpawner containerSpawner;

    private static final String SANDBOX_USER_UID = "65534";
    private static final String SANDBOX_USER_GID = "65534";
    private static final String SANDBOX_IMAGE = "sandbox:latest";

    @BeforeEach
    void setUp() {
        // Configure default mock behavior for resource limits
        when(sandboxConfig.buildDockerMemoryLimit()).thenReturn("--memory 288m");
        when(sandboxConfig.buildDockerCpuLimit()).thenReturn("--cpus 1.0");
        when(sandboxConfig.buildDockerPidLimit()).thenReturn("--pids-limit 512");
        when(sandboxConfig.buildJvmFlags()).thenReturn("-Xms128m -Xmx256m");
    }

    // ========== Non-root User Execution Tests ==========

    @Test
    @DisplayName("buildDockerCommand_nonRootUser_uid65534")
    void buildDockerCommand_nonRootUser_uid65534() {
        List<String> cmd = getDockerCommand("test code");
        
        int userIndex = cmd.indexOf("--user");
        assertNotEquals(-1, userIndex, "Docker command should contain --user flag");
        assertTrue(cmd.get(userIndex + 1).contains("65534"), 
            "User should be UID 65534 (nobody user)");
    }

    @Test
    @DisplayName("buildDockerCommand_nonRootUser_gid65534")
    void buildDockerCommand_nonRootUser_gid65534() {
        List<String> cmd = getDockerCommand("test code");
        
        int userIndex = cmd.indexOf("--user");
        String userSpec = cmd.get(userIndex + 1);
        assertTrue(userSpec.contains("65534:65534"), 
            "Both UID and GID should be 65534 (nobody:nobody)");
    }

    // ========== Read-only Filesystem Tests ==========

    @Test
    @DisplayName("buildDockerCommand_readOnlyFilesystem_flagPresent")
    void buildDockerCommand_readOnlyFilesystem_flagPresent() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--read-only"), 
            "Docker command should contain --read-only flag");
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsMount_tmp")
    void buildDockerCommand_tmpfsMount_tmp() {
        List<String> cmd = getDockerCommand("test code");
        
        int tmpfsIndex = cmd.indexOf("--tmpfs");
        assertNotEquals(-1, tmpfsIndex, "Docker command should contain --tmpfs");
        assertTrue(cmd.get(tmpfsIndex + 1).startsWith("/tmp"), 
            "First tmpfs mount should be for /tmp");
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsSecurityFlags_noexecNodevNosuid")
    void buildDockerCommand_tmpfsSecurityFlags_noexecNodevNosuid() {
        List<String> cmd = getDockerCommand("test code");
        
        int tmpfsIndex = cmd.indexOf("--tmpfs");
        String tmpfsFlags = cmd.get(tmpfsIndex + 1);
        assertAll(
            () -> assertTrue(tmpfsFlags.contains("noexec"), "tmpfs should have noexec"),
            () -> assertTrue(tmpfsFlags.contains("nodev"), "tmpfs should have nodev"),
            () -> assertTrue(tmpfsFlags.contains("nosuid"), "tmpfs should have nosuid")
        );
    }

    @Test
    @DisplayName("buildDockerCommand_tmpfsMount_sandbox")
    void buildDockerCommand_tmpfsMount_sandbox() {
        List<String> cmd = getDockerCommand("test code");
        
        // Find all tmpfs mounts
        int count = 0;
        for (int i = 0; i < cmd.size(); i++) {
            if (cmd.get(i).equals("--tmpfs")) {
                if (cmd.get(i + 1).contains("/sandbox")) {
                    count++;
                }
            }
        }
        assertTrue(count > 0, "Docker command should have tmpfs mount for /sandbox");
    }

    // ========== Capability Restriction Tests ==========

    @Test
    @DisplayName("buildDockerCommand_capabilities_dropAll")
    void buildDockerCommand_capabilities_dropAll() {
        List<String> cmd = getDockerCommand("test code");
        
        int capIndex = cmd.indexOf("--cap-drop=ALL");
        assertNotEquals(-1, capIndex, "All capabilities should be dropped");
    }

    @Test
    @DisplayName("buildDockerCommand_capabilities_addKill")
    void buildDockerCommand_capabilities_addKill() {
        List<String> cmd = getDockerCommand("test code");
        
        int capIndex = cmd.indexOf("--cap-add=KILL");
        assertNotEquals(-1, capIndex, "KILL capability should be added for thread signaling");
    }

    // ========== Privilege Escalation Prevention Tests ==========

    @Test
    @DisplayName("buildDockerCommand_noNewPrivileges_enforced")
    void buildDockerCommand_noNewPrivileges_enforced() {
        List<String> cmd = getDockerCommand("test code");
        
        assertTrue(cmd.contains("--security-opt"), 
            "Security options should be specified");
        int securityIndex = cmd.indexOf("--security-opt");
        assertTrue(cmd.contains("no-new-privileges:true"), 
            "no-new-privileges should be enforced");
    }

    // ========== Seccomp Profile Tests ==========

    @Test
    @DisplayName("buildDockerCommand_seccompProfile_applied")
    void buildDockerCommand_seccompProfile_applied() {
        // Enable seccomp and set profile path so the profile is injected into the command
        when(sandboxConfig.isSeccompEnabled()).thenReturn(true);
        when(sandboxConfig.getSeccompProfilePath()).thenReturn("/etc/seccomp/seccomp-profile.json");

        List<String> cmd = getDockerCommand("test code");

        assertTrue(cmd.contains("--security-opt"),
            "Security options should include seccomp");
        String seccompArg = null;
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (cmd.get(i).equals("--security-opt") && cmd.get(i + 1).startsWith("seccomp=")) {
                seccompArg = cmd.get(i + 1);
                break;
            }
        }
        assertNotNull(seccompArg, "Seccomp profile should be applied");
        assertTrue(seccompArg.contains("seccomp-profile.json"),
            "Seccomp should use seccomp-profile.json");
    }

    // ========== Docker Command Structure Tests ==========

    @Test
    @DisplayName("buildDockerCommand_dockerBinary_first")
    void buildDockerCommand_dockerBinary_first() {
        List<String> cmd = getDockerCommand("test code");
        
        assertEquals("docker", cmd.get(0), "First argument should be 'docker'");
    }

    @Test
    @DisplayName("buildDockerCommand_runCommand_second")
    void buildDockerCommand_runCommand_second() {
        List<String> cmd = getDockerCommand("test code");
        
        assertEquals("run", cmd.get(1), "Second argument should be 'run'");
    }

    @Test
    @DisplayName("buildDockerCommand_imageSpecified_last")
    void buildDockerCommand_imageSpecified_last() {
        List<String> cmd = getDockerCommand("test code");
        
        String lastArg = cmd.get(cmd.size() - 1);
        assertTrue(lastArg.contains("sandbox"), 
            "Last argument should specify the sandbox image");
    }

    // ========== Helper Methods ==========

    private List<String> getDockerCommand(String code) {
        // Use reflection to access buildDockerCommand method
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
