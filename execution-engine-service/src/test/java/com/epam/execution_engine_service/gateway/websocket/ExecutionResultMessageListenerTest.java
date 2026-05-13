package com.epam.execution_engine_service.gateway.websocket;

import com.epam.execution_engine_service.persistence.event.ExecutionResultEvent;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionResultMessageListener}.
 *
 * Verifies Redis Pub/Sub → STOMP delivery routing, null userId handling,
 * malformed JSON resilience, and silent discard when no session is active.
 *
 * EPMICMPCOD-342 / Sub-task EPMICMPCOD-546
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionResultMessageListener — Redis Pub/Sub → STOMP delivery")
class ExecutionResultMessageListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SimpUserRegistry simpUserRegistry;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExecutionResultMessageListener listener;

    private ExecutionResultEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = ExecutionResultEvent.builder()
                .executionId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .userId(99L)
                .problemId(1L)
                .verdict("PASSED")
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private Message buildRedisMessage(String body) {
        return new Message() {
            @Override
            public byte[] getBody() {
                return body.getBytes();
            }

            @Override
            public byte[] getChannel() {
                return "execution-completed".getBytes();
            }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Active STOMP session found for target userId")
    class ActiveSessionFound {

        @Test
        @DisplayName("convertAndSendToUser called once with correct args when user has active session")
        void onMessage_userHasActiveSession_sendsToUserOnce() throws Exception {
            Message redisMessage = buildRedisMessage("{\"userId\":\"user-99\"}");
            when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class))).thenReturn(sampleEvent);
            when(simpUserRegistry.getUser("99")).thenReturn(mock(SimpUser.class));

            listener.onMessage(redisMessage, null);

            verify(messagingTemplate, times(1))
                    .convertAndSendToUser(
                            "99",
                            ExecutionResultMessageListener.RESULT_DESTINATION,
                            sampleEvent);
        }
    }

    @Nested
    @DisplayName("No active STOMP session for target userId")
    class NoActiveSession {

        @Test
        @DisplayName("convertAndSendToUser NOT called when user has no active STOMP session")
        void onMessage_noActiveSession_doesNotSendToUser() throws Exception {
            Message redisMessage = buildRedisMessage("{\"userId\":\"user-99\"}");
            when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class))).thenReturn(sampleEvent);
            when(simpUserRegistry.getUser("99")).thenReturn(null);

            listener.onMessage(redisMessage, null);

            verifyNoInteractions(messagingTemplate);
        }
    }

    @Nested
    @DisplayName("Malformed JSON body")
    class MalformedJsonBody {

        @Test
        @DisplayName("Malformed JSON does not throw exception — message is silently discarded")
        void onMessage_malformedJson_noExceptionThrownAndMessageDiscarded() throws Exception {
            Message redisMessage = buildRedisMessage("not-valid-json-at-all");
            doThrow(JsonMappingException.fromUnexpectedIOE(new java.io.IOException("parse error")))
                    .when(objectMapper).readValue(anyString(), eq(ExecutionResultEvent.class));

            assertThatNoException().isThrownBy(() -> listener.onMessage(redisMessage, null));

            verifyNoInteractions(messagingTemplate);
            verifyNoInteractions(simpUserRegistry);
        }
    }

    @Nested
    @DisplayName("ExecutionResultEvent with null userId")
    class NullUserIdEvent {

        @Test
        @DisplayName("Event with null userId is discarded without calling convertAndSendToUser")
        void onMessage_nullUserId_doesNotCallSendAndDoesNotThrow() throws Exception {
            ExecutionResultEvent nullUserEvent = ExecutionResultEvent.builder()
                    .executionId(UUID.randomUUID())
                    .userId(null)
                    .build();
            Message redisMessage = buildRedisMessage("{\"userId\":null}");
            when(objectMapper.readValue(anyString(), eq(ExecutionResultEvent.class))).thenReturn(nullUserEvent);

            assertThatNoException().isThrownBy(() -> listener.onMessage(redisMessage, null));

            verifyNoInteractions(messagingTemplate);
            verifyNoInteractions(simpUserRegistry);
        }
    }
}
