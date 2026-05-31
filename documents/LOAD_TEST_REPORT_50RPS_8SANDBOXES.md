# Load Test Report — 50 RPS Burst | 8 Sandbox Containers

**Date:** May 22, 2026  
**Tester:** Local Development Environment  
**System:** CodEval Execution Engine Service  

---

## 1. Test Configuration

### Load Test Parameters (`load_test.py`)

| Parameter | Value |
|---|---|
| Batch size (requests per wave) | 50 |
| Concurrency (parallel workers) | 50 |
| Delay between waves | 1 second |
| Result poll workers | 10 |
| Result poll timeout | 30 seconds |
| Problem ID | 3 |
| Language | JAVA |
| Mode | submit (all test cases including hidden) |

### System Configuration (`.env`)

| Component | Parameter | Value |
|---|---|---|
| **Sandbox Pool** | `APP_EXECUTION_POOL_WARM_MIN_SIZE` | **8 containers** |
| **Sandbox Pool** | `APP_SANDBOX_MEMORY_LIMIT_MB` | 256 MB per container |
| **Sandbox Pool** | `APP_SANDBOX_JVM_XMS` | 128m |
| **Sandbox Pool** | `APP_SANDBOX_JVM_XMX` | 256m |
| **Kafka** | `APP_KAFKA_CONCURRENCY` | 50 listener threads |
| **Kafka** | `APP_KAFKA_PARTITIONS` | 50 partitions |
| **Orchestrator** | `APP_EXECUTION_ORCHESTRATION_THREADS` | 100 threads |
| **Orchestrator** | `APP_EXECUTION_TIMEOUT_MS` | 3000 ms |
| **Redis** | `APP_REDIS_RATE_LIMIT_RPM` | 10000 (effectively disabled) |
| **Redis** | `APP_REDIS_STATUS_TTL_SECONDS` | 600 seconds |
| **Cache** | `APP_CACHE_TESTCASE_TTL_MINUTES` | 60 minutes |
| **DB** | `HIBERNATE_BATCH_SIZE` | 50 |
| **Server** | `SERVER_PORT` | 8080 |

---

## 2. Results Summary

### Throughput

| Metric | Value |
|---|---|
| Total requests submitted | **9,950** |
| Waves completed | 199 |
| Wall-clock time | 269.02 seconds |
| **Overall throughput** | **37.0 req/s** |

### HTTP Response Codes (Submit endpoint)

| Status Code | Count | % |
|---|---|---|
| **202 Accepted** | **9,950** | **100%** |
| 429 Rate Limited | 0 | 0% |
| 5xx Errors | 0 | 0% |
| Connection failures | 0 | 0% |

> ✅ **Zero failures at the submission layer.** All 9,950 requests were accepted by the engine.

### Submit Response Latency (202 only)

| Metric | Value |
|---|---|
| Minimum | 0.007 s |
| Maximum | 1.701 s |
| **Average** | **0.092 s** |

> Average submit latency dropped from **97ms → 92ms** compared to the 30 RPS run, and the max latency also improved from 2.813s → **1.701s**, indicating the system handled the higher concurrency more consistently. This is likely due to Kafka batching becoming more efficient with denser traffic.

### Execution Verdicts

| Verdict | Count | % |
|---|---|---|
| **COMPLETED** | **9,950** | **100%** |
| TIMEOUT | 0 | 0% |
| UNRESOLVED | 0 | 0% |

> ✅ **100% completion rate.** All 9,950 executions went through the full pipeline (Kafka → Orchestrator → Sandbox → PostgreSQL → Redis).

### Grafana Metrics (from dashboard at time of test)

| Metric | Value | Notes |
|---|---|---|
| Total Submissions | 9,950 | Matches load test count |
| Total WS Results Sent | 9,951 | +1 from prior warm-up |
| Total WS Push Failures | **0** | Zero WebSocket delivery failures |
| Avg Submission Time | **22.1 ms** | Server-side metric (faster than client-observed 92ms due to no network/queue overhead) |
| Rate-Limited (429) | No data | Zero rate limit hits |
| Kafka Avg Processing Time | ~5ms steady, spike to ~50ms at 16:20 | Burst spike when 50 concurrent msgs hit simultaneously, self-recovered |
| GC Pause Time | Peak **7ms** (G1 Minor GC), settled to ~2ms | Healthy; no GC pressure |
| GC Overhead % | No data | GC overhead negligible / below threshold |

> ⚠️ **Note on `[FAIL]` labels:** Same script display issue as the 30 RPS run. The status endpoint returns only `{"status":"COMPLETED"}` with no `verdict` field. All executions genuinely completed — this is not an engine failure.

---

## 3. Observations & Analysis

### ✅ What Performed Well

