package com.epam.execution_engine_service.controller;

import com.epam.execution_engine_service.config.SecurityTestConfig;
import com.epam.execution_engine_service.config.TestKafkaProducerConfiguration;
import com.epam.execution_engine_service.config.TestRedisConfiguration;
import com.epam.execution_engine_service.dto.ExecutionRequest;
import com.epam.execution_engine_service.dto.ErrorResponse;
import com.epam.execution_engine_service.persistence.repository.ExecutionStatusRepository;
import com.epam.execution_engine_service.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for ExecutionController validation and authentication.
 *
 * <p>Tests the complete flow through Spring Security and JSR-380 validation.
 *
 * @author Execution Engine Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityTestConfig.class, TestRedisConfiguration.class, TestKafkaProducerConfiguration.class})
@DisplayName("ExecutionController Integration Tests")
class ExecutionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private ExecutionStatusRepository executionStatusRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ExecutionRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ExecutionRequest(
                "two-sum",
                "java21",
                "SUBMIT",
                "class Solution { public int[] twoSum(int[] nums, int target) { ... } }"
        );
        
        // Configure RateLimiterService mock (SRS Section 3.5: Rate limiting)
        // Mock returns normally (doesn't throw RateLimitException) to allow requests
        doNothing().when(rateLimiterService).checkRateLimit(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        
        // Configure ExecutionStatusRepository mock (SRS Section 8: Redis status storage)
        // Mock returns the ExecutionStatus that was saved
        when(executionStatusRepository.save(ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Configure Kafka mock (SRS Section 7.1: Kafka publishing)
        // KafkaTemplate is provided by TestKafkaProducerConfiguration as a mock bean
        // Configure it to return immediately without connecting to actual Kafka broker
        when(kafkaTemplate.send(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("Valid request with authentication returns 202 Accepted")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testValidRequestWithAuth() throws Exception {
        mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Request without authentication returns 401 Unauthorized")
    void testRequestWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request with missing problemId returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testMissingProblemId() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                null,
                "java21",
                "RUN",
                "source code"
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Validation Failed");
        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("problemId"));
    }

    @Test
    @DisplayName("Request with missing language returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testMissingLanguage() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                null,
                "RUN",
                "source code"
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("language"));
    }

    @Test
    @DisplayName("Request with missing mode returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testMissingMode() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                null,
                "source code"
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("mode"));
    }

    @Test
    @DisplayName("Request with invalid mode returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testInvalidMode() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "INVALID",
                "source code"
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("mode") && 
                        e.getMessage().contains("RUN or SUBMIT"));
    }

    @Test
    @DisplayName("Request with missing sourceCode returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testMissingSourceCode() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                null
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("sourceCode"));
    }

    @Test
    @DisplayName("Request with blank sourceCode returns 400 Bad Request")
    @WithMockUser(username = "testuser")
    void testBlankSourceCode() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "RUN",
                "   "
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors())
                .anyMatch(e -> e.getField().equals("sourceCode"));
    }

    @Test
    @DisplayName("Request with multiple invalid fields returns 400 with all field errors")
    @WithMockUser(username = "testuser")
    void testMultipleInvalidFields() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                null,
                null,
                "INVALID",
                null
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getFieldErrors()).hasSize(4);
        assertThat(response.getFieldErrors())
                .extracting("field")
                .containsExactlyInAnyOrder("problemId", "language", "mode", "sourceCode");
    }

    @Test
    @DisplayName("Mode validation is case-insensitive: 'run' passes")
    @WithMockUser(username = "testuser")
    void testModeCaseInsensitive() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                "two-sum",
                "java21",
                "run",
                "source code"
        );

        mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Error response has correct format with timestamp and status")
    @WithMockUser(username = "testuser")
    void testErrorResponseFormat() throws Exception {
        ExecutionRequest request = new ExecutionRequest(
                null,
                "java21",
                "RUN",
                "source code"
        );

        MvcResult result = mockMvc.perform(post("/api/executions")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Validation Failed");
        assertThat(response.getMessage()).isNotBlank();
        assertThat(response.getFieldErrors()).isNotEmpty();
    }
}
