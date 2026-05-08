# Architecture Decision Document — EPMICMPCOD-351: Persist Execution & Test Case Results

**Date:** May 7, 2026  
**Author:** Architecture Design Agent  
**Status:** DRAFT — Awaiting Approval  
**Ticket:** EPMICMPCOD-351  
**Branch:** `EXE-351/persist-execution-and-test-case-results`

---

## 1. EXECUTIVE SUMMARY

The Execution Engine Service will persist `ExecutionResultEvent` directly within the Execution Orchestrator using Spring Data JPA + PostgreSQL with two linked entity tables (`submissions` and `submission_test_results`). The persistence flow enforces strict transactional boundaries: results are saved atomically within `@Transactional(REQUIRED, READ_COMMITTED)` blocks using Hibernate JDBC batching (batch_size=50), followed by manual Kafka offset acknowledgment **only after successful DB commit**, and immediate Redis Pub/Sub broadcasting to WebSocket handlers. Package structure segregates JPA entities, repositories, services, mappers, and configuration under `com.epam.execution_engine_service.persistence.*`, with dependency injection managed by Spring's component scanning. This design maintains modular monolithic isolation while satisfying acceptance criteria for batched inserts, transactional guarantees, Kafka semantics, and Redis fan-out.

---

## 2. ARCHITECTURAL CONSTRAINTS & DESIGN DECISIONS

### 2.1 Modular Monolith — Direct Embedding (NO Separate Service)

**Decision:** Persist results directly inside the Execution Orchestrator module.

**Rationale:**
- **SRS §2.2, Step 11–14:** Orchestrator receives `ExecutionResultEvent`, persists to DB, publishes to Redis, orchestrates WebSocket forwarding.
- **Avoids:** Separate persistence service, inter-service Kafka topics, distributed transaction complexity.
- **Implements:** Single Spring Boot application with embedded JPA + Redis clients.

**Implication:** All persistence code lives in `execution-engine-service`, not a separate module.

### 2.2 Database: PostgreSQL + Spring Data JPA + Hibernate

**Decision:** Use Spring Data JPA with Hibernate ORM.

**Rationale:**
- Type-safe queries via Spring Data repositories.
- Automatic DDL validation via `spring.jpa.hibernate.ddl-auto: validate`.
- Built-in support for JDBC batching (`hibernate.jdbc.batch_size: 50`, `hibernate.order_inserts: true`).
- Native support for UUID column type and timestamptz.

**Implication:**
- `pom.xml` adds `spring-boot-starter-data-jpa` and `org.postgresql:postgresql` dependencies.
- `application.properties` configures Hibernate batch and DDL settings.

### 2.3 Schema Design — Two-Table Model with FK Relationship

**Decision:** Split results into `submissions` (parent) and `submission_test_results` (child).

**Entity Relationship:**
```
submissions (PK: id BIGSERIAL, UNIQUE: execution_id UUID)
    1:N
submission_test_results (PK: id BIGSERIAL, FK: execution_id → submissions.execution_id)
```

**Rationale:**
- **1:N cardinality:** A submission has 1 to N test results.
- **Batch efficiency:** Hibernate batches 50 test-result inserts per submission.
- **Query patterns:** Fast lookups by `execution_id`, `user_id`, `problem_id` via indexes.
- **Hidden test cases:** All results persisted; filters applied at API layer only.

**Schema Details:**

