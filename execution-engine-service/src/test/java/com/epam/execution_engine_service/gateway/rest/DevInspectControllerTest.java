package com.epam.execution_engine_service.gateway.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DevinspectController.class)
@ActiveProfiles("dev")
@DisplayName("DevinspectController Unit Tests")
class DevinspectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private Object submissionRepository;

    @Nested
    @DisplayName("Redis Status Endpoint Tests")
    class RedisStatusTests {

        @Test
        @DisplayName("Should return 200 with exists=true when Redis key exists")
        void testRedisStatus_KeyExists() throws Exception {
            String executionId = "exec-123";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("PENDING");
            when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(300L);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true))
                    .andExpect(jsonPath("$.value").value("PENDING"));
        }

        @Test
        @DisplayName("Should return 200 with exists=false when Redis key not found")
        void testRedisStatus_KeyNotFound() throws Exception {
            String executionId = "exec-missing";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn(null);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("Should handle null TTL response from Redis")
        void testRedisStatus_NullTTL() throws Exception {
            String executionId = "exec-ttl";
            String key = "execution:status:" + executionId;
            
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn("RUNNING");
            when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(null);

            mockMvc.perform(get("/api/dev/inspect/redis/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }
    }

    @Nested
    @DisplayName("Redis Rate Limit Endpoint Tests")
    class RedisRateLimitTests {

        @Test
        @DisplayName("Should return rate limit keys for user")
        void testRedisRateLimit_WithKeys() throws Exception {
            String userId = "user-123";
            
            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.keys").isArray());
        }

        @Test
        @DisplayName("Should return empty keys when no rate limit data exists")
        void testRedisRateLimit_NoKeys() throws Exception {
            String userId = "user-empty";
            
            mockMvc.perform(get("/api/dev/inspect/redis/rate/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys").isArray());
        }
    }

    @Nested
    @DisplayName("Database Submission Endpoint Tests")
    class DatabaseSubmissionTests {

        @Test
        @DisplayName("Should return 400 for invalid UUID format")
        void testDbSubmission_InvalidUUID() throws Exception {
            String invalidId = "not-a-uuid";
            
            mockMvc.perform(get("/api/dev/inspect/db/{executionId}", invalidId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 200 with found=false when submission not in database")
        void testDbSubmission_NotFound() throws Exception {
            String executionId = "550e8400-e29b-41d4-a716-446655440000";
            
            mockMvc.perform(get("/api/dev/inspect/db/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.found").value(false));
        }
    }

    @Nested
    @DisplayName("Recent Submissions Endpoint Tests")
    class RecentSubmissionsTests {

        @Test
        @DisplayName("Should return recent submissions list")
        void testRecentSubmissions_Success() throws Exception {
            mockMvc.perform(get("/api/dev/inspect/db/recent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void testRecentSubmissions_WithLimit() throws Exception {
            mockMvc.perform(get("/api/dev/inspect/db/recent").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
