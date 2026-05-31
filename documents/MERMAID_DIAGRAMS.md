# CodEval Execution Engine — All Mermaid Diagrams

**Source:** Technical Architecture & Performance Report  
**Date:** May 25, 2026  
**System:** CodEval Platform — Module 2: Execution Engine

---

## Diagram 1 — High-Level System Architecture

> Shows the full system topology: client, engine host, supporting infrastructure (Kafka, Redis, PostgreSQL), and sandbox pool with all communication flows.

```mermaid
graph TB
    subgraph Client["👤 Client Layer"]
        Browser["Browser / API Client"]
    end

    subgraph Engine["🖥️ Execution Engine Host"]
        App["codeval-execution-engine\nSpring Boot 3.x | Java 21 | Port 8080\n─────────────────────────────────\n[API Gateway Component]\n[Execution Orchestrator Component]\n[Persistence Component]"]

        subgraph Infra["Supporting Infrastructure (Docker)"]
            Kafka["📨 Apache Kafka\nexecution-tasks topic\n(50 partitions)"]
            Redis["⚡ Redis 7\nStatus KV + Rate Limit + Pub/Sub"]
            PG["🐘 PostgreSQL 16\nSubmissions + Test Results"]
        end

        subgraph Sandbox["🐳 Sandbox Pool (Docker)"]
            S1["sandbox-wrapper:1"]
            S2["sandbox-wrapper:2"]
            S3["sandbox-wrapper:N"]
        end
    end

    Browser -->|"HTTPS POST /api/executions\nWS /ws"| App
    App -->|"Publish ExecutionTaskEvent"| Kafka
    Kafka -->|"Consume"| App
    App -->|"acquire / release"| Sandbox
    App -->|"Status KV + Pub/Sub"| Redis
    App -->|"JPA save()"| PG
    Redis -->|"WS result push"| App
    App -->|"STOMP /user/queue/execution-results"| Browser
```

---

## Diagram 2 — Internal Component Architecture

> Breaks down the monolith into its four internal components (API Gateway, Orchestrator, Persistence, Sandbox Wrapper) with internal data flows.

```mermaid
graph LR
    subgraph EE["codeval-execution-engine (Monolith — Port 8080)"]
        direction TB

        subgraph GW["API Gateway Component"]
            JWT["JWT Auth Filter"]
            RL["Redis Rate Limiter\n(Token Bucket per userId)"]
            REST["REST Controller\nPOST /api/executions\nGET /api/executions/{id}/status"]
            WS["WebSocket Handler\nSTOMP /ws\n/user/queue/execution-results"]
        end

        subgraph ORCH["Execution Orchestrator Component"]
            KL["Kafka Listener\n(Pool A — 50 threads)"]
            OS["Orchestration Service\n(Pool B — 100 threads)"]
            CP["Docker Container Pool\n(BlockingQueue + Semaphore)"]
            CC["Caffeine Cache\nTest Cases (60 min TTL)"]
        end

        subgraph PERSIST["Persistence Component"]
            JPA["Spring Data JPA\nSubmissionRepository\nBatch Size: 50"]
        end

        subgraph SW["Sandbox Wrapper (Standalone JAR)"]
            JC["JavaCompiler API\n(in-memory compile)"]
            CL["ClassLoader Isolation\n(per test case)"]
            EX["Reflective Execution\n+ stdin capture"]
        end
    end

    GW -->|"ExecutionTaskEvent → Kafka"| ORCH
    ORCH -->|"source code + test cases"| SW
    SW -->|"TestCaseResultEvent[]"| ORCH
    ORCH -->|"save submission"| PERSIST
    ORCH -->|"Redis KV + Pub/Sub"| GW
```

---

## Diagram 3 — End-to-End Request Sequence

> Full lifecycle of a single code submission from HTTP POST through Kafka, sandbox execution, DB persistence, and WebSocket result delivery.

