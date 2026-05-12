# Ticket Context — EPMICMPCOD-342

**Generated:** 2026-05-12T00:00:00Z
**Pipeline Run:** 1
**Status at Pull:** Open

---

## Ticket Details

| Field            | Value                                                                                          |
|------------------|-----------------------------------------------------------------------------------------------|
| Ticket ID        | EPMICMPCOD-342                                                                                |
| Title            | Team2 - Route Real-Time Execution Results via Redis Pub/Sub to STOMP WebSocket Sessions       |
| Type             | Story                                                                                         |
| Epic             | EPIC-001 — Execution Engine                                                                   |
| Story Points     | 3                                                                                             |
| Assignee         | Mohd Muzammil (mohd_muzammil@epam.com)                                                        |
| Reporter         | Yashvi Bhuwalka (yashvi_bhuwalka@epam.com)                                                   |
| Sprint           | N/A                                                                                           |
| Labels           | N/A                                                                                           |
| Components       | JAN2026-JAVA-TEAM2                                                                            |
| Priority         | Major                                                                                         |

---

## Description

**Actor:** System  
**User Story ID:** US-004  
**Category:** DEV

As a System, I want every running ECS instance of the `codeval-execution-engine` monolith to subscribe to the Redis Pub/Sub channel `execution-completed`, so that the specific instance holding the user's STOMP WebSocket session can push the `ExecutionResultEvent` to `/user/queue/execution-results` **without duplicate delivery** across horizontally scaled instances.

### Summary

The API Gateway Component maintains STOMP WebSocket sessions via Spring's `SimpUserRegistry`. All ECS task instances subscribe to the Redis Pub/Sub channel `execution-completed` via `RedisMessageListenerContainer`. On receiving a broadcast, each instance checks whether it holds a STOMP session for the target `userId`. The instance that does uses `SimpMessagingTemplate.convertAndSendToUser()` to push the result to `/user/queue/execution-results`. Instances without a matching session silently discard the event. Clients whose WebSocket is unavailable fall back to polling `GET /api/executions/{executionId}/status` from Redis KV.

### SRS Alignment

| SRS Section | Requirement |
|---|---|
| §2.2 Steps 13–14 | DB persist → Redis KV write → Redis Pub/Sub broadcast → STOMP push |
| §3.2 | WebSocket endpoint `/ws` (STOMP), client subscribes to `/user/queue/execution-results` |
| §3.3 | `/ws` HTTP handshake permitted without auth; STOMP CONNECT carries Bearer JWT |
| §3.1 | Simple JWT validation via `app.jwt.secret-key` (no OAuth2 server round-trip) |
| §8 | Redis Pub/Sub channel `execution-completed` — fire-and-forget broadcast, no TTL |
| §9 | `ExecutionResultEvent`: `verdict`, `score`, `totalRuntimeMs`, `memoryBytes`, `List<TestCaseResultEvent>` |

---

## Acceptance Criteria

- [ ] All ECS task instances subscribe to Redis Pub/Sub channel `execution-completed` via `RedisMessageListenerContainer` on startup.
- [ ] On receiving an `execution-completed` message, each instance uses `SimpUserRegistry` to check whether the target `userId` has an active STOMP session on that instance.
- [ ] The instance holding the matching STOMP session calls `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/execution-results", ExecutionResultEvent)`.
- [ ] Instances without a matching STOMP session silently discard the Pub/Sub message (no ERROR log, no forwarding).
- [ ] The STOMP CONNECT frame carries a Bearer JWT; JWT is validated and `userId` is bound as the STOMP session principal.
- [ ] Clients whose WebSocket is unavailable are served via REST fallback: `GET /api/executions/{executionId}/status` reads from Redis KV.
- [ ] No duplicate delivery — only the instance holding the session pushes; all others discard.
- [ ] Integration test: two application contexts (two ECS instances) both subscribed to `execution-completed`; only the instance with the STOMP session delivers; the other discards.

---

## Subtasks

| Key | Summary | Status |
|---|---|---|
| EPMICMPCOD-545 | Configure STOMP WebSocket Endpoint `/ws` with JWT Principal Binding on CONNECT | Open |
| EPMICMPCOD-546 | Subscribe All ECS Instances to Redis Pub/Sub `execution-completed` and Dispatch via `SimpMessagingTemplate` | Open |
| EPMICMPCOD-547 | Write Integration Tests for Redis Pub/Sub to STOMP Routing — No Duplicate Delivery | Open |

---

## Subtask Detail — EPMICMPCOD-545

**Configure STOMP WebSocket Endpoint `/ws` with JWT Principal Binding on CONNECT**

- Implement `WebSocketMessageBrokerConfigurer` with `@EnableWebSocketMessageBroker`
- Register STOMP endpoint: `/ws` with SockJS fallback
- Configure broker: simple broker on `/queue`, `/topic`; app prefix `/app`; user prefix `/user`
- `ChannelInterceptor` on `CLIENT_INBOUND` intercepts STOMP `CONNECT` frames:
  - Extracts Bearer JWT from STOMP headers
  - Validates via `app.jwt.secret-key` (SRS §3.1)
  - Sets authenticated `userId` as `StompHeaderAccessor` user principal
  - Rejects invalid/absent JWT with `MessageDeliveryException`
