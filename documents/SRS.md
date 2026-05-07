# Team 2 Component-JAN2026-JAVA-TEAM2
# CODEVAL PLATFORM
## Module 2 — Execution Engine
### Low Level Design Document
 
**Version:** 2.0  
**Date:** 21 April 2026  
**Status:** Proposed Baseline  
 
---
 
## 1. Module Overview
The Execution Engine module is the secure, high-performance code evaluation backbone of the CodEval platform. It accepts user code submissions, queues them via Kafka for resilience against traffic spikes, executes them inside hardened sandboxed containers, and returns real-time results via WebSocket.
 
This module is a **Modular Monolithic Spring Boot application** deployed on AWS ECS. It internally handles API routing, Kafka consumption, sandbox orchestration, and database persistence, eliminating internal network hops.
 
### Sub-Components (Internal Modules)
1. **API Gateway Component:** JWT Auth, rate limiting, Kafka publishing, WebSocket registry.
2. **Execution Orchestrator Component:** Kafka consumption, container lifecycle management, Caffeine caching.
3. **Persistence Component:** Direct PostgreSQL write operations via Spring Data JPA.
4. **Sandbox Wrapper (Standalone JAR):** In-memory JVM compile, ClassLoader isolation, reflective execution.
 
---
 
## 2. Architecture Overview
### 2.1 Service Boundaries
| Component | Port | Technology Stack |
| :--- | :--- | :--- |
| **codeval-execution-engine** (Monolith) | `8080` | Java 21, Spring Boot 3.x, Spring Security (JWT), Spring WebSocket, Spring Kafka, Caffeine, Spring Data JPA, PostgreSQL Driver |
| **sandbox-wrapper** | N/A (stdin/socket) | Java 21, `javax.tools.JavaCompiler`, Custom `ClassLoader`, no Spring dependency |
 
### 2.2 End-to-End Runtime Flow
1. Client connects through AWS ALB over HTTPS/WebSocket.
2. The Engine authenticates the request via simple JWT validation.
3. Rate limits are applied via Redis token-bucket per `userId` and IP.
4. The Engine generates a UUID `executionId` and writes `PENDING` status to Redis KV.
5. The Engine publishes an `ExecutionTaskEvent` to the Kafka topic `execution-tasks`.
6. HTTP `202 Accepted` is returned to the client.
7. Internal Kafka Listener (Pool A) consumes the message and hands it to the Orchestrator (Pool B).
8. Orchestrator fetches test cases from **Caffeine local cache** (fallback to DB on miss).
9. Orchestrator acquires a pre-warmed sandbox container.
10. **Execution Loop:** Orchestrator feeds test cases to the Sandbox Wrapper one by one. The Wrapper compiles the code *once*, instantiates a **new ClassLoader per test case** to prevent static variable state-bleed, and executes.
11. Orchestrator aggregates the final verdict.
12. Persistence Component directly saves the `ExecutionResultEvent` to PostgreSQL synchronously (or via `@Async`).
13. Result is written to Redis KV and published to Redis Pub/Sub `execution-completed`.
14. The specific Engine instance holding the WebSocket session pushes the result to the client.
 
---
 
## 3. API Gateway Component
### 3.1 Overview
Handles incoming HTTP/WS traffic. It uses simple JWT validation—verifying the token signature using a shared secret or public key without full OAuth2 authorization server round-trips.
 
