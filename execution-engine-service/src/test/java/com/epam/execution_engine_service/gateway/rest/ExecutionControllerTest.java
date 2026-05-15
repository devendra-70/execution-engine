package com.epam.execution_engine_service.gateway.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
@TestPropertySource(properties = {
        "app.kafka.topic=execution-tasks",
        "app.redis.status-ttl-seconds=600"
})
@DisplayName("ExecutionController Unit Tests")
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate kafkaTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("Submit Execution Endpoint Tests")
    class SubmitExecutionTests {

        @Test
        @DisplayName("Should reject request without authentication")
        void testSubmitExecution_NoAuth() throws Exception {
            String requestBody = """
                    {
                        "problemId": 1,
                        "language": "JAVA",
                        "mode": "RUN",
                        "sourceCode": "public class Solution {}"
                    }
                    """;

            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should accept valid request with authentication")
        @WithMockUser(username = "123")
        void testSubmitExecution_ValidRequest() throws Exception {
            String requestBody = """
                    {
                        "problemId": 100,
                        "language": "JAVA",
                        "mode": "RUN",
                        "sourceCode": "public class Solution {}"
                    }
                    """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(null);

            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should store execution status in Redis")
        @WithMockUser(username = "456")
        void testSubmitExecution_StoresRedisStatus() throws Exception {
            String requestBody = """
                    {
                        "problemId": 200,
                        "language": "PYTHON",
                        "mode": "RUN",
                        "sourceCode": "print('Hello')"
                    }
                    """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(null);

            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isAccepted());

            verify(redisTemplate).opsForValue();
            verify(valueOps).set(
                    argThat(k -> k.startsWith("execution:status:")),
                    eq("PENDING"),
                    any()
            );
        }

        @Test
        @DisplayName("Should publish event to Kafka")
        @WithMockUser(username = "789")
        void testSubmitExecution_PublishesToKafka() throws Exception {
            String requestBody = """
                    {
                        "problemId": 300,
                        "language": "JAVA",
                        "mode": "DEBUG",
                        "sourceCode": "code here"
                    }
                    """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(null);

            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isAccepted());

            verify(kafkaTemplate).send(
                    eq("execution-tasks"),
                    eq("789"),
                    any()
            );
        }

        @Test
        @DisplayName("Should return valid UUID in executionId")
        @WithMockUser(username = "999")
        void testSubmitExecution_ReturnsExecutionId() throws Exception {
            String requestBody = """
                    {
                        "problemId": 400,
                        "language": "JAVA",
                        "mode": "RUN",
                        "sourceCode": "code"
                    }
                    """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(null);

            mockMvc.perform(post("/api/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").isNotEmpty())
                    .andExpect(jsonPath("$.executionId").value(
                            org.hamcrest.Matchers.matchesPattern(
                                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
                            )
                    ));
        }

        @Test
        @DisplayName("Should handle multiple concurrent requests")
        @WithMockUser(username = "111")
        void testSubmitExecution_ConcurrentRequests() throws Exception {
            String requestBody = """
                    {
                        "problemId": 500,
                        "language": "JAVA",
                        "mode": "RUN",
                        "sourceCode": "code"
                    }
                    """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(null);

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                        .andExpect(status().isAccepted());
            }

            verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any());
        }
    }
}