- `SecurityConfig` must permit `/ws` at HTTP level (already done ✅)

**Acceptance Criteria (545):**
- [ ] STOMP endpoint `/ws` registered; HTTP upgrade works without Bearer JWT
- [ ] Valid JWT in STOMP CONNECT → `userId` registered in `SimpUserRegistry`
- [ ] Invalid/absent JWT → connection rejected (STOMP ERROR frame)
- [ ] Client can subscribe to `/user/queue/execution-results` after CONNECT
- [ ] Unit test: valid JWT → session established; invalid JWT → rejected

---

## Subtask Detail — EPMICMPCOD-546

**Subscribe All ECS Instances to Redis Pub/Sub `execution-completed` and Dispatch via `SimpMessagingTemplate`**

- Define `RedisMessageListenerContainer` bean; register on startup
- `MessageListenerAdapter` wrapping `ExecutionResultMessageListener`
- Bind to `ChannelTopic("execution-completed")`
- `onMessage()`: deserialise JSON → `ExecutionResultEvent`; check `SimpUserRegistry.getUser(userId)`; if present → `convertAndSendToUser()`; if absent → DEBUG log + discard
- Subscription active before accepting HTTP traffic (`ApplicationReadyEvent` / `@PostConstruct`)

**Acceptance Criteria (546):**
- [ ] `RedisMessageListenerContainer` subscribes to `execution-completed` on startup
- [ ] `ExecutionResultEvent` deserialised correctly from JSON
- [ ] Session present → `convertAndSendToUser()` called once with correct userId + payload
- [ ] Session absent → message discarded, no exception, DEBUG log only
- [ ] Unit test: mock registry with session → `convertAndSendToUser` called; mock registry without session → not called

---

## Subtask Detail — EPMICMPCOD-547

**Write Integration Tests for Redis Pub/Sub to STOMP Routing — No Duplicate Delivery**

Five test scenarios:
1. **Happy Path** — single instance, active STOMP session → message delivered once
2. **No Session** — no STOMP session → message discarded silently
3. **Multi-Instance** — two app contexts; only instance A has session → only A delivers (no duplicate)
4. **WebSocket Fallback** — no session → `GET /api/executions/{executionId}/status` returns result from Redis KV
5. **JWT Rejected** — invalid JWT on STOMP CONNECT → session not established; STOMP ERROR frame returned

Infrastructure: embedded Redis (Testcontainers), separate Spring contexts for multi-instance test. Tag: `@Tag("websocket-routing")`. Coverage ≥ 80% on listener dispatch path.

---

## Linked Tickets

| Relationship | Ticket ID | Title |
|---|---|---|
| Parent | EPMICMPCOD-342 | Team2 - Route Real-Time Execution Results via Redis Pub/Sub to STOMP WebSocket Sessions |
| Subtask | EPMICMPCOD-545 | Configure STOMP WebSocket Endpoint /ws with JWT Principal Binding on CONNECT |
| Subtask | EPMICMPCOD-546 | Subscribe All ECS Instances to Redis Pub/Sub execution-completed and Dispatch via SimpMessagingTemplate |
| Subtask | EPMICMPCOD-547 | Write Integration Tests for Redis Pub/Sub to STOMP Routing — No Duplicate Delivery |

---

## Existing Codebase State

| Component | Status | Notes |
|---|---|---|
| `SecurityConfig` — `/ws` permitted | ✅ Exists | HTTP-level permit already in place |
| `RedisTemplate<String,String>` bean | ✅ Exists | In `RedisConfig` |
| `JwtTokenProvider` / `JwtClaimsExtractor` | ✅ Exists | Simple JWT via `app.jwt.secret-key` |
| `ApplicationProperties` — `app.jwt.secret-key` | ✅ Exists | Wired in `Jwt` inner class |
| `WebSocketMessageBrokerConfigurer` | ❌ Missing | Needs to be created (EPMICMPCOD-545) |
| STOMP endpoint `/ws` | ❌ Missing | Needs to be created (EPMICMPCOD-545) |
| `ChannelInterceptor` for JWT binding | ❌ Missing | Needs to be created (EPMICMPCOD-545) |
| `RedisMessageListenerContainer` | ❌ Missing | Needs to be created (EPMICMPCOD-546) |
| `ExecutionResultMessageListener` | ❌ Missing | Needs to be created (EPMICMPCOD-546) |
| `spring-boot-starter-websocket` dependency | ❌ Missing | Needs to be added to pom.xml |

---

## Pipeline Metadata

| Field               | Value                |
|---------------------|----------------------|
| Pipeline started    | 2026-05-12T00:00:00Z |
| Current stage       | Context Built        |
| Architecture run    | Pending              |
| Implementation run  | Pending              |
| Review loop runs    | 0                    |
