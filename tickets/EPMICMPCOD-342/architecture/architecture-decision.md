# Architecture Decision — EPMICMPCOD-342

**Ticket:** EPMICMPCOD-342 — Route Real-Time Execution Results via Redis Pub/Sub to STOMP WebSocket Sessions  
**Branch:** EXE-342/route-redis-pubsub-stomp-websocket  
**SRS Sections:** §2.2 (Steps 13–14), §3.1, §3.2, §3.3, §8, §9  
**Approved:** 2026-05-12  

---

## 1. Components Touched

| Component | Change Type | File |
|---|---|---|
| API Gateway — WebSocket Config | **NEW** | `config/WebSocketConfig.java` |
| API Gateway — STOMP JWT Interceptor | **NEW** | `gateway/security/JwtStompInterceptor.java` |
| API Gateway — Redis Pub/Sub Listener | **NEW** | `gateway/websocket/ExecutionResultMessageListener.java` |
| Redis Config | **MODIFY** | `persistence/config/RedisConfig.java` — add `RedisMessageListenerContainer` bean |
| pom.xml | **MODIFY** | Add `spring-boot-starter-websocket` dependency *(config-permission gate required)* |

---

## 2. APIs Designed

| Type | Endpoint / Channel | Direction | Auth |
|---|---|---|---|
| WebSocket/STOMP | `GET /ws` (HTTP upgrade) | Client → Server | None at HTTP level (SRS §3.3) |
| STOMP CONNECT | `CONNECT` frame | Client → Server | Bearer JWT in STOMP header (SRS §3.1) |
| STOMP Subscribe | `/user/queue/execution-results` | Client subscribes | Authenticated principal |
| Redis Pub/Sub | `execution-completed` channel | Internal broadcast | N/A |

---

## 3. New Files

### 3.1 `WebSocketConfig.java`

```
package: com.epam.execution_engine_service.config
implements: WebSocketMessageBrokerConfigurer
annotation: @Configuration, @EnableWebSocketMessageBroker
```

Responsibilities:
- Register STOMP endpoint `/ws` with SockJS fallback and `setAllowedOrigins("*")`
- Enable simple message broker on `/queue`, `/topic`
- Application destination prefix: `/app`
- User destination prefix: `/user`
- Register `JwtStompInterceptor` on `CLIENT_INBOUND` channel via `configureClientInboundChannel()`

### 3.2 `JwtStompInterceptor.java`

```
package: com.epam.execution_engine_service.gateway.security
implements: ChannelInterceptor
annotation: @Component
```

Responsibilities:
- intercepts STOMP `CONNECT` command frames only (skip all others)
- Extracts `Authorization: Bearer <token>` from `StompHeaderAccessor` native headers
- Validates JWT via existing `JwtTokenProvider` (shared-secret `app.jwt.secret-key`, SRS §3.1)
- On success: extracts `userId` via `JwtClaimsExtractor`, sets `StompHeaderAccessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()))`
- On failure (invalid/absent): throws `MessageDeliveryException` → Spring sends STOMP ERROR frame, session is not established
- Spring STOMP automatically registers connected principals in `SimpUserRegistry` — no manual tracking needed

### 3.3 `ExecutionResultMessageListener.java`

```
package: com.epam.execution_engine_service.gateway.websocket
implements: MessageListener
annotation: @Component
```

Responsibilities:
- `onMessage(Message message, byte[] pattern)`:
  1. Deserialise message body JSON → `ExecutionResultEvent` via `ObjectMapper`
  2. Extract `userId` from `ExecutionResultEvent.getUserId()`
  3. Query `SimpUserRegistry.getUser(userId)`
  4. If session present → call `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/execution-results", executionResultEvent)`
  5. If session absent → `log.debug("No STOMP session for userId {}; discarding", userId)` — no exception, no ERROR log
- Subscription to `ChannelTopic("execution-completed")` established via `RedisMessageListenerContainer` before app serves HTTP traffic

### 3.4 `RedisConfig.java` — addendum

Add new beans:
- `RedisMessageListenerContainer` — uses existing `RedisConnectionFactory`; registers `MessageListenerAdapter(executionResultMessageListener)` on `ChannelTopic("execution-completed")`
- `MessageListenerAdapter` — wraps `ExecutionResultMessageListener`

---

## 4. Schema Changes

**None.** No new DB tables, no new Redis key patterns, no Kafka topic changes.

Redis Pub/Sub channel `execution-completed` (fire-and-forget, no TTL) is already defined in SRS §8 and used by the existing `ExecutionResultPublishingService`.

---

## 5. Dependency Change

| Dependency | Action | Scope |
|---|---|---|
| `spring-boot-starter-websocket` | ADD to `pom.xml` | compile |

*This is a protected config file. Config-permission gate will be shown at Stage 2 before the agent writes it.*

---

## 6. Existing Assets Reused (No Changes)

| Asset | Location | How Used |
|---|---|---|
| `JwtTokenProvider` | `gateway/security/JwtTokenProvider.java` | JWT validation in `JwtStompInterceptor` |
| `JwtClaimsExtractor` | `gateway/security/JwtClaimsExtractor.java` | Extract `userId` from token claims |
| `ApplicationProperties.jwt.secretKey` | `config/ApplicationProperties.java` | Secret for JWT validation |
| `RedisTemplate<String,String>` | `persistence/config/RedisConfig.java` | Existing bean, unchanged |
| `SecurityConfig` — `/ws` permitted | `config/SecurityConfig.java` | HTTP-level permit already present ✅ |
| `ExecutionResultEvent.userId` | `persistence/event/ExecutionResultEvent.java` | Confirmed present ✅ |

---

## 7. SRS Traceability

| SRS Ref | Requirement | Implementation |
|---|---|---|
| §2.2 Step 13 | Result written to Redis KV + published to `execution-completed` | Handled by existing `ExecutionResultPublishingService` (no change) |
| §2.2 Step 14 | Instance holding WS session pushes result to client | `ExecutionResultMessageListener` → `convertAndSendToUser()` |
| §3.1 | Simple JWT validation via shared secret | `JwtStompInterceptor` uses `JwtTokenProvider` |
| §3.2 | WS endpoint `/ws` (STOMP); client subscribes to `/user/queue/execution-results` | `WebSocketConfig` |
| §3.3 | `/ws` handshake permitted without auth; STOMP CONNECT carries JWT | `SecurityConfig` (existing) + `JwtStompInterceptor` |
| §8 | All ECS instances subscribe to `execution-completed` via `RedisMessageListenerContainer` | `ExecutionResultMessageListener` + `RedisConfig` addendum |
| §9 | `ExecutionResultEvent` payload: `verdict`, `score`, `totalRuntimeMs`, `memoryBytes`, `List<TestCaseResultEvent>` | Existing `ExecutionResultEvent` confirmed ✅ |

---

## 8. Test Strategy (EPMICMPCOD-547)

| Scenario | Type | Tool |
|---|---|---|
| Happy path: active session → delivery | Integration | Testcontainers Redis + STOMP client |
| No session → silent discard | Unit | Mockito (`SimpUserRegistry` mock) |
| Multi-instance: no duplicate delivery | Integration | Two Spring app contexts, shared embedded Redis |
| WebSocket fallback → REST/Redis KV | Integration | MockMvc + embedded Redis |
| Invalid JWT → STOMP ERROR | Unit | `JwtStompInterceptor` unit test |

Tag: `@Tag("websocket-routing")`. Coverage target ≥ 80% on listener dispatch path (SRS-aligned: EPMICMPCOD-547 AC#5).