### 3.2 API Endpoints
| Method | Endpoint | Auth | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/executions` | Bearer JWT | Submit code. Returns `executionId` + PENDING. |
| `GET` | `/api/executions/{executionId}/status` | Bearer JWT | REST fallback if WS drops. |
| `GET` | `/actuator/health` | None | ALB / ECS health probe. |
| `WS` | `/ws` (STOMP) | Bearer JWT | Client subscribes to `/user/queue/execution-results`. |
 
### 3.3 Rate Limiting & Security
Rate limiting is maintained in Redis to operate globally across all ECS tasks.
* All API routes require a valid JWT.
* The `/actuator/health` and `/ws` handshake are explicitly permitted without auth.
 
---
 
## 4. Execution Orchestrator Component
### 4.1 Thread Pool Architecture
The system isolates Kafka consumption from container I/O using two configurable thread pools:
 
| Pool | Name | Configurable Property | Purpose |
| :--- | :--- | :--- | :--- |
| **Pool A** | Kafka Listener Pool | `app.kafka.concurrency` | Consumes messages, hands off to Pool B, manually commits offsets. |
| **Pool B** | TaskExecutor | `app.execution.orchestration-threads` | Manages container acquisition, streaming test cases, and waiting for I/O. |
 
### 4.2 Caching Strategy (Caffeine)
To eliminate Redis network overhead for test cases, the Orchestrator uses JVM-local **Caffeine Caching**.
* **Key:** `problemId`
* **Value:** `List<TestCase>`
* **Eviction:** Configurable time-based eviction (e.g., 60 minutes) or size-based limit.
 
### 4.3 Container Lifecycle
A pool of idle containers runs the `sandbox-wrapper` persistently.
* A single submission (which may contain 10-50 test cases) uses **one container**.
* Code is passed via socket. The wrapper compiles it once.
* Test cases are fed iteratively.
* The container is returned to the pool after the complete submission is evaluated.
 
---
 
## 5. Persistence Component
### 5.1 Overview
Unlike previous iterations, we rely directly on Spring Boot/Spring Data JPA to write results. Kafka is no longer used for the database persistence step.
 
### 5.2 Flow
Once the Execution Orchestrator aggregates the final verdict, it invokes `SubmissionRepository.save()`.
* Uses JPA Batch Inserts (`batch_size: 50`) to insert the parent `SubmissionEntity` and all `SubmissionTestResultEntity` records efficiently in a single transaction.
* Kafka offset for the ingestion task is acknowledged **only after** this DB commit succeeds.
 
---
 
## 6. Sandbox Wrapper (Standalone JAR)
### 6.1 Overview
A tiny, dependency-free Java application running inside the isolated container.
 
### 6.2 The `ClassLoader` Execution Pipeline
To prevent **state-bleed** (where user code utilizing `static` variables retains data between test cases), the wrapper relies on dynamic ClassLoaders:
 
1. Wrapper receives source code and compiles it in-memory via `JavaCompiler` to a `byte[]`.
2. **Loop starts** (for each test case received via socket):
    * Wrapper instantiates a *new* custom `URLClassLoader` passing the compiled `byte[]`.
    * Reflection is used to load the `Solution` class. Because it's a new ClassLoader, all `static` variables are initialized fresh.
    * Method is invoked with injected `stdin`. Output is captured.
    * Result is streamed back to the Orchestrator.
    * ClassLoader reference is dropped (eligible for GC).
3. The Wrapper remains alive, waiting for the next user's source code.
 
### 6.3 Configurable JVM Memory
The heap and memory configurations for the user code execution are completely configurable via application properties, which the Orchestrator injects into the `docker run` command or ECS equivalent.
 
---
 
## 7. Kafka Topic Design
We use Kafka strictly for ingestion to buffer against traffic spikes.
 
### 7.1 Topic: `execution-tasks`
| Parameter | Description |
| :--- | :--- |
| **Partitions** | Configurable via `app.kafka.partitions`. Defines max concurrent consumer threads across the ECS cluster. |
| **Message Key** | `userId` (Guarantees ordered execution for a single user). |
| **Retention** | 24 hours. |
| **Producer Acks** | `all` (Idempotent producer). |
 
---
 
## 8. Redis & Caffeine Design
| Store | Key Pattern | TTL | Purpose |
| :--- | :--- | :--- | :--- |
| **Redis KV** | `execution:status:{id}` | Configurable (`app.redis.status-ttl-seconds`) | Fallback execution state tracking. |
| **Redis KV** | `ratelimit:user:{id}` | Rolling window | Global API rate limiting. |
| **Redis Pub/Sub** | `execution-completed` | N/A | Broadcasts completed execution to all Engine instances. |
| **Caffeine** | `problem:{id}` | Configurable (`app.cache.testcase-ttl`) | JVM-local cache for problem test cases. |
 
---
 
## 9. Java Domain Model (Core DTOs)
* **ExecutionRequest:** `problemId`, `language`, `mode`, `sourceCode`.
* **ExecutionTaskEvent:** Extends Request with `executionId`, `userId`, `submittedAt`.
* **ExecutionResultEvent:** Contains `verdict`, `score`, `totalRuntimeMs`, `memoryBytes`, and `List<TestCaseResultEvent>`.
 
---
 
## 10. Error Handling & Edge Cases
| Scenario | Detected At | Behaviour |
| :--- | :--- | :--- |
| **Compile error** | Sandbox Wrapper | Short-circuit. `COMPILE_ERROR` returned. DB updated. |
| **Runtime error** | Sandbox Wrapper | Exception caught via reflection. Test case marked `RUNTIME_ERROR`. Loop continues to next test case. |
| **Timeout (TLE)** | Orchestrator (Future timeout) | Container forcibly terminated (`SIGKILL`). Replaced in pool. `TIME_LIMIT_EXCEEDED` recorded. |
| **DB Unreachable** | Persistence Component | Transaction rolls back. Kafka offset *not* committed. Task gracefully retried. |
 
---
 
## 11. AWS ECS Auto-Scaling & Infrastructure
This architecture utilizes **AWS ECS (Elastic Container Service) with EC2 Capacity Providers**.
 
### 11.1 ECS Deployment Model
* **Task Definition:** Defines the `codeval-execution-engine` Docker image. Maps port 8080 to the host. Mounts the Docker socket (if using Docker-out-of-Docker for sandboxes) or configures Firecracker microVMs.
* **Service:** Manages desired task counts and connects to the Application Load Balancer (ALB).
 
### 11.2 Auto-Scaling Strategy
1. **Service Auto-Scaling (Container Level):**
   * **Scale Up Trigger:** CPU utilization > 70% OR Kafka `execution-tasks` consumer group lag > threshold.
   * **Action:** ECS spins up more instances of the Engine application.
2. **Capacity Provider Auto-Scaling (EC2 Level):**
   * **Trigger:** ECS attempts to place a new Engine container, but existing EC2 instances lack available RAM/CPU.
   * **Action:** AWS Auto Scaling Group provisions a new EC2 instance dynamically.
 
---
 
## 12. Application Configuration Reference (`application.yml`)
All critical parameters are strictly configurable to allow runtime tuning without code changes.
```yaml
server:
  port: 8080
 
