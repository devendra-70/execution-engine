package com.epam.execution_engine_service.controller;

import com.epam.execution_engine_service.config.SecurityTestConfig;
import com.epam.execution_engine_service.config.TestKafkaProducerConfiguration;
import com.epam.execution_engine_service.config.TestRedisConfiguration;
import com.epam.execution_engine_service.config.TestServiceConfiguration;
import com.epam.execution_engine_service.gateway.ExecutionController;
import com.epam.execution_engine_service.gateway.ExecutionStatusResponse;
import com.epam.execution_engine_service.gateway.ExecutionStatusService;
import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import com.epam.execution_engine_service.service.ExecutionRegistrationResponse;
import com.epam.execution_engine_service.service.ExecutionRegistrationService;
import com.epam.execution_engine_service.util.IpAddressExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit Tests for ExecutionController (SRS §3.2)
 * 
 * Test Coverage: 100% of controller methods
 * - POST /api/executions
 * - GET /api/executions/{executionId}/status
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityTestConfig.class, TestRedisConfiguration.class, TestKafkaProducerConfiguration.class, TestServiceConfiguration.class})
class ExecutionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private ExecutionRegistrationService executionRegistrationService;
    
    @MockBean
    private ExecutionStatusService executionStatusService;
    
    @MockBean
    private IpAddressExtractor ipAddressExtractor;
    
    private ExecutionRequest validRequest;
    private ExecutionRegistrationResponse registrationResponse;
    private ExecutionStatusResponse statusResponse;
    private UUID executionId;
    
    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        
        // Valid execution request
        validRequest = ExecutionRequest.builder()
                .problemId("two-sum")
                .language("java21")
                .mode("SUBMIT")
                .sourceCode("public class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }")
                .build();
        
        // Expected registration response
        registrationResponse = ExecutionRegistrationResponse.builder()
                .executionId(executionId.toString())
                .status("PENDING")
                .build();
        
        // Status response
        statusResponse = ExecutionStatusResponse.builder()
                .executionId(executionId.toString())
                .status("COMPLETED")
                .build();
    }
    
    @Test
    @WithMockUser(username = "user123", roles = {"USER"})
    void testSubmitExecution_Success_Returns202Accepted() throws Exception {
        when(ipAddressExtractor.extractClientIp(any())).thenReturn("192.168.1.1");
        when(executionRegistrationService.registerExecution(any(), any(), any()))
                .thenReturn(registrationResponse);
        
        mockMvc.perform(post("/api/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
    
    @Test
    @WithMockUser(username = "user123", roles = {"USER"})
    void testSubmitExecution_InvalidRequest_ReturnsBadRequest() throws Exception {
        ExecutionRequest invalidRequest = ExecutionRequest.builder()
                .problemId("") // Empty problemId
                .language("java21")
                .mode("SUBMIT")
                .sourceCode("public class Solution { }")
                .build();
        
        mockMvc.perform(post("/api/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testSubmitExecution_MissingAuth_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "user123", roles = {"USER"})
    void testGetExecutionStatus_Success_Returns200Ok() throws Exception {
        when(executionStatusService.getExecutionStatus(executionId))
                .thenReturn(statusResponse);
        
        mockMvc.perform(get("/api/executions/" + executionId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
    
    @Test
    @WithMockUser(username = "user123", roles = {"USER"})
    void testGetExecutionStatus_NotFound_Returns404() throws Exception {
        when(executionStatusService.getExecutionStatus(any()))
                .thenReturn(null);
        
        mockMvc.perform(get("/api/executions/" + executionId + "/status"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @WithMockUser(username = "user123", roles = {"USER"})
    void testGetExecutionStatus_InvalidUUID_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/executions/invalid-uuid/status"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testUnauthorized_WithoutJWT_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }
}
