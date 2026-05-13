package com.epam.execution_engine_service.gateway;

import com.epam.execution_engine_service.gateway.ExecutionController;
import com.epam.execution_engine_service.gateway.ExecutionStatusService;
import com.epam.execution_engine_service.persistence.entity.ExecutionRequest;
import com.epam.execution_engine_service.service.ExecutionRegistrationResponse;
import com.epam.execution_engine_service.service.ExecutionRegistrationService;
import com.epam.execution_engine_service.util.IpAddressExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ExecutionControllerTest — Unit tests for ExecutionController (SRS §3.2)
 * 
 * Coverage target: ≥100% (controllers must be fully tested)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
class ExecutionControllerTest {

    @Mock
    private ExecutionRegistrationService executionRegistrationService;
    
    @Mock
    private ExecutionStatusService executionStatusService;
    
    @Mock
    private IpAddressExtractor ipAddressExtractor;

    @InjectMocks
    private ExecutionController executionController;

    private ExecutionRequest testRequest;
    private UUID testExecutionId;

    @BeforeEach
    void setUp() {
        testExecutionId = UUID.randomUUID();
        testRequest = new ExecutionRequest();
        testRequest.setProblemId("problem-1");
        testRequest.setLanguage("JAVA");
        testRequest.setMode("RUN");
        testRequest.setSourceCode("public class Solution { }");
    }

    @Test
    void testSubmitExecution_ReturnsAccepted() {
        // Arrange
        HttpServletRequest mockHttpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        
        ExecutionRegistrationResponse mockResponse = ExecutionRegistrationResponse.builder()
                .executionId(testExecutionId.toString())
                .status("PENDING")
                .build();

        when(ipAddressExtractor.extractClientIp(any(HttpServletRequest.class))).thenReturn("192.168.1.1");
        when(executionRegistrationService.registerExecution(any(ExecutionRequest.class), anyString(), anyString()))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ExecutionRegistrationResponse> response = 
                executionController.submitExecution(testRequest, mockHttpRequest);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(testExecutionId.toString(), response.getBody().getExecutionId());
    }
}