```mermaid
sequenceDiagram
    participant C as 👤 Client
    participant API as API Gateway
    participant Redis as Redis
    participant Kafka as Kafka
    participant Orch as Orchestrator
    participant Cache as Caffeine Cache
    participant Pool as Container Pool
    participant SB as Sandbox Container
    participant DB as PostgreSQL

    C->>API: POST /api/executions (JWT + code)
    API->>API: Validate JWT
    API->>Redis: Check rate limit (token bucket)
    Redis-->>API: OK
    API->>Redis: SET execution:status:{id} = PENDING (TTL 600s)
    API->>Kafka: Publish ExecutionTaskEvent (key=userId)
    API-->>C: 202 Accepted + {executionId}

    Note over C,API: Client subscribes to WS /user/queue/execution-results

    Kafka->>Orch: Consume ExecutionTaskEvent (Pool A)
    Orch->>Orch: Hand off to Pool B (async)
    Orch->>Cache: getTestCases(problemId)
    Cache-->>Orch: List<TestCase> (cache hit ~0ms)

    Orch->>Pool: acquire(container, timeout=8000ms)
    Pool-->>Orch: SandboxContainer (ready)

    loop For each test case
        Orch->>SB: send sourceCode + testCase input
        SB->>SB: JavaCompiler.compile() → byte[]
        SB->>SB: new ClassLoader(byte[])
        SB->>SB: Reflect.invoke(solution)
        SB-->>Orch: TestCaseResult (verdict + runtimeMs)
    end

    Orch->>Pool: release(container)
    Orch->>Orch: aggregateVerdict() + calculateScore()
    Orch->>DB: JPA batch save (submission + test results)
    DB-->>Orch: commit OK
    Orch->>Redis: SET execution:status:{id} = COMPLETED
    Orch->>Redis: PUBLISH execution-completed {resultJson}
    Redis->>API: WS subscriber receives event
    API->>C: STOMP push → /user/queue/execution-results
    C->>C: Display verdict + score + runtime
```

---

## Diagram 4 — Sandbox Execution Pipeline (Flowchart)

> Step-by-step flow inside the sandbox container: in-memory compilation, per-test-case ClassLoader isolation, reflective execution, and container reuse.

```mermaid
flowchart TD
    A["📥 Receive source code via TCP socket"] --> B["🔨 Compile in-memory\njavax.tools.JavaCompiler → byte[]"]
    B --> C{Compile OK?}
    C -- No --> D["Return COMPILE_ERROR\n(short-circuit all test cases)"]
    C -- Yes --> E["Loop: For each test case"]
    E --> F["Instantiate new URLClassLoader\n(compiled byte[])"]
    F --> G["Reflection: load Solution class\n(static vars fresh per ClassLoader)"]
    G --> H["Inject stdin / Invoke method\nCapture stdout + timing"]
    H --> I{Output matches\nexpected?}
    I -- Yes --> J["ACCEPTED ✅"]
    I -- No --> K["WRONG_ANSWER ❌\nor RUNTIME_ERROR"]
    J --> L{More test cases?}
    K --> L
    L -- Yes --> F
    L -- No --> M["Stream final results to Orchestrator"]
    M --> N["Drop ClassLoader reference → GC eligible"]
    N --> O["🔁 Container stays alive\nAwaits next submission"]
```

---

## Diagram 5 — Thread Pool & Concurrency Model

> Shows the two-pool architecture: Kafka Listener Pool (Pool A) decoupled from Orchestration Pool (Pool B), both gated by the Semaphore-capped container pool.

```mermaid
graph LR
    subgraph KP["Pool A — Kafka Listener\n(app.kafka.concurrency = 50 threads)"]
        K1[Thread K-1]
        K2[Thread K-2]
        KN[Thread K-N]
    end

    subgraph OP["Pool B — Orchestration\n(app.execution.orchestration-threads = 100 threads)"]
        O1[Thread O-1]
        O2[Thread O-2]
        ON[Thread O-N]
    end

    subgraph POOL["Container Pool\n(Semaphore — 8 slots max)"]
        C1["Container 1"]
        C2["Container 2"]
        C3["Container 3"]
        C8["Container 8"]
    end

    Kafka --> K1 & K2 & KN
    K1 -->|async handoff| O1
    K2 -->|async handoff| O2
    KN -->|async handoff| ON
    O1 -->|acquire/release| C1
    O2 -->|acquire/release| C2
    ON -->|acquire/release| C8

    style POOL fill:#e8f5e9
```

---

## Diagram 6 — Error Handling & Resilience Flows

> Maps every failure scenario (compile error, runtime error, TLE, DB failure, container crash) to its detection point and recovery behaviour.

```mermaid
flowchart TD
    E1["Compile Error\n(user code)"] -->|"Short-circuit"| R1["COMPILE_ERROR verdict\nDB updated\nKafka offset committed"]
    E2["Runtime Exception\n(user code)"] -->|"Reflection catch"| R2["RUNTIME_ERROR for that test case\nLoop continues\nOther test cases still evaluated"]
    E3["Time Limit Exceeded\n(execution > 3000ms)"] -->|"Future.get(timeout)"| R3["Container SIGKILL'd\nReplaced in pool via virtual thread\nTIME_LIMIT_EXCEEDED recorded"]
    E4["DB Unreachable"] -->|"Transaction rollback"| R4["Kafka offset NOT committed\nTask automatically retried\nAt-least-once guarantee"]
    E5["Container Crash\n(OOM / unexpected exit)"] -->|"TCP liveness check fails"| R5["Dead container discarded\nReplacement spawned in background\nSemaphore slot preserved"]
```