**`submissions` Table:**
```sql
CREATE TABLE submissions (
    id BIGSERIAL PRIMARY KEY,
    execution_id UUID NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    problem_id VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    mode VARCHAR(20) NOT NULL, -- RUN or SUBMIT
    verdict VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    score NUMERIC(5, 2), -- nullable
    total_runtime_ms BIGINT NOT NULL,
    memory_bytes BIGINT NOT NULL,
    raw_output TEXT,
    error_output TEXT,
    submitted_code TEXT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**`submission_test_results` Table:**
```sql
CREATE TABLE submission_test_results (
    id BIGSERIAL PRIMARY KEY,
    execution_id UUID NOT NULL,
    test_case_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    runtime_ms BIGINT NOT NULL,
    memory_bytes BIGINT NOT NULL,
    expected_output TEXT,
    actual_output TEXT,
    error_output TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_submission_test_results_execution FOREIGN KEY (execution_id) REFERENCES submissions(execution_id)
);
```

**Indexes:**
```sql
CREATE INDEX idx_submissions_user_created_at ON submissions(user_id, created_at DESC);
CREATE INDEX idx_submissions_problem_created_at ON submissions(problem_id, created_at DESC);
CREATE INDEX idx_submission_test_results_execution ON submission_test_results(execution_id);
```

### 2.4 Transactional Guarantees & Kafka ACK Semantics

**Decision:** Atomic persistence + manual Kafka ACK.

**Flow:**
1. Orchestrator receives `ExecutionResultEvent` from Kafka consumer (auto-commit disabled).
2. Service method wraps persistence in `@Transactional(propagation=REQUIRED, isolation=READ_COMMITTED)`.
3. `SubmissionEntity` + `SubmissionTestResultEntity` objects created and flushed to DB.
4. If commit succeeds → Kafka manual ACK (`kafkaTemplate.sendDefault(...)` or `acknowledgment.acknowledge()`).
5. If exception → rollback occurs; no ACK; Kafka redelivers.

**Rationale:**
- Prevents loss of results due to premature ACK.
- Satisfies SRS §2.2, Step 13: "ACK only after DB commit."
- `READ_COMMITTED` isolation prevents phantom reads and dirty-read contamination.

**Implication:**
- Service layer explicitly acknowledges only after commit.
- Exception handling ensures rollback without ACK.

### 2.5 Redis Publishing — Status KV + Pub/Sub Channel

**Decision:** After DB commit, publish to Redis in two ways:
1. **Key-Value:** `SET execution:status:{executionId}` with TTL 600s.
2. **Pub/Sub:** `PUBLISH execution-completed {message}`.

**Rationale:**
- **KV store:** Persistent for 10 minutes; WebSocket handlers query on reconnect.
- **Pub/Sub:** Immediate broadcast to all subscribed handlers.
- **SRS §8:** Caching & real-time notification pattern.

**Message Format:**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123",
  "status": "COMPLETED",
  "verdict": "ACCEPTED",
  "score": 100
}
```

---

## 3. COMPONENT STRUCTURE

### 3.1 Package Organization (SRS §13 Aligned)

**Strict adherence to SRS §13 repository layout:**

```
com.epam.execution_engine_service/
├── gateway/
│   └── [...REST controllers, WebSocket handlers, JWT Auth...]
├── orchestrator/
│   ├── ExecutionOrchestrator.java (calls persistence service)
│   ├── kafka/
│   │   └── [...Kafka consumer, manual ACK handler...]
│   └── [...cache, executor pool management...]
└── persistence/
    ├── entity/
    │   ├── SubmissionEntity
    │   └── SubmissionTestResultEntity
    ├── repository/
    │   ├── SubmissionRepository (Spring Data JPA)
    │   └── SubmissionTestResultRepository (Spring Data JPA)
    ├── service/
    │   ├── ExecutionResultPersistenceService (JPA ops + batch logic + Kafka ACK)
    │   └── ExecutionResultPublishingService (Redis ops)
    ├── mapper/
    │   └── ResultMapper (Maps ExecutionResultEvent → SubmissionEntity)
    ├── event/
    │   ├── ExecutionResultEvent (inbound DTO from Orchestrator)
    │   └── TestCaseResultEvent (inbound DTO - part of ExecutionResultEvent)
    └── config/
        ├── JpaConfig.java (batch properties, DDL validation)
        └── RedisConfig.java (Redis template, serializers)
```

**Rationale:** All classes are collocated within `persistence` component (SRS §13), eliminating external "domain" folder. Event/DTO classes live in `event` subpackage within persistence since they are persistence-specific inbound contracts.

### 3.2 JPA Entity Classes

#### `SubmissionEntity`
```java
@Entity
@Table(
    name = "submissions",
    indexes = {
        @Index(name = "idx_submissions_user_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_submissions_problem_created_at", columnList = "problem_id, created_at")
    }
)
public class SubmissionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, columnDefinition = "UUID")
    private UUID executionId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String problemId;
    
    @Column(nullable = false, length = 50)
    private String language;
    
    @Column(nullable = false, length = 20)
    private String mode; // RUN, SUBMIT
    
    @Column(nullable = false, length = 50)
    private String verdict;
    
    @Column(nullable = false, length = 50)
    private String status;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal score;
    
    @Column(nullable = false)
    private Long totalRuntimeMs;
    
    @Column(nullable = false)
    private Long memoryBytes;
    
    @Column(columnDefinition = "TEXT")
    private String rawOutput;
    
    @Column(columnDefinition = "TEXT")
    private String errorOutput;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String submittedCode;
    
    @Column(nullable = false)
    private OffsetDateTime submittedAt;
    
    @Column(nullable = false)
    private OffsetDateTime completedAt;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "execution_id", referencedColumnName = "execution_id", foreignKey = @ForeignKey(name = "fk_submission_test_results_execution"))
    private List<SubmissionTestResultEntity> testResults = new ArrayList<>();
    
    // Getters, setters, equals, hashCode
}
```

#### `SubmissionTestResultEntity`
```java
@Entity
@Table(
    name = "submission_test_results",
    indexes = {
        @Index(name = "idx_submission_test_results_execution", columnList = "execution_id")
    }
)
public class SubmissionTestResultEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID executionId;
    
    @Column(nullable = false)
    private String testCaseId;
    
    @Column(nullable = false, length = 50)
    private String status;
    
    @Column(nullable = false)
    private Long runtimeMs;
    
    @Column(nullable = false)
    private Long memoryBytes;
    
    @Column(columnDefinition = "TEXT")
    private String expectedOutput;
    
    @Column(columnDefinition = "TEXT")
    private String actualOutput;
    
    @Column(columnDefinition = "TEXT")
    private String errorOutput;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    
    // Getters, setters, equals, hashCode
}
```

### 3.3 Spring Data JPA Repositories

```java
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {
    Optional<SubmissionEntity> findByExecutionId(UUID executionId);
    List<SubmissionEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<SubmissionEntity> findByProblemIdOrderByCreatedAtDesc(String problemId, Pageable pageable);
}

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResultEntity, Long> {
    List<SubmissionTestResultEntity> findByExecutionId(UUID executionId);
}
```

### 3.4 Mapper: ExecutionResultEvent → Entity

```java
@Component
public class ResultMapper {
    
    public SubmissionEntity toEntity(ExecutionResultEvent event) {
        SubmissionEntity entity = new SubmissionEntity();
        entity.setExecutionId(event.getExecutionId());
        entity.setUserId(event.getUserId());
        entity.setProblemId(event.getProblemId());
        entity.setLanguage(event.getLanguage());
        entity.setMode(event.getMode());
        entity.setVerdict(event.getVerdict());
        entity.setStatus(event.getStatus());
        entity.setScore(event.getScore());
        entity.setTotalRuntimeMs(event.getTotalRuntimeMs());
        entity.setMemoryBytes(event.getMemoryBytes());
        entity.setRawOutput(event.getRawOutput());
        entity.setErrorOutput(event.getErrorOutput());
        entity.setSubmittedCode(event.getSubmittedCode());
        entity.setSubmittedAt(event.getSubmittedAt());
        entity.setCompletedAt(event.getCompletedAt());
        
        List<SubmissionTestResultEntity> testResults = event.getTestResults().stream()
            .map(this::toTestResultEntity)
            .collect(Collectors.toList());
        entity.setTestResults(testResults);
        
        return entity;
    }
    
    private SubmissionTestResultEntity toTestResultEntity(TestCaseResultEvent event) {
        SubmissionTestResultEntity entity = new SubmissionTestResultEntity();
        entity.setExecutionId(event.getExecutionId());
        entity.setTestCaseId(event.getTestCaseId());
        entity.setStatus(event.getStatus());
        entity.setRuntimeMs(event.getRuntimeMs());
        entity.setMemoryBytes(event.getMemoryBytes());
        entity.setExpectedOutput(event.getExpectedOutput());
        entity.setActualOutput(event.getActualOutput());
        entity.setErrorOutput(event.getErrorOutput());
        return entity;
    }
}
```

### 3.5 Persistence Service

```java
@Service
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
@Slf4j
public class ResultPersistenceService {
    
    private final SubmissionRepository submissionRepository;
    private final ResultMapper resultMapper;
    
    public void persistExecutionResult(ExecutionResultEvent event) {
        log.info("Persisting execution result for executionId={}", event.getExecutionId());
        
        SubmissionEntity entity = resultMapper.toEntity(event);
        
        // Hibernate batches test results (batch_size: 50)
        submissionRepository.save(entity);
        
        // Flush to ensure DB commit happens
        submissionRepository.flush();
        
        log.info("Successfully persisted execution result for executionId={}", event.getExecutionId());
    }
}
```

### 3.6 Redis Publishing Service

```java
@Service
@Slf4j
public class ResultPublishingService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${app.redis.status-ttl-seconds:600}")
    private long statusTtlSeconds;
    
    public void publishExecutionCompletion(ExecutionResultEvent event) {
        String executionId = event.getExecutionId().toString();
        String key = "execution:status:" + executionId;
        
        String statusJson = convertToJson(event);
        
        // Set KV with TTL
        redisTemplate.opsForValue().set(key, statusJson, Duration.ofSeconds(statusTtlSeconds));
        
        // Publish to channel
        redisTemplate.convertAndSend("execution-completed", statusJson);
        
        log.info("Published execution completion to Redis for executionId={}", executionId);
    }
    
    private String convertToJson(ExecutionResultEvent event) {
        // Jackson ObjectMapper or similar
        return new ObjectMapper().writeValueAsString(event);
    }
}
```

### 3.7 Execution Orchestrator Integration

```java
@Service
@Slf4j
public class ExecutionOrchestrator {
    
    private final ResultPersistenceService persistenceService;
    private final ResultPublishingService publishingService;
    private final KafkaAcknowledgment acknowledgment;
    
    @KafkaListener(topics = "execution-results")
    public void handleExecutionResult(
        ExecutionResultEvent event,
        Acknowledgment ack) {
        
        try {
            // 1. Persist to DB (transactional)
            persistenceService.persistExecutionResult(event);
            
            // 2. Publish to Redis (only after commit)
            publishingService.publishExecutionCompletion(event);
            
            // 3. Acknowledge Kafka (only after all succeeds)
            ack.acknowledge();
            
            log.info("Successfully processed execution result for executionId={}", event.getExecutionId());
        } catch (Exception e) {
            log.error("Failed to process execution result for executionId={}", event.getExecutionId(), e);
            // No ACK; Kafka will redelivery
            throw new RuntimeException("Execution result processing failed", e);
        }
    }
}
```

---

## 4. DATA FLOW DIAGRAM

```
┌─────────────────────────────────────────────────────────────────────┐
│ Kafka Topic: execution-results (manual ACK disabled auto-commit)    │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
                    ExecutionResultEvent
                              ↓
        ┌─────────────────────────────────────┐
        │ ExecutionOrchestrator.handleResult() │
        └─────────────────────────────────────┘
                              ↓
        ┌─────────────────────────────────────────────────┐
        │ ResultPersistenceService.persistExecutionResult │
        │ @Transactional(REQUIRED, READ_COMMITTED)       │
        └─────────────────────────────────────────────────┘
                              ↓
            ┌─────────────────────────────────────┐
            │ ResultMapper.toEntity()              │
            │ (ExecutionResultEvent → Entities)   │
            └─────────────────────────────────────┘
                              ↓
        ┌──────────────────────────────────────────────┐
        │ SubmissionRepository.save(entity)            │
        │ + SubmissionTestResultRepository (batched)   │
        │ Batch size: 50 (hibernate.order_inserts)    │
        └──────────────────────────────────────────────┘
                              ↓
    ┌────────────────────────────────────────────────┐
    │ PostgreSQL: INSERT submissions, test_results   │
    │ (ACID transaction with FK constraints)         │
    └────────────────────────────────────────────────┘
                    (on success) ↓
        ┌─────────────────────────────────────────────┐
        │ ResultPublishingService.publishCompletion() │
        └─────────────────────────────────────────────┘
                              ↓
    ┌──────────────────────────────────────────────┐
    │ Redis SET execution:status:{id} (TTL 600s)  │
    │ Redis PUBLISH execution-completed           │
    └──────────────────────────────────────────────┘
                              ↓
        ┌────────────────────────────────────────┐
        │ Kafka: acknowledgment.acknowledge()     │
        │ (Manual ACK only after DB + Redis ok)  │
        └────────────────────────────────────────┘
```

---

## 5. CONFIGURATION MANAGEMENT

### 5.1 `application.properties`

```properties
# ===== PostgreSQL / JPA =====
spring.datasource.url=jdbc:postgresql://localhost:5432/execution_engine_db
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:password}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true

# ===== Hibernate JDBC Batching =====
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.fetch_size=50

# ===== DDL Strategy =====
spring.jpa.hibernate.ddl-auto=validate

# ===== Kafka Consumer (Manual ACK) =====
spring.kafka.consumer.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=execution-engine-consumer
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=manual

# ===== Redis =====
spring.redis.host=localhost
spring.redis.port=6379
app.redis.status-ttl-seconds=600
```

### 5.2 `application-dev.properties`

```properties
# Development overrides
spring.jpa.properties.hibernate.generate_statistics=true
spring.jpa.properties.hibernate.use_sql_comments=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

---

## 6. MAVEN DEPENDENCIES

### 6.1 New Dependencies to Add to `execution-engine-service/pom.xml`

```xml
<!-- Spring Data JPA + Hibernate -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Lettuce (Redis client) -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>

<!-- Spring Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Lombok (optional, for @Slf4j, @Getter, @Setter) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- Jackson for JSON serialization -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- JUnit 5 & Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- TestContainers for PostgreSQL & Redis -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility for async testing -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.1.0</version>
    <scope>test</scope>
</dependency>
```

---

## 7. TEST STRATEGY

### 7.1 Test Categories (Covering Acceptance Criteria)

#### **Layer 1: Entity & Mapping Tests**
- `SubmissionEntityTest` — JPA column mappings, annotations, UUID handling.
- `SubmissionTestResultEntityTest` — FK relationship, cascading.
- `ResultMapperTest` — `ExecutionResultEvent` → `SubmissionEntity` conversion fidelity.

**Example:**
```java
@Test
void toEntity_convertsEventToSubmissionWithAllFields() {
    ExecutionResultEvent event = createTestEvent();
    SubmissionEntity entity = resultMapper.toEntity(event);
    
    assertThat(entity.getExecutionId()).isEqualTo(event.getExecutionId());
    assertThat(entity.getUserId()).isEqualTo(event.getUserId());
    assertThat(entity.getTestResults()).hasSize(event.getTestResults().size());
}
```

#### **Layer 2: Persistence Service Tests (Batching & Transactions)**
- `ResultPersistenceServiceTest` — Transactional behavior, batching, flush semantics.
- `TransactionalRollbackTest` — Exception during persist triggers rollback.
- `BatchingTest` — 50+ test results inserted in batches, no OOM.

**Example:**
```java
@SpringBootTest
@Transactional
class ResultPersistenceServiceTest {
    
    @Test
    void persistExecutionResult_successfullyInsertsBothTables() {
        ExecutionResultEvent event = createTestEventWith(10, TestResults());
        persistenceService.persistExecutionResult(event);
        
        Optional<SubmissionEntity> persisted = submissionRepository.findByExecutionId(event.getExecutionId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getTestResults()).hasSize(10);
    }
    
    @Test
    void persistExecutionResult_rollbackOnException() {
        // Trigger exception in mapper or service
        assertThatThrownBy(() -> persistenceService.persistExecutionResult(invalidEvent))
            .isInstanceOf(RuntimeException.class);
        
        // Verify no entry in DB
        assertThat(submissionRepository.count()).isZero();
    }
}
```

#### **Layer 3: Kafka ACK Semantics**
- `KafkaIntegrationTest` — Event consumed, persisted, ACK sent.
- `KafkaRollbackTest` — Exception during persist → no ACK → redelivery.
- `KafkaAckOrderTest` — ACK happens **only after** Redis publish.

**Example:**
```java
@SpringBootTest
@EmbeddedKafka
class KafkaIntegrationTest {
    
    @Test
    void handleExecutionResult_acknowledgesOnlyAfterPersistAndRedis() {
        // Send event to Kafka topic
        kafkaTemplate.send("execution-results", testEvent);
        
        // Await persistence in DB
        await().atMost(Duration.ofSeconds(5))
            .until(() -> submissionRepository.findByExecutionId(testEvent.getExecutionId()).isPresent());
        
        // Verify Redis key exists
        String statusKey = "execution:status:" + testEvent.getExecutionId();
        assertThat(redisTemplate.opsForValue().get(statusKey)).isNotNull();
        
        // Verify no redelivery (ACK was sent)
        // Use Kafka consumer group offset tracking
    }
}
```

#### **Layer 4: Redis Publishing**
- `ResultPublishingServiceTest` — KV set + Pub/Sub publish.
- `RedisTTLTest` — Key expires after 600 seconds.
- `RedisPubSubTest` — Pub/Sub message received by subscribers.

**Example:**
```java
@SpringBootTest
class ResultPublishingServiceTest {
    
    @Test
    void publishExecutionCompletion_setsKeyWithTTL() {
        ExecutionResultEvent event = createTestEvent();
        publishingService.publishExecutionCompletion(event);
        
        String key = "execution:status:" + event.getExecutionId();
        String value = redisTemplate.opsForValue().get(key);
        
        assertThat(value).isNotNull();
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(600);
    }
    
    @Test
    void publishExecutionCompletion_publishesToChannel() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        redisTemplate.getConnectionFactory().getConnection().subscribe(
            message -> {
                receivedMessage.set(new String(message.getBody()));
                latch.countDown();
            },
            "execution-completed".getBytes()
        );
        
        publishingService.publishExecutionCompletion(event);
        
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).contains(event.getExecutionId().toString());
    }
}
```

#### **Layer 5: End-to-End Flow (Docker Compose)**
- `E2ETest` — Full flow: Kafka → Persist → Redis → Kafka ACK.
- Uses TestContainers: PostgreSQL, Redis, Kafka (or EmbeddedKafka).

**Example (via Docker Compose):**
```yaml
version: "3.9"
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: execution_engine_db
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
  
  redis:
    image: redis:7
    ports:
      - "6379:6379"
  
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    ports:
      - "9092:9092"
```

### 7.2 Code Coverage Targets
- **Service Layer:** ≥90%
- **Entity & Mapper:** ≥85%
- **Overall:** ≥85% (via JaCoCo)

---

## 8. PACKAGE STRUCTURE & DEPENDENCY INJECTION

### 8.1 Directory Tree (SRS §13 Aligned)
```
execution-engine-service/src/main/java/com/epam/execution_engine_service/
├── ExecutionEngineServiceApplication.java
├── persistence/
│   ├── entity/
│   │   ├── SubmissionEntity.java
│   │   └── SubmissionTestResultEntity.java
│   ├── repository/
│   │   ├── SubmissionRepository.java
│   │   └── SubmissionTestResultRepository.java
│   ├── service/
│   │   ├── ExecutionResultPersistenceService.java
│   │   └── ExecutionResultPublishingService.java
│   ├── event/
│   │   ├── ExecutionResultEvent.java
│   │   └── TestCaseResultEvent.java
│   ├── mapper/
│   │   └── ResultMapper.java
│   └── config/
│       ├── JpaConfig.java
│       └── RedisConfig.java
├── orchestrator/
│   └── ExecutionOrchestrator.java
└── [other modules per SRS §13]
```

### 8.2 Spring Component Scanning
- **`@SpringBootApplication`** enables component scanning for all `@Component`, `@Service`, `@Repository` in `com.epam.execution_engine_service`.
- Repositories auto-proxied via `@EnableJpaRepositories` (inherited from starter).
- Redis template auto-configured via `spring-boot-starter-data-redis`.
- Kafka listener via `@EnableKafka` and `@KafkaListener`.

---

## 9. RISK ASSESSMENT & MITIGATION

### 9.1 Risks

| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| **Kafka duplicate delivery** | Duplicate submissions in DB | Medium | Unique constraint on `execution_id` prevents duplicates; idempotent processing. |
| **Redis unavailable** | Status not published; no Pub/Sub | Medium | Graceful degradation; log warning; continue Kafka ACK (DB is source of truth). |
| **Batch size 50 too large** | Memory spike with large test results | Low | Tune batch size if 50+ test results expected; profile with load test. |
| **Transactional timeout** | Long transactions block connections | Medium | Set `spring.transaction.default-timeout`; add monitoring. |
| **FK constraint violation** | Orphaned test results if submission delete fails | Low | `CascadeType.ALL` + `orphanRemoval=true` handles cascade. |
| **Index bloat on high-volume** | Query slowdown over months | Medium | Schedule REINDEX; monitor query plans; consider partitioning. |
| **DDL validation failure on startup** | Application won't start | Low | Ensure Flyway/Liquibase DDL applied **before** `ddl-auto: validate`. |
| **Kafka offset corruption** | Messages reprocessed after restart | Low | Manual ACK ensures only committed messages; verify consumer group offset. |

### 9.2 Mitigation Strategies

1. **Kafka Idempotence:**
   - Unique constraint on `execution_id` — prevents duplicate rows.
   - Application-level deduplication via cache (Caffeine) if needed.

2. **Redis Resilience:**
   - Non-blocking Redis operations; failures logged but not fatal.
   - Fallback: WebSocket forwarder polls Redis at intervals.

3. **Database Connection Pooling:**
   - `spring.datasource.hikari.maximum-pool-size: 10` (tunable).
   - Monitor active connections; alert on threshold.

4. **Monitoring & Observability:**
   - Log each step: persist, Redis publish, Kafka ACK.
   - Metrics: batching count, persistence latency, Redis ops latency.
   - Use Spring Boot Actuator + Micrometer for Prometheus export.

5. **Load Testing:**
   - Generate 100+ concurrent submissions; validate batch performance.
   - Measure memory footprint, GC impact.

---

## 10. ACCEPTANCE CRITERIA MAPPING

| Criterion | Component | Test |
|---|---|---|
| 1. Entity mapping fidelity | `SubmissionEntity`, `SubmissionTestResultEntity`, `ResultMapper` | `ResultMapperTest.toEntity_*` |
| 2. JPA cascade persist | `SubmissionEntity.testResults` (CascadeType.ALL) | `ResultPersistenceServiceTest.persistExecutionResult_*` |
| 3. Transactional atomicity | `@Transactional(REQUIRED, READ_COMMITTED)` | `TransactionalRollbackTest` |
| 4. Kafka ACK after commit | Manual ACK in `ExecutionOrchestrator.handleResult()` | `KafkaIntegrationTest.handleExecutionResult_acknowledgesOnlyAfter*` |
| 5. Batching (50/batch) | Hibernate config + JDBC batch size | `BatchingTest.largeVolume_batches*` |
| 6. Redis KV + TTL | `ResultPublishingService.publishExecutionCompletion()` | `RedisTTLTest` |
| 7. Redis Pub/Sub | `redisTemplate.convertAndSend()` | `RedisPubSubTest` |
| 8. Hidden test cases | API layer filters; all persisted | `ApiLayerFilterTest` (future) |
| 9. Build + Coverage | JaCoCo plugin | CI pipeline run |

---

## 11. FUTURE ENHANCEMENTS

1. **Query Performance:** Add read replicas for analytics queries.
2. **Archival:** Move old submissions to cold storage after 1 year.
3. **Audit Trail:** Add `updated_at`, `updated_by` for compliance.
4. **Caching Layer:** Caffeine cache on `findByUserId()`, `findByProblemId()` queries.
5. **Metrics Export:** Expose batch metrics, persistence latency to Prometheus.

---

## 12. APPROVAL CHECKLIST

- [ ] Architecture aligns with SRS §2.2, §5, §8, §9, §12.
- [ ] JPA schema matches DDL spec (columns, types, constraints, indexes).
- [ ] Transactional boundaries ensure Kafka ACK semantics.
- [ ] Redis KV + Pub/Sub flow documented and tested.
- [ ] Package structure modular and scalable.
- [ ] Test strategy covers all acceptance criteria.
- [ ] Configuration externalized (application.properties).
- [ ] Risk assessment and mitigation complete.
- [ ] No blocking conflicts with existing architecture.

---

**END OF ARCHITECTURE DESIGN DOCUMENT**
