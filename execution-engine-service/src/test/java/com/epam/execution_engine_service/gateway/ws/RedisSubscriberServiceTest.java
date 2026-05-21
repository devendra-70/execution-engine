package com.epam.execution_engine_service.gateway.ws;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisSubscriberService
 * Tests Redis Pub/Sub subscription and message processing for WebSocket delivery.
 */
@ExtendWith(MockitoExtension.class)
class RedisSubscriberServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Message redisMessage;

    @InjectMocks
    private RedisSubscriberService redisSubscriberService;

    private ExecutionResultEvent executionResultEvent;

    @BeforeEach
    void setUp() {
        executionResultEvent = ExecutionResultEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(12345L)
                .problemId(67890L)
                .problemName("Test Problem")
                .score(100)
                .totalRuntimeMs(1500L)
                .memoryBytes(2048L)
                .build();
    }

    @Test
    void subscribe_validSetup_registersMessageListener() {
        // When
        redisSubscriberService.subscribe();

        // Then
        ArgumentCaptor<ChannelTopic> topicCaptor = ArgumentCaptor.forClass(ChannelTopic.class);
        verify(listenerContainer, times(1)).addMessageListener(
                eq(redisSubscriberService),
                topicCaptor.capture()
        );

        assertEquals("execution-completed", topicCaptor.getValue().getTopic());
    }

    @Test
    void onMessage_validPayload_sendsToUserQueue() throws Exception {
        // Given
        String jsonPayload = "{\"executionId\":\"123\",\"userId\":12345}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(executionResultEvent);

        // When
        redisSubscriberService.onMessage(redisMessage, new byte[0]);

        // Then
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                "12345",
                "/queue/execution-results",
                executionResultEvent
        );
    }

    @Test
    void onMessage_validPayload_parsesJsonCorrectly() throws Exception {
        // Given
        String jsonPayload = "{\"executionId\":\"456\",\"userId\":67890}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(executionResultEvent);

        // When
        redisSubscriberService.onMessage(redisMessage, new byte[0]);

        // Then
        verify(objectMapper, times(1)).readValue(jsonPayload, ExecutionResultEvent.class);
    }

    @Test
    void onMessage_multipleMessages_sendsEachToCorrectUser() throws Exception {
        // Given
        ExecutionResultEvent user1Event = ExecutionResultEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(111L)
                .build();

        ExecutionResultEvent user2Event = ExecutionResultEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(222L)
                .build();

        String payload1 = "{\"userId\":111}";
        String payload2 = "{\"userId\":222}";

        when(redisMessage.getBody())
                .thenReturn(payload1.getBytes())
                .thenReturn(payload2.getBytes());

        when(objectMapper.readValue(payload1, ExecutionResultEvent.class)).thenReturn(user1Event);
        when(objectMapper.readValue(payload2, ExecutionResultEvent.class)).thenReturn(user2Event);

        // When
        redisSubscriberService.onMessage(redisMessage, new byte[0]);
        redisSubscriberService.onMessage(redisMessage, new byte[0]);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
                "111",
                "/queue/execution-results",
                user1Event
        );
        verify(messagingTemplate).convertAndSendToUser(
                "222",
                "/queue/execution-results",
                user2Event
        );
    }

    @Test
    void onMessage_invalidJson_handlesExceptionGracefully() throws Exception {
        // Given
        String invalidJson = "{invalid json}";
        byte[] messageBody = invalidJson.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Invalid JSON"));

        // When
        assertDoesNotThrow(() -> redisSubscriberService.onMessage(redisMessage, new byte[0]));

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void onMessage_objectMapperException_doesNotSendMessage() throws Exception {
        // Given
        String jsonPayload = "{\"userId\":12345}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class)))
                .thenThrow(new RuntimeException("Deserialization error"));

        // When
        assertDoesNotThrow(() -> redisSubscriberService.onMessage(redisMessage, new byte[0]));

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void onMessage_nullMessage_handlesExceptionGracefully() {
        // Given
        when(redisMessage.getBody()).thenReturn(null);

        // When
        assertDoesNotThrow(() -> redisSubscriberService.onMessage(redisMessage, new byte[0]));

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void onMessage_emptyMessage_handlesExceptionGracefully() throws Exception {
        // Given
        byte[] emptyBody = new byte[0];

        when(redisMessage.getBody()).thenReturn(emptyBody);
        when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Empty content"));

        // When
        assertDoesNotThrow(() -> redisSubscriberService.onMessage(redisMessage, new byte[0]));

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void onMessage_messagingTemplateThrowsException_handlesExceptionGracefully() throws Exception {
        // Given
        String jsonPayload = "{\"executionId\":\"789\",\"userId\":12345}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(executionResultEvent);

        doThrow(new RuntimeException("WebSocket send failed"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        // When
        assertDoesNotThrow(() -> redisSubscriberService.onMessage(redisMessage, new byte[0]));

        // Then
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void onMessage_correctDestination_sendsToQueueExecutionResults() throws Exception {
        // Given
        String jsonPayload = "{\"executionId\":\"999\",\"userId\":55555}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(executionResultEvent);

        // When
        redisSubscriberService.onMessage(redisMessage, new byte[0]);

        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSendToUser(
                anyString(),
                destinationCaptor.capture(),
                any()
        );

        assertEquals("/queue/execution-results", destinationCaptor.getValue());
    }

    @Test
    void onMessage_userIdAsLong_convertsToStringCorrectly() throws Exception {
        // Given
        ExecutionResultEvent eventWithLongUserId = ExecutionResultEvent.builder()
                .executionId(UUID.randomUUID())
                .userId(987654321L)
                .build();

        String jsonPayload = "{\"userId\":987654321}";
        byte[] messageBody = jsonPayload.getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(eventWithLongUserId);

        // When
        redisSubscriberService.onMessage(redisMessage, new byte[0]);

        // Then
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSendToUser(
                userIdCaptor.capture(),
                anyString(),
                any()
        );

        assertEquals("987654321", userIdCaptor.getValue());
    }

    @Test
    void onMessage_withPattern_processesMessageNormally() throws Exception {
        // Given
        String jsonPayload = "{\"executionId\":\"111\",\"userId\":12345}";
        byte[] messageBody = jsonPayload.getBytes();
        byte[] pattern = "execution-*".getBytes();

        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(jsonPayload, ExecutionResultEvent.class))
                .thenReturn(executionResultEvent);

        // When
        redisSubscriberService.onMessage(redisMessage, pattern);

        // Then
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                "12345",
                "/queue/execution-results",
                executionResultEvent
        );
    }

    @Test
    void subscribe_multipleInvocations_registersMultipleTimes() {
        // When
        redisSubscriberService.subscribe();
        redisSubscriberService.subscribe();

        // Then
        verify(listenerContainer, times(2)).addMessageListener(
                eq(redisSubscriberService),
                any(ChannelTopic.class)
        );
    }
}