---

## Diagram 7 — Observability Pipeline (Prometheus → Grafana)

> Shows how the engine exposes metrics via Spring Boot Actuator, scraped by Prometheus every 15s, and visualised in the Grafana dashboard.

```mermaid
graph LR
    Engine["Execution Engine\n(Spring Boot Actuator)"] -->|"/actuator/prometheus\n(scrape every 15s)"| Prom["Prometheus\n(Time-series DB)"]
    Prom -->|"PromQL queries"| Grafana["Grafana Dashboard"]
    Grafana --> Panels

    subgraph Panels["Dashboard Panels"]
        P1["📊 Total Submissions"]
        P2["📈 Submission Rate (req/s)"]
        P3["⏱️ Avg Submission Time (ms)"]
        P4["🔴 Rate-Limited (429) Count"]
        P5["📨 Kafka Processing Time"]
        P6["🌐 WS Results Sent / Failures"]
        P7["🗑️ GC Pause Time (ms)"]
        P8["💾 JVM Heap Usage"]
    end
```

---

## Diagram 8 — Submit Response Latency: 30 RPS Run (Bar Chart)

> Latency distribution for the 30-RPS burst test: min 8ms, avg 97ms, max 2,813ms across 4,800 requests.

```mermaid
xychart-beta
    title "Submit Response Latency — 30 RPS Run (4,800 requests)"
    x-axis ["Min", "Average", "Max"]
    y-axis "Latency (seconds)" 0 --> 3
    bar [0.008, 0.097, 2.813]
```

---

## Diagram 9 — Submit Response Latency: 50 RPS Run (Bar Chart)

> Latency distribution for the 50-RPS burst test: min 7ms, avg 92ms, max 1,701ms across 9,950 requests. Max latency improved 40% vs the 30 RPS run.

```mermaid
xychart-beta
    title "Submit Response Latency — 50 RPS Run (9,950 requests)"
    x-axis ["Min", "Average", "Max"]
    y-axis "Latency (seconds)" 0 --> 2
    bar [0.007, 0.092, 1.701]
```

---

## Diagram 10 — Execution Verdicts: 30 RPS Run (Pie Chart)

> 100% of 4,800 executions completed. Zero timeouts, zero unresolved.

```mermaid
pie title Execution Verdicts — 30 RPS Run (4,800 requests)
    "COMPLETED" : 4800
    "TIMEOUT" : 0
    "UNRESOLVED" : 0
```

---

## Diagram 11 — Throughput Comparison: 30 RPS vs 50 RPS (Bar Chart)

> Achieved throughput grew +64% (22.5 → 37.0 req/s) with identical hardware, demonstrating clean linear scaling.

```mermaid
xychart-beta
    title "Achieved Throughput: 30 RPS vs 50 RPS (same 8 containers)"
    x-axis ["30 RPS Run", "50 RPS Run"]
    y-axis "Achieved Throughput (req/s)" 0 --> 45
    bar [22.5, 37.0]
```

---

## Diagram 12 — Theoretical vs Achieved Throughput (Bar Chart)

> Achieved throughput (37 req/s) exceeds the theoretical sustained maximum (20 req/s) because Kafka queue buffering absorbs burst waves.

```mermaid
xychart-beta
    title "Theoretical Sustained Max vs Achieved Throughput (req/s)"
    x-axis ["Theoretical Max\n(8 containers @ 400ms)", "30 RPS Test\n(Kafka buffered)", "50 RPS Test\n(Kafka buffered)"]
    y-axis "Throughput (req/s)" 0 --> 45
    bar [20, 22.5, 37.0]
```

---

## Diagram 13 — Full System Pipeline (Condensed)

> All system layers shown accurately with key components — condensed to one or two nodes per zone.

