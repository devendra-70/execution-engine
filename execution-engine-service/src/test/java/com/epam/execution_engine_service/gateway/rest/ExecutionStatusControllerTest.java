package com.epam.execution_engine_service.gateway.rest;

import com.epam.execution_engine_service.gateway.security.JwtTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionStatusController.class)
@DisplayName("ExecutionStatusController Unit Tests")
class ExecutionStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenValidator jwtTokenValidator;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("Get Status Endpoint Tests")
    class GetStatusTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with status when execution found in Redis")
        void testGetStatus_Found() throws Exception {
            String executionId = "exec-123";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(executionId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 when execution status not found in Redis")
        void testGetStatus_NotFound() throws Exception {
            String executionId = "exec-456";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn(null);

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return COMPLETED status when execution is complete")
        void testGetStatus_CompletedStatus() throws Exception {
            String executionId = "exec-completed";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("COMPLETED");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return RUNNING status when execution is running")
        void testGetStatus_RunningStatus() throws Exception {
            String executionId = "exec-running";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("RUNNING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle UUID format executionId")
        void testGetStatus_UUIDFormat() throws Exception {
            String executionId = "550e8400-e29b-41d4-a716-446655440000";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(executionId));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return response with executionId and status fields")
        void testGetStatus_ResponseFields() throws Exception {
            String executionId = "exec-fields";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").exists())
                    .andExpect(jsonPath("$.status").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle special characters in executionId")
        void testGetStatus_SpecialCharacterId() throws Exception {
            String executionId = "exec-123-test";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(executionId));
        }

        @Test
        @WithMockUser
        @DisplayName("Should verify correct Redis key is used")
        void testGetStatus_ExactKeyFormat() throws Exception {
            String executionId = "test-exec";
            String expectedKey = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(expectedKey)).thenReturn("PENDING");

            mockMvc.perform(get("/api/executions/{executionId}/status", executionId))
                    .andExpect(status().isOk());

            verify(valueOps).get(expectedKey);
        }
    }
}