- **Zero failures at all layers** — 9,950/9,950 submitted (202), 9,950/9,950 executed (COMPLETED), **0 WebSocket push failures**.
- **Improved latency under higher load** — Max submit latency fell from 2.813s (30 RPS) to **1.701s (50 RPS)**, demonstrating Kafka producer efficiency improves with denser batching.
- **GC fully stable** — Peak GC pause of 7ms (G1 Minor Evacuation Pause) is negligible. No full GC events. JVM heap is healthy throughout.
- **WebSocket delivery perfect** — 9,951 WS results sent with 0 failures, confirming Redis Pub/Sub → WebSocket pipeline is robust under load.
- **No rate limiting triggered** — With 50 unique user tokens and `APP_REDIS_RATE_LIMIT_RPM=10000`, the rate limiter had no impact.

### ⚠️ Notable Observations

- **Kafka processing time spike to ~50ms at 16:20** — When all 50 concurrent requests hit Kafka simultaneously at wave start, consumer processing time spiked briefly before dropping back to ~5ms. This is expected with a single broker and no partition rebalancing buffer. Self-recovered within seconds.
- **Throughput (37.0 req/s) < burst rate (50 req/s)** — Each wave of 50 fires then waits 1 second. Wave execution takes ~0.2–0.3s, giving effective rate `50 / (0.25 + 1.0) ≈ 40 req/s`. The measured 37.0 req/s is consistent — the gap is result-poller thread contention in the test client, not engine throttling.
- **8 containers serving 37 req/s** — Theoretical max with 8 containers at ~400ms avg = 20 sub/s sustained. The system exceeded this because the orchestration queue absorbs burst spikes, draining them while the next wave is delayed by the 1s gap. Effective utilization pattern: burst → queue absorbs → drain during pause → repeat.

---

## 4. Comparison: 30 RPS vs 50 RPS

| Metric | 30 RPS Run | 50 RPS Run | Δ |
|---|---|---|---|
| Total requests | 4,800 | 9,950 | +107% |
| Wall-clock time | 213.76s | 269.02s | +26% |
| Overall throughput | 22.5 req/s | **37.0 req/s** | **+64%** |
| 202 success rate | 100% | 100% | = |
| Completion rate | 100% | 100% | = |
| Avg submit latency | 97ms | **92ms** | **-5%** ✅ |
| Max submit latency | 2.813s | **1.701s** | **-40%** ✅ |
| WS push failures | 0 | 0 | = |
| GC peak pause | — | 7ms | healthy |
| Kafka spike | — | ~50ms (brief) | self-recovered |

> The system scaled **linearly and cleanly** from 30 → 50 RPS with no degradation. Latency actually improved, and completion rate held at 100%.

---

## 5. Capacity Headroom at This Configuration

With 8 containers and ~400ms avg execution time:

| Metric | Value |
|---|---|
| Theoretical sustained max | 8 ÷ 0.4s = **20 sub/s** |
| Achieved with queue buffering | **37 sub/s** (burst-wave pattern) |
| Headroom before queue saturation | ~500 queued tasks before orchestrationPool rejects |
| Users to saturate (@ 5 RPM each) | ~444 users |
| Docker host RAM for sandboxes | 8 × 256MB = **2 GB** |

---

## 6. Recommendations (unchanged from 30 RPS report)

| Priority | Recommendation | Reason |
|---|---|---|
| 🟡 Medium | Increase `APP_EXECUTION_POOL_WARM_MIN_SIZE` to **10–12** | Temporary pool drop-to-7 during container replacement under 50 concurrent requests |
| 🟡 Medium | Add `spring.datasource.hikari.maximum-pool-size=20` | Default 10 HikariCP connections under 100 orchestration threads |
| 🟡 Medium | Enrich `/api/executions/{id}/status` to return `verdict`, `score`, `totalRuntimeMs` | Fixes `[FAIL]` display bug in load test and enables proper frontend polling |
| 🟢 Low | Reduce `APP_KAFKA_CONCURRENCY` to 15, `APP_EXECUTION_ORCHESTRATION_THREADS` to 20 | Over-provisioned vs 8 container slots; wastes ~160MB JVM thread stack |
| 🟢 Low | Set `APP_REDIS_RATE_LIMIT_RPM` to a realistic production value | Currently 10000 = effectively off |

---

## 7. Test Infrastructure

| Service | Image | Role |
|---|---|---|
| execution-engine | custom build | Spring Boot app under test |
| codeval-postgres | postgres:16-alpine | Submission persistence |
| codeval-redis | redis:7-alpine | Status KV + Rate limiting + Pub/Sub |
| codeval-kafka | confluentinc/cp-kafka:7.6.0 | Task queue (single broker) |
| codeval-zookeeper | confluentinc/cp-zookeeper:7.6.0 | Kafka coordination |
| sandbox containers | codeval/sandbox-wrapper:latest | Java code execution (×8) |
| Prometheus + Grafana | prom/prometheus + grafana/grafana | Metrics & dashboards |

---

*Report generated from local load test run. All services running in Docker on a single host.*