```mermaid
flowchart LR
    USER(["👤 User\nBrowser"])

    subgraph GW["🔐 API Gateway"]
        direction TB
        AUTH{{"JWT Auth · Rate Limit"}}
        PUB(["Write PENDING → Redis\nPublish to Kafka · HTTP 202"])
    end

    subgraph KAFKA["📨 Apache Kafka — execution-tasks · 50 partitions · acks=all · key=userId"]
        direction LR
        PROD(["Producer"])
        TOPIC[("Broker\n0…49")]
        CONS(["Pool A\n50 threads"])
    end

    subgraph ORCH["⚙️ Execution Orchestrator"]
        direction TB
        PB(["Pool B · 100 threads\nCaffeine Cache → Test Cases"])
        SEM{{"Semaphore · 8 slots\nacquire / release"}}
    end

    subgraph SB["🐳 Sandbox Pool — 8 pre-warmed containers"]
        direction TB
        EXEC(["Compile once · JavaCompiler\nnew ClassLoader per test case\nReflect.invoke → verdict"])
    end

    subgraph DELIVER["📡 Persist & Deliver"]
        direction TB
        DB[("🐘 PostgreSQL\nJPA batch save")]
        REDIS[("⚡ Redis KV + Pub/Sub\nstatus=COMPLETED · notify")]
        WS(["🔔 WebSocket\nSTOMP push to user"])
    end

    USER -->|"POST /api/executions"| AUTH
    AUTH --> PUB
    PUB -.->|"202 instantly"| USER
    PUB --> PROD --> TOPIC --> CONS
    CONS --> PB --> SEM
    SEM -->|"acquire"| EXEC
    EXEC -->|"release"| SEM
    EXEC --> DB
    EXEC --> REDIS
    REDIS --> WS
    WS -->|"real-time result"| USER

    style GW      fill:#4a148c,color:#fff,stroke:#6a1b9a
    style KAFKA   fill:#bf360c,color:#fff,stroke:#7f0000
    style ORCH    fill:#0d47a1,color:#fff,stroke:#002984
    style SB      fill:#b71c1c,color:#fff,stroke:#7f0000
    style DELIVER fill:#1b5e20,color:#fff,stroke:#003300
    style USER    fill:#263238,color:#fff,stroke:#37474f
    style AUTH    fill:#6a1b9a,color:#fff,stroke:#4a148c
    style PUB     fill:#6a1b9a,color:#fff,stroke:#4a148c
    style PROD    fill:#e64a19,color:#fff,stroke:#bf360c
    style TOPIC   fill:#e64a19,color:#fff,stroke:#bf360c
    style CONS    fill:#e64a19,color:#fff,stroke:#bf360c
    style PB      fill:#1565c0,color:#fff,stroke:#0d47a1
    style SEM     fill:#880e4f,color:#fff,stroke:#560027
    style EXEC    fill:#7f0000,color:#fff,stroke:#4a0000
    style DB      fill:#2e7d32,color:#fff,stroke:#1b5e20
    style REDIS   fill:#00695c,color:#fff,stroke:#004d40
    style WS      fill:#006064,color:#fff,stroke:#004d40
```

---

## Diagram 14 — Condensed System Pipeline (Visual)

> Clean horizontal pipeline: one node per major stage, colour-coded by layer.

```mermaid
flowchart LR
    USER(["👤 User"])
    GW{{"🔐 API Gateway\nJWT · Rate Limit · 202"}}
    KAFKA[("📨 Kafka\nexecution-tasks")]
    ORCH(["⚙️ Orchestrator\nPool A → Pool B\nCaffeine Cache"])
    SB(["🐳 Sandbox Pool\n8 containers\ncompile · execute"])
    REDIS[("⚡ Redis\nKV · Pub/Sub")]
    WS(["🔔 WebSocket\nSTOMP result"])

    USER -->|"POST /api/executions"| GW
    GW -->|"publish event"| KAFKA
    KAFKA -->|"consume"| ORCH
    ORCH -->|"acquire / release"| SB
    SB -->|"verdict + score"| REDIS
    REDIS -->|"pub/sub notify"| WS
    WS -->|"real-time result"| USER
    GW -.->|"202 Accepted instantly"| USER

    style USER fill:#37474f,color:#fff,stroke:#263238
    style GW fill:#7b1fa2,color:#fff,stroke:#4a148c
    style KAFKA fill:#f57f17,color:#fff,stroke:#e65100
    style ORCH fill:#6a1b9a,color:#fff,stroke:#4a148c
    style SB fill:#c62828,color:#fff,stroke:#b71c1c
    style REDIS fill:#00695c,color:#fff,stroke:#004d40
    style WS fill:#1565c0,color:#fff,stroke:#0d47a1
```

---

*All diagrams extracted from TECHNICAL_ARCHITECTURE_DOCUMENT.md — CodEval Platform, May 25, 2026*