app:
  jwt:
    secret-key: ${JWT_SECRET}      # Simple JWT validation key
 
  kafka:
    topic: execution-tasks
    partitions: 50                 # Configurable partitions for scaling
    concurrency: 25                # Thread Pool A size (Kafka Listeners)
 
  execution:
    orchestration-threads: 50      # Thread Pool B size
    timeout-ms: 3000               # Configurable hard timeout (TLE) per submission
    pool:
      warm-min-size: 5             # Idle containers maintained
      idle-ttl-seconds: 300        # Container recycling threshold
    sandbox:
      memory-limit-mb: 256         # Configurable Docker cgroup memory limit
      jvm-xms: 128m                # JVM Initial Heap
      jvm-xmx: 256m                # JVM Max Heap
 
  redis:
    status-ttl-seconds: 600        # execution:status key TTL (10 mins)
    rate-limit:
      requests-per-minute: 5
 
  cache:
    testcase-ttl-minutes: 60       # Caffeine test case cache TTL
 
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/codeval
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50        # Efficient bulk insert
        order_inserts: true
 
 
13. Repository Layout
The project follows a standard Spring Boot Monorepo structure with a separate module for the standalone Sandbox compiler.
 
Plaintext
platform/execution-engine/
│
├── execution-engine-app/                  # Main Spring Boot Monolith
│   ├── src/main/java/.../gateway        # REST/WS Controllers, JWT Auth
│   ├── src/main/java/.../orchestrator   # Kafka Consumers, Docker Client, Caffeine
│   └── src/main/java/.../persistence    # JPA Repositories, Entities
│
├── sandbox-wrapper/                     # Standalone Lightweight Java App
│   ├── src/main/java/.../Wrapper.java   # No Spring. Pure JavaCompiler & ClassLoader logic
│   └── pom.xml                          # Builds to a tiny, executable shaded JAR
│
├── deploy/
│   ├── ecs/                             # ECS Task Definitions and Service JSON
│   └── terraform/                       # Infrastructure as Code (MSK, RDS, ECS Cluster)
│
└── docker-compose.yml                   # For local development ONLY (Postgres, Redis, Kafka)