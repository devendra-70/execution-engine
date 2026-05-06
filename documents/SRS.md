Team 2 Component-JAN2026-JAVA-TEAM2

CODEVAL PLATFORM

Module 2 --- Execution Engine

Low Level Design Document

Version	1.0
Date	21 April 2026
Status	Proposed Baseline
1. Module Overview
The Execution Engine module is the secure, high-performance code evaluation backbone of the CodEval platform. It is responsible for accepting user code submissions, routing them through a Kafka-backed task queue, executing them inside hardened sandboxed containers, and returning real-time results to the client over WebSocket.

This module is architecturally separate from the CodEval Service (Module 1) and is composed of four independently deployable Spring Boot microservices, a custom JVM sandbox wrapper, and a set of managed AWS infrastructure components.

Sub-Module	Primary Role	Core Responsibility
2.1 API Gateway Service	System / User	Auth, rate limiting, Kafka publishing, WebSocket session registry
2.2 Execution Worker Service	System	Kafka consumption, sandbox orchestration, result publishing
2.3 Result Persistence Service	System	Async PostgreSQL write from Kafka result topic
2.4 Sandbox Wrapper	System	In-memory JVM compile, reflective execution
2. Architecture Overview
2.1 Service Boundaries
The platform is decomposed into four microservices, each with clearly scoped responsibilities. There is no direct synchronous coupling between services; all inter-service communication flows through Kafka topics or Redis Pub/Sub.

Service	Port	Technology Stack
api-gateway-service	8080	Java 21, Spring Boot 3.x, Spring Security OAuth2, Spring WebSocket, Spring Kafka, Spring Data Redis
execution-worker-service	8081	Java 21, Spring Boot 3.x, Spring Kafka, Redis client, Container runtime integration
result-persistence-service	8082	Java 21, Spring Boot 3.x, Spring Kafka, Spring Data JPA, PostgreSQL driver
sandbox-wrapper	N/A (stdin/socket)	Java 21, javax.tools.JavaCompiler, custom ClassLoader, no Spring dependency
2.2 End-to-End Runtime Flow
The following sequence describes the complete lifecycle of a code submission from the client browser to final persistence:

Client connects through AWS ALB over HTTPS/WebSocket.

api-gateway-service authenticates the request via OAuth2 JWT.

Gateway applies Redis-based token-bucket rate limiting per userId and IP.

Gateway generates a UUID executionId, writes PENDING status to Redis KV (execution:status:{executionId}, TTL 10 min).

Gateway publishes an ExecutionTaskEvent to Kafka topic execution-tasks (keyed by userId).

Gateway returns HTTP 202 Accepted with executionId to client.

execution-worker-service consumes the message from Kafka.

Worker fetches test cases from Redis cache (problem:testcases:{problemId}) or falls back to the database.

Worker acquires a pre-warmed sandbox container from the pool.

Worker injects source code into the sandbox via tmpfs path or local socket.

Sandbox wrapper compiles the code in-memory and executes it with resource limits enforced.

Worker evaluates all test case results and aggregates a verdict.

Worker writes the final result to Redis KV (execution:status:{executionId}) and publishes to Redis Pub/Sub channel execution-completed.

All API Gateway instances receive the Pub/Sub broadcast; only the instance holding the matching WebSocket session forwards the result to the client.

Worker publishes a durable ExecutionResultEvent to Kafka topic execution-results.

result-persistence-service consumes the event and writes to PostgreSQL.

If the WebSocket connection dropped, the client polls GET /api/executions/{executionId}/status to retrieve the result from Redis KV.

3. Sub-Module 2.1 --- API Gateway Service
3.1 Overview
The API Gateway Service is the single entry point for all client requests. It handles authentication, rate limiting, Kafka publishing, WebSocket session management, and result forwarding. It is horizontally scalable and stateless with respect to execution state --- all state is maintained in Redis.

3.2 Responsibilities
Authenticate every inbound request using Spring Security OAuth2 Resource Server (JWT bearer token).

Enforce per-user and per-IP rate limits using a Redis token-bucket algorithm. Return HTTP 429 on breach.

Accept POST /api/executions requests, validate the payload, generate a UUID executionId.

Write initial PENDING status to Redis KV with a 10-minute TTL.

Publish ExecutionTaskEvent to Kafka topic execution-tasks keyed by userId.

Maintain an in-memory WebSocket/STOMP session registry keyed by userId.

Subscribe to Redis Pub/Sub channel execution-completed and forward results to the correct WebSocket session.

Serve REST fallback GET /api/executions/{executionId}/status reading from Redis KV.

3.3 API Endpoints
Method	Endpoint	Auth	Description
POST	/api/executions	Bearer JWT	Submit code for execution. Returns executionId + PENDING status.
GET	/api/executions/{executionId}/status	Bearer JWT	REST fallback. Returns current execution status from Redis KV.
GET	/actuator/health	None	Kubernetes readiness/liveness probe.
WS	/ws (STOMP)	Bearer JWT	WebSocket endpoint. Client subscribes to /user/queue/execution-results.
3.4 Request / Response Contracts
POST /api/executions --- Request
Field	Type	Required	Description
problemId	String	Yes	Unique identifier for the coding problem (e.g., two-sum).
language	String	Yes	Language/runtime tag (e.g., java21).
mode	Enum: RUN | SUBMIT	Yes	RUN = preview results only. SUBMIT = score and persist.
sourceCode	String	Yes	Full source code submitted by the user.
POST /api/executions --- Response (202 Accepted)
Field	Type	Description
executionId	UUID	Unique identifier for this execution job.
status	String	Always PENDING at submission time.
submittedAt	ISO-8601 Timestamp	Server-side timestamp of submission.
WebSocket / STOMP Result Payload
Field	Type	Description
executionId	UUID	Matches the executionId returned at submission.
userId	String	User who submitted the code.
status	Enum	COMPLETED | FAILED | TIMEOUT
verdict	Enum	PASSED | WRONG_ANSWER | TIME_LIMIT_EXCEEDED | RUNTIME_ERROR | COMPILE_ERROR
score	Integer	Score (0--100). Populated only for SUBMIT mode.
testResults	Array	Per-test-case result objects (see Worker section).
3.5 Rate Limiting Design
Rate limiting is implemented as a Redis token-bucket per userId with a secondary bucket per IP. The token replenishment rate and burst capacity are configurable via application.yml. The Gateway enforces rate limiting before publishing to Kafka.

Parameter	Default Value	Description
requests-per-minute	30	Maximum sustained requests per user per minute.
status-ttl-seconds	600	TTL for execution status keys in Redis (10 minutes).
3.6 Security Configuration
All routes are secured via Spring Security OAuth2 Resource Server. JWT tokens are validated against the configured issuer URI. The WebSocket endpoint and health probe are explicitly permitted without authentication.

Route Pattern	HTTP Method	Auth Required
/actuator/health, /ws/**	Any	No --- permitted for health checks and WebSocket handshake
/api/executions	POST	Yes --- authenticated user
/api/executions/**	GET	Yes --- authenticated user
All other routes	Any	Denied
4. Sub-Module 2.2 --- Execution Worker Service
4.1 Overview
The Execution Worker Service is the core execution orchestrator. It consumes tasks from Kafka, acquires pre-warmed sandbox containers, coordinates code injection and execution, aggregates results, and publishes outcomes to Redis and Kafka.

4.2 Thread Pool Architecture
The Worker maintains two independent thread pools to prevent Kafka consumer threads from being blocked by container I/O:

Pool	Name	Size	Purpose
Pool A	Kafka Consumer Listener Pool	25 threads (concurrency: 25)	Consumes Kafka messages, hands off to Pool B, acknowledges offset.
Pool B	ExecutionTaskExecutor (ThreadPoolExecutor)	25 threads	Handles container acquisition, code injection, execution I/O, and result publishing.
Design Rationale: Separating Kafka consumer threads (Pool A) from container I/O threads (Pool B) ensures that slow executions never stall Kafka offset acknowledgement, preventing consumer group rebalancing and message redelivery.
4.3 Execution Orchestration
Each incoming ExecutionTaskEvent is processed by the ExecutionOrchestrator service via the following sequence:

Fetch test cases from Redis cache (problem:testcases:{problemId}). If cache miss, load from database and populate cache.

Acquire a SandboxSession from the pre-warmed SandboxPoolService.

Submit execution to Pool B via CompletableFuture.supplyAsync(..., executionTaskExecutor).

Apply a hard timeout using future.orTimeout(timeoutMs, MILLISECONDS).

On success: pass ExecutionResultEvent to ResultPublisher.

On timeout or exception: create a failure ExecutionResultEvent via FailureResultFactory.timeoutOrFailure(...) and publish.

In finally block: return SandboxSession to the pool via sandboxPoolService.release(session).

4.4 Pre-Warmed Container Pool
To eliminate JVM cold-start latency, the Worker maintains a pool of idle, pre-warmed containers. Each container runs the sandbox-wrapper application in a persistent listening state, waiting for source code to be injected.

Parameter	Value	Description
warm-pool-min-size	3	Minimum idle containers maintained at all times.
idle-container-ttl-seconds	300	Containers idle for more than 5 minutes are destroyed and replaced.
timeout-ms	3000	Maximum allowed execution time per submission (3 seconds).
orchestration-threads	25	Size of Pool B (ExecutionTaskExecutor).
The pool reactively scales up when Kafka consumer lag increases (traffic spike detected). Idle containers are recycled by a scheduled sweeper job (see Sub-Module 2.5 --- Process Cleanup).

4.5 Result Publishing
After execution completes, the ResultPublisher performs two actions in sequence:

Write the complete ExecutionResultEvent to Redis KV at key execution:status:{executionId} with TTL of result-ttl-seconds (600s). This overwrites the PENDING status written by the Gateway.

Publish the result to Redis Pub/Sub channel execution-completed (for immediate WebSocket delivery). Then publish to Kafka topic execution-results (for durable persistence).

4.6 Kafka Consumer Configuration
Parameter	Value	Description
group-id	execution-workers	Kafka consumer group for load distribution across Worker instances.
auto-offset-reset	earliest	Start from the earliest unprocessed message on new group assignment.
enable-auto-commit	false	Manual offset acknowledgement --- offset committed only after execution completes.
concurrency	25	Number of concurrent Kafka listener threads per Worker instance.
ack-mode	manual	Acknowledgement is called explicitly in the listener after processing.
5. Sub-Module 2.3 --- Result Persistence Service
5.1 Overview
The Result Persistence Service is a headless background consumer with no public API. It reads completed execution results from the Kafka topic execution-results and writes them durably to Amazon RDS (PostgreSQL 15+). It is the only service that writes to the primary relational database.

5.2 Responsibilities
Consume ExecutionResultEvent messages from Kafka topic execution-results (consumer group: result-persistence).

Map the event to SubmissionEntity and SubmissionTestResultEntity JPA entities.

Persist submission records and per-test-case results to PostgreSQL using batch inserts (batch_size: 50).

Acknowledge Kafka offset only after successful database write.

5.3 Database Schema
submissions
Column	Type	Constraints	Description
id	BIGSERIAL	PK	Auto-incrementing primary key.
execution_id	UUID	UNIQUE NOT NULL	Links to the executionId generated by the Gateway.
user_id	VARCHAR(64)	NOT NULL	Identifier of the submitting user.
problem_id	VARCHAR(128)	NOT NULL	Identifier of the coding problem.
language	VARCHAR(32)	NOT NULL	Language/runtime tag used for this submission.
mode	VARCHAR(16)	NOT NULL	RUN or SUBMIT.
verdict	VARCHAR(32)	NOT NULL	Final verdict: PASSED, WRONG_ANSWER, TLE, RE, COMPILE_ERROR.
status	VARCHAR(32)	NOT NULL	COMPLETED, FAILED, or TIMEOUT.
score	INTEGER		Score awarded (SUBMIT mode only).
total_runtime_ms	BIGINT		Total wall-clock execution time across all test cases.
memory_bytes	BIGINT		Peak memory consumed during execution.
raw_output	TEXT		Concatenated stdout from all test cases.
error_output	TEXT		Concatenated stderr or compile error messages.
submitted_code	TEXT	NOT NULL	Full source code submitted by the user.
submitted_at	TIMESTAMPTZ	NOT NULL	Timestamp of original submission.
completed_at	TIMESTAMPTZ		Timestamp when execution completed.
created_at	TIMESTAMPTZ	DEFAULT NOW()	Record insertion timestamp.
submission_test_results
Column	Type	Constraints	Description
id	BIGSERIAL	PK	Auto-incrementing primary key.
execution_id	UUID	FK → submissions.execution_id	Foreign key linking to the parent submission.
test_case_id	VARCHAR(128)	NOT NULL	Identifier of the specific test case.
status	VARCHAR(32)	NOT NULL	PASSED, FAILED, TIME_LIMIT_EXCEEDED, or RUNTIME_ERROR.
runtime_ms	BIGINT		Execution time for this test case in milliseconds.
memory_bytes	BIGINT		Memory used for this test case.
expected_output	TEXT		Expected output string for this test case.
actual_output	TEXT		Actual output produced by the user\'s code.
error_output	TEXT		Error message if status is RUNTIME_ERROR or TLE.
created_at	TIMESTAMPTZ	DEFAULT NOW()	Record insertion timestamp.
5.4 Indexes
Index Name	Columns	Purpose
idx_submissions_user_created_at	user_id, created_at DESC	Efficient retrieval of a user\'s submission history in reverse chronological order.
idx_submissions_problem_created_at	problem_id, created_at DESC	Efficient retrieval of all submissions for a given problem.
idx_submission_test_results_execution	execution_id	Fast join from submission_test_results to submissions.
6. Sub-Module 2.4 --- Sandbox Wrapper
6.1 Overview
The Sandbox Wrapper is a lightweight, standalone Java application (no Spring dependency) that runs persistently inside each pre-warmed container. It listens for incoming source code, compiles it entirely in JVM memory using the javax.tools.JavaCompiler API, and executes the compiled class by reflection. This design eliminates disk I/O and file system overhead for compile-and-run cycles.

6.2 In-Memory Compilation Pipeline
Wrapper listens on stdin or a local Unix socket for incoming source code.

Wrapper invokes javax.tools.JavaCompiler.getTask(...) targeting a DiagnosticCollector and a custom JavaFileManager that routes output to a ByteArrayOutputStream.

If compilation fails, the DiagnosticCollector captures all error messages. These are returned immediately as a COMPILE_ERROR result. No execution occurs.

If compilation succeeds, the bytecode resides in the ByteArrayOutputStream.

A custom URLClassLoader (or equivalent ClassLoader) loads the bytecode from the stream directly into JVM memory --- no .class file is written to disk.

Java Reflection is used to locate the target class (e.g., Solution), find the designated solution method or main(), and invoke it with the test case input fed via System.setIn().

stdout and stderr are captured by redirecting System.out and System.err to ByteArrayOutputStreams before invocation. They are restored after execution completes.

The wrapper returns a structured result (status, stdout, stderr, runtime_ms) to the Worker over the same channel.

6.3 JVM Runtime Configuration
The following JVM flags are applied to the wrapper JVM process inside each sandbox container to minimise startup and execution overhead:

JVM Flag	Purpose
-XX:TieredStopAtLevel=1	Disables the C2 JIT optimising compiler. Only the fast C1 compiler is used. Reduces startup latency and eliminates background CPU profiling overhead.
-XX:+UseEpsilonGC	Uses the Epsilon (no-op) garbage collector. Eliminates all GC pauses and CPU overhead. Heap is never reclaimed; the container is destroyed after execution.
-Xms256m -Xmx256m	Fixes heap at exactly 256 MB. Prevents dynamic heap resizing overhead and enforces the problem\'s memory limit.
-XX:SharedArchiveFile=app-cds.jsa	Class Data Sharing (CDS) --- pre-warmed containers load a CDS archive of the JDK standard library from a memory-mapped file, reducing boot time significantly.
6.4 Security Hardening Inside the Container
Control	Setting	Purpose
User	Non-root UID/GID	Prevents container escape via privilege escalation.
Root filesystem	readOnlyRootFilesystem: true	Prevents user code from writing persistent files.
Privilege escalation	allowPrivilegeEscalation: false	Blocks setuid/setgid execution.
Linux capabilities	drop: ALL	Removes all kernel capabilities from the container.
Seccomp profile	RuntimeDefault or custom	Blocks dangerous syscalls (ptrace, unshare, etc.).
Network	--network none	Disables all outbound network access from user code.
Memory limit	256 Mi	Cgroup memory limit matches JVM heap setting.
CPU limit	1000m (1 vCPU)	Prevents CPU starvation of neighbouring workloads.
PID limit	64	Prevents fork-bomb attacks from user-submitted code.
Init process	tini as PID 1	Automatically reaps orphaned child processes.
tmpfs mount	Ephemeral RAM disk for code injection	Source code is injected into a tmpfs path --- never touches persistent storage.
7. Kafka Topic Design
7.1 Topic: execution-tasks
Parameter	Value	Description
Partitions	100	High partition count enables up to 100 concurrent Worker consumers.
Replication factor	3	Data durability across three Kafka brokers.
Message key	userId	All submissions from a single user land on the same partition, preserving order.
Retention	24 hours	Messages are retained for 24 hours to allow delayed consumer recovery.
Producer	api-gateway-service	Idempotent producer with acks=all and lz4 compression.
Consumer group	execution-workers	Shared across all Worker instances for load distribution.
7.2 Topic: execution-results
Parameter	Value	Description
Partitions	24	Lower partition count --- persistence is async and lower throughput than execution.
Replication factor	3	Data durability across three Kafka brokers.
Message key	executionId	Results are keyed by executionId for ordered persistence.
Retention	72 hours	Extended retention to allow late consumer recovery or reprocessing.
Producer	execution-worker-service	Worker publishes after writing to Redis.
Consumer group	result-persistence	Consumed exclusively by result-persistence-service.
8. Redis Key Design
Key Pattern	TTL	Written By	Read By	Purpose
execution:status:{executionId}	600 s (10 min)	Gateway (PENDING) → Worker (result)	Gateway (REST fallback), Worker	Stores execution lifecycle state from PENDING through to final result.
ratelimit:user:{userId}	Rolling window	Gateway	Gateway	Token bucket counter for per-user rate limiting.
ratelimit:ip:{ip}	Rolling window	Gateway	Gateway	Token bucket counter for per-IP rate limiting.
problem:testcases:{problemId}	600 s -- 3600 s	Worker (on cache miss)	Worker	Cached test case list for a problem. TTL varies by problem volatility.
Pub/Sub Channel: execution-completed --- Worker publishes result events. All Gateway instances subscribe. Only the instance holding the matching WebSocket session forwards the result to the client. Others discard.
9. Java Domain Model
9.1 ExecutionRequest (Gateway Inbound DTO)
Field	Type	Description
problemId	String	Identifier of the coding problem.
language	String	Runtime tag (e.g., java21f).
mode	String	RUN or SUBMIT.
sourceCode	String	Full source code submitted by the user.
9.2 ExecutionTaskEvent (Kafka Message --- execution-tasks)
Field	Type	Description
executionId	UUID	Unique job identifier generated by the Gateway.
userId	String	Identifier of the submitting user (also the Kafka message key).
problemId	String	Identifier of the coding problem.
language	String	Runtime tag.
mode	String	RUN or SUBMIT.
sourceCode	String	Full source code to execute.
submittedAt	Instant	Submission timestamp.
9.3 ExecutionResultEvent (Kafka Message --- execution-results / Redis)
Field	Type	Description
executionId	UUID	Matches the executionId from the task event.
userId	String	Submitting user.
problemId	String	Coding problem.
verdict	String	PASSED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR.
status	String	COMPLETED, FAILED, or TIMEOUT.
score	Integer	Score (0--100, SUBMIT mode only).
totalRuntimeMs	long	Total wall-clock execution time.
memoryBytes	long	Peak memory used.
rawOutput	String	Concatenated stdout.
errorOutput	String	Concatenated stderr or compile errors.
submittedCode	String	Full submitted source code.
submittedAt	Instant	Original submission timestamp.
completedAt	Instant	Execution completion timestamp.
testResults	List<TestCaseResultEvent>	Per-test-case result breakdown (see below).
9.4 TestCaseResultEvent
Field	Type	Description
testCaseId	String	Identifier of the test case.
status	String	PASSED, FAILED, TIME_LIMIT_EXCEEDED, or RUNTIME_ERROR.
runtimeMs	long	Execution time for this test case.
memoryBytes	long	Memory used for this test case.
expectedOutput	String	Expected output (for FAILED results).
actualOutput	String	Actual output produced by user code.
errorOutput	String	Error message if status is not PASSED.
10. Error Handling & Edge Cases
Scenario	Detected At	Behaviour	User Sees
Compile error	Sandbox Wrapper	Short-circuit; no test cases executed. COMPILE_ERROR verdict returned.	Compiler error message. No test results.
Runtime error	Sandbox Wrapper (per test case)	Captured as RUNTIME_ERROR per test case. Remaining test cases continue.	Error message per failing test case.
Timeout (per test case)	Worker (CompletableFuture orTimeout)	Container process SIGKILLed. TIME_LIMIT_EXCEEDED recorded.	TLE status per test case.
Wrong answer	Worker (output comparison)	Output mismatch. FAILED recorded with actual vs expected.	Actual vs expected output (for visible test cases).
Worker crash / thread failure	Sweeper cron job (60s)	Orphaned containers SIGKILLed. Kafka offset not acknowledged --- message redelivered.	No immediate impact. Execution retried.
WebSocket connection drop	Client-side / Gateway	Client polls REST fallback GET /api/executions/{executionId}/status.	Result retrieved from Redis KV within 10-minute TTL.
Redis write failure	ResultPublisher	IllegalStateException thrown. Kafka offset not acknowledged. Worker retries.	Possible duplicate execution on retry.
Rate limit exceeded	API Gateway	HTTP 429 Too Many Requests returned immediately. Request not published to Kafka.	429 error with retry-after guidance.
11. Integration Points
11.1 Integration with CodeVal Service (Module 1)
The Execution Engine exposes its REST and WebSocket API to be consumed by the CodeVal Service\'s Admin and User Code Editor pages. The CodeVal Service acts as a client to the Execution Engine\'s API Gateway.

CodeVal Service Call	Execution Engine Endpoint	Description
POST /api/editor/run (Admin/User Code Editor)	POST /api/executions (mode: RUN)	Run code against test cases. Results streamed back via WebSocket.
POST /api/editor/submit (Admin/User Code Editor)	POST /api/executions (mode: SUBMIT)	Final submission. Results persisted to PostgreSQL. Score returned.
Note: The CodeVal Service constructs the ExecutionRequest payload (problemId, language, mode, sourceCode) and forwards the executionId to the client browser. The browser then manages the WebSocket subscription independently.
11.2 AWS Managed Service Integration
Component	AWS Service	Notes
Kafka broker	Amazon MSK (Managed Streaming for Apache Kafka)	Both execution-tasks and execution-results topics hosted here.
Redis cache / Pub/Sub	Amazon ElastiCache for Redis	Shared by all Gateway and Worker instances.
PostgreSQL database	Amazon RDS for PostgreSQL 15+	Single writer: result-persistence-service. Read access for submission history.
Load balancer	AWS Application Load Balancer (ALB)	Terminates TLS, routes HTTP/WebSocket to api-gateway-service pods.
Container orchestration	Amazon EKS (Elastic Kubernetes Service)	All microservices and execution containers run on EKS EC2 Node Groups.
12. Kubernetes Deployment Baseline
12.1 Deployment Summary
Service	Replicas	CPU Request/Limit	Memory Request/Limit
api-gateway-service	3 (min)	500m / 1000m	512Mi / 1Gi
execution-worker-service	4 (min)	2000m / 4000m	2Gi / 4Gi
result-persistence-service	2 (min)	250m / 500m	256Mi / 512Mi
12.2 Horizontal Pod Autoscaler (HPA) Guidance
Service	Scale Trigger	Rationale
api-gateway-service	CPU utilisation + p95 request latency + active WebSocket session count	Session count reflects real-time load more accurately than CPU alone.
execution-worker-service	CPU utilisation + Kafka consumer group lag	Kafka lag is the primary indicator of execution queue build-up.
result-persistence-service	Kafka consumer group lag on execution-results topic	Scale persistence consumers when write backlog grows.
12.3 Node Selection
Execution Worker pods must be scheduled on dedicated EC2 node groups with the node label workload: execution. This isolates execution workloads from other services to prevent noisy-neighbour CPU and memory contention on shared nodes.

12.4 Health Probes
Probe	Path	Notes
Readiness	/actuator/health/readiness	Checked before routing traffic. Fails if Kafka or Redis connections are not ready.
Liveness	/actuator/health/liveness	Restarts pod if the application is in an unrecoverable state.
13. Operational SLOs and Alerting
13.1 Service Level Objectives
Metric	Target	Measurement
Submit API availability	99.9%	Percentage of POST /api/executions requests returning 2xx or 429.
p95 submit latency	< 300 ms (excluding execution time)	Time from request receipt to Kafka publish completion.
p95 end-to-end execution (warm sandbox, Java)	< 2 seconds	Time from Kafka publish to WebSocket result delivery.
Result delivery success rate	> 99.5%	Percentage of submitted jobs whose results are delivered via WebSocket or REST fallback.
13.2 Critical Alerts
Alert	Condition	Severity
Kafka consumer lag high	execution-workers group lag > threshold for > 2 min	Critical
Redis memory above threshold	Redis memory utilisation > 80%	Warning
Worker timeout rate elevated	Timeout rate > 5% of executions over 5 min	Warning
Sandbox acquisition latency rising	p95 pool.acquire() time > 500 ms	Warning
PostgreSQL insert failure rate	INSERT failures > 0.1% over 5 min	Critical
WebSocket session lookup failures	Session not found events rising	Warning
14. Application Configuration Reference
14.1 api-gateway-service (application.yml)
Property	Default	Description
server.port	8080	HTTP server port.
app.execution.topic	execution-tasks	Kafka topic for publishing execution tasks.
app.execution.status-ttl-seconds	600	TTL for Redis execution status keys.
app.websocket.endpoint	/ws	STOMP WebSocket endpoint path.
app.rate-limit.requests-per-minute	30	Per-user rate limit (token bucket).
spring.kafka.producer.properties.enable.idempotence	true	Idempotent Kafka producer (exactly-once semantics).
spring.kafka.producer.properties.acks	all	Wait for all in-sync replicas to acknowledge.
spring.kafka.producer.properties.compression.type	lz4	Message compression for reduced bandwidth.
14.2 execution-worker-service (application.yml)
Property	Default	Description
server.port	8081	HTTP server port (health probes only).
app.execution.task-topic	execution-tasks	Kafka topic to consume from.
app.execution.result-topic	execution-results	Kafka topic to publish results to.
app.execution.timeout-ms	3000	Maximum execution time per submission (ms).
app.execution.result-ttl-seconds	600	TTL for result keys in Redis.
app.execution.orchestration-threads	25	Size of Pool B (ExecutionTaskExecutor).
app.execution.warm-pool-min-size	3	Minimum pre-warmed containers in the pool.
app.execution.idle-container-ttl-seconds	300	Idle container expiry time (5 minutes).
spring.kafka.listener.concurrency	25	Size of Pool A (Kafka consumer threads).
spring.kafka.listener.ack-mode	manual	Offsets acknowledged manually after processing.
14.3 result-persistence-service (application.yml)
Property	Default	Description
server.port	8082	HTTP server port (health probes only).
app.persistence.topic	execution-results	Kafka topic to consume results from.
spring.kafka.consumer.group-id	result-persistence	Dedicated consumer group for persistence.
spring.jpa.hibernate.ddl-auto	validate	Schema is managed externally; Hibernate validates only.
spring.jpa.properties.hibernate.jdbc.batch_size	50	Batch insert size for efficient PostgreSQL writes.
spring.jpa.properties.hibernate.order_inserts	true	Optimises batch ordering for INSERT statements.
15. Repository Layout
Path	Contents
platform/api-gateway-service/	Spring Boot API Gateway source code.
platform/execution-worker-service/	Spring Boot Execution Worker source code.
platform/result-persistence-service/	Spring Boot Result Persistence Service source code.
platform/sandbox-wrapper/	Lightweight JVM sandbox wrapper source code (no Spring).
platform/deploy/kubernetes/	Raw Kubernetes manifests (Deployments, Services, HPAs, PodSecurityContext).
platform/deploy/helm/	Helm chart for environment-parameterised deployment.
platform/deploy/terraform/	Terraform modules for MSK, ElastiCache, RDS, EKS, ALB provisioning.
platform/docs/	Architecture documents, this LLD, and API specifications.
16. Recommended Build Order
api-gateway-service --- establishes the entry point, auth, and Kafka publishing.

execution-worker-service --- core execution orchestration and Redis publishing.

result-persistence-service --- async persistence consumer.

sandbox-wrapper --- in-memory JVM compile/execute application.

Kubernetes and managed AWS service infrastructure (EKS, MSK, ElastiCache, RDS).

Observability stack --- Prometheus, Grafana, alerting rules, and SLO dashboards.

Glossary
Term	Definition
API Gateway Service	The Spring Boot service that authenticates requests, rate-limits users, and publishes execution tasks to Kafka.
CDS	Class Data Sharing --- a JVM feature that memory-maps pre-loaded class metadata to reduce boot time.
CompletableFuture	A Java async construct used to wrap container execution with a hard timeout.
Epsilon GC	A no-op JVM garbage collector that never reclaims memory, eliminating GC pauses.
Execution Worker Service	The Spring Boot service that consumes Kafka tasks and orchestrates sandbox container execution.
ExecutionId	A UUID generated by the Gateway that uniquely identifies a single code submission job.
Kafka Partition Key (userId)	Using userId as the Kafka message key ensures all submissions from one user land on the same partition, preserving order.
Pre-Warmed Container	A sandbox container pre-started with the JVM loaded and the wrapper listening, eliminating cold-start latency.
Redis Pub/Sub	A publish/subscribe channel used to broadcast execution results from Workers to all Gateway instances.
Result Persistence Service	The headless Spring Boot service that reads completed results from Kafka and writes them to PostgreSQL.
Sandbox Wrapper	The lightweight Java application inside each container that performs in-memory compilation and reflective execution.
Seccomp Profile	A Linux kernel feature that restricts the syscalls a process is permitted to make.
tmpfs	A RAM-backed filesystem mount used for ephemeral code injection into sandbox containers.
TLE	Time Limit Exceeded --- execution was forcibly terminated for exceeding the configured timeout.
Verdict	The final outcome of a code execution: PASSED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, or COMPILE_ERROR.
--- End of Document ---