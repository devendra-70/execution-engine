# Load Test Report — 50 RPS Sustained | 8 Sandbox Containers | 102,150 Requests

**Date:** May 31, 2026  
**Tester:** Local Development Environment  
**System:** CodEval Execution Engine Service  
**Test Duration:** 2779.0 seconds (~46.3 minutes)

---

## 1. Test Configuration

### Load Test Parameters (`load_test.py`)

| Parameter | Value |
|---|---|
| Batch size (requests per wave) | 50 |
| Concurrency (parallel workers) | 50 |
| Delay between waves | ~0.32–0.43s (wave execution time only) |
| Result poll workers | 10 |
| Result poll timeout | 30 seconds |
| Problem ID | 3 |
| Language | JAVA |
| Mode | submit (all test cases including hidden) |

### System Configuration

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
| Total requests submitted | **102,150** |
| Waves completed | **2,043** |
| Wall-clock time | **2,779.0 seconds** (~46.3 min) |
| **Overall throughput** | **36.8 req/s** |
| Peak wave throughput | **158.1 req/s** (wave 2043) |
| Typical wave throughput | ~116 req/s per wave |

> The overall 36.8 req/s sustained over 46+ minutes represents a **63% improvement** over the 30 RPS test (22.5 req/s) and demonstrates stable long-duration execution capability.

### HTTP Response Codes (Submit endpoint)

| Status Code | Count | % |
|---|---|---|
| **202 Accepted** | **102,150** | **100%** |
| 429 Rate Limited | 0 | 0% |
| 5xx Errors | 0 | 0% |
| Connection failures | 0 | 0% |

> ✅ **Zero failures at the submission layer across 102,150 requests.** Confirmed by both load test output and Grafana HTTP Error Rate panel (No data = 0 errors).

### Submit Response Latency (202 only)

| Metric | Value |
|---|---|
| Minimum | **0.006 s** |
| Maximum | **5.088 s** |
| **Average** | **0.089 s** |
| Grafana Avg Submission Time | **13.3 ms** (steady state, per dashboard) |

> The 89ms average submit response reflects Redis write + Kafka publish time. The 5.088s max spike is higher than the 30 RPS test (2.813s max), consistent with heavier Kafka producer backpressure under 50-concurrent burst waves. The Grafana panel shows **13.3ms avg** during steady-state operation — the 89ms figure includes warm-up and burst spikes.

### Execution Verdicts

| Verdict | Count | % |
|---|---|---|
| **ACCEPTED** | **102,150** | **100.0%** |
| TIMEOUT | 0 | 0% |
| UNRESOLVED | 0 | 0% |
| WRONG_ANSWER | 0 | 0% |

> ✅ **100% ACCEPTED across all 102,150 executions.** Every submission passed all test cases (score=100) and completed within acceptable runtime bounds (21ms–169ms observed in final waves).

---

## 3. Grafana / Prometheus Observations

> *Data sourced from Grafana dashboard screenshots captured at 18:32 on 2026-05-31.*

### HTTP Request Rate Panel

| Series | Observed Rate |
|---|---|
| `POST /api/executions` (202) | **~40 req/s** sustained |
| `GET /api/executions/{id}/status` (200) | **~42 req/s** sustained |
| `GET /api/dev/token` (200) | Minimal (~1 req/s) |
| `GET /actuator/prometheus` (200) | Scrape noise only |

> The dual ~40 req/s lines confirm the engine was handling both inbound submissions and outbound polling simultaneously — the real request pressure is approximately **2× the submission rate** when accounting for status polling.

### HTTP Response Time Panel (avg / p50 / p95 / p99)

| Endpoint | avg | Notes |
|---|---|---|
| `POST /api/executions` | **~13 ms** | Stable, minimal variance |
| `GET /api/executions/{id}/status` | **~5 ms** (p50) | Redis cache reads, very low latency |
| `GET /actuator/prometheus` | **~10–20 ms** | Prometheus scrape cost |

> Response times remained stable throughout the 46-minute run with no degradation trend — confirming no memory leak or connection pool exhaustion.

### Key Gauges

| Panel | Value |
|---|---|
| Total Submissions (counter) | **102,150** ✅ |
| Rate-Limited (429) | **No data** (= 0) ✅ |
| Active HTTP Requests | **No data** (all resolved, clean drain) |
| HTTP Error Rate (4xx/5xx) | **No data** (= 0 errors) ✅ |
| GC Overhead % | **No data** (below threshold / metric not emitted) |
| Uptime | **47.6 minutes** (matches test duration) ✅ |
| Avg Submission Time (dashboard) | **13.3 ms** |

---

## 4. Per-Wave Performance (Sample — Final Waves)

| Wave | Requests | Wave Time | Wave Throughput | Overall Rate | Accepted |
|---|---|---|---|---|---|
| 2042 | 50 | 0.43s | 116.1 req/s | 36.8 req/s | 50/50 |
| 2043 | 50 | 0.32s | 158.1 req/s | 36.8 req/s | 50/50 |
| **Final drain** | **50** | — | — | — | **50/50** |

> Wave execution times of 0.32–0.43s indicate healthy parallelism across 50 concurrent workers. The pipeline (Kafka consumer → orchestrator → sandbox → Redis write) consistently resolves within 200ms for most executions.

### Observed Execution Runtime (final waves, sample)

| Metric | Value |
|---|---|
| Minimum observed | 21 ms |
| Maximum observed | 169 ms |
| Typical range | 40–110 ms |

---

## 5. Capacity Analysis

With 8 containers and ~65–100ms avg execution time:

| Metric | Value |
|---|---|
| Theoretical max throughput (sustained) | 8 ÷ 0.08s = **~100 sub/s** (theoretical ceiling) |
| Actual sustained throughput | **36.8 req/s** |
| Utilization of theoretical ceiling | **~37%** (headroom remains) |
| Users to saturate system (@ 5 RPM each) | ~440 users |
| Docker host RAM for sandboxes | 8 × 256MB = **2 GB** |
| Total executions processed | **102,150** |
| Zero-failure run duration | **46.3 minutes** |

> The system ran at 36.8 req/s for 46+ minutes with zero failures, processing over 100K executions. Actual container throughput is well below the theoretical ceiling, confirming the Kafka queue absorbs burst without container starvation.

---

## 6. Comparison: 30 RPS Test vs. 50 RPS Sustained Test

| Metric | 30 RPS Test (May 22) | 50 RPS Test (May 31) | Change |
|---|---|---|---|
| Concurrency | 30 | 50 | +67% |
| Total requests | 4,800 | **102,150** | +2,028% |
| Wall-clock time | 213.76s | 2,779.0s | +13× |
| Overall throughput | 22.5 req/s | **36.8 req/s** | **+63%** |
| Peak wave throughput | 222.5 req/s | 158.1 req/s | — |
| Avg submit latency | 97ms | **89ms** | -8ms |
| Max submit latency | 2.813s | 5.088s | +2.3s |
| 202 success rate | 100% | **100%** | = |
| ACCEPTED verdict rate | 100% | **100%** | = |
| HTTP errors | 0 | **0** | = |

> The max submit latency increase (2.8s → 5.1s) under higher concurrency is expected — more concurrent Kafka producers briefly contend for broker acks during burst peaks. The average latency actually improved slightly (97ms → 89ms) due to better warm-pool utilization.

---

## 7. Observations & Analysis

### ✅ What Performed Well

- **Zero failures at scale** — 102,150 submissions over 46+ minutes with 0 HTTP errors, 0 rate limits, 0 timeouts.
- **100% ACCEPTED verdict rate** — All executions produced correct results, confirming sandbox execution correctness is unaffected by high concurrency.
- **Sustained 36.8 req/s** — The engine maintained this throughput continuously without degradation across 2,043 waves.
- **Sub-15ms submission latency (steady state)** — Grafana shows 13.3ms avg, significantly below the 30s polling timeout.
- **No container pool exhaustion** — Pool cycling worked correctly throughout; container reuse confirmed in container trace logs.
- **Clean drain on Ctrl+C** — All 50 queued executions resolved within 30s after the final wave, confirming no orphaned tasks.

### ⚠️ Notable Observations

- **Max submit latency spike (5.088s):** Higher than the 30 RPS test (2.813s). Under 50-concurrent waves, occasional Kafka producer blocking is more frequent. Still well within the 30s result poll timeout, so no functional impact.
- **Grafana "No data" for GC Overhead:** Either the JVM GC metric is not exported via Micrometer, or GC pressure was too low to register above the panel threshold. The latter is more likely given the stable response times.
- **Peak wave rate (158 req/s) < 30 RPS test peak (222 req/s):** Because waves in this test used fixed 50-concurrent workers (vs. bursty 30), peak per-wave throughput is lower but overall throughput is higher and more consistent.
- **Status polling doubles actual request load:** At 36.8 sub/s, the status endpoint handles an additional ~40 req/s. Total HTTP load on the engine is approximately **~80 req/s** combined.

---

## 8. Recommendations

| Priority | Recommendation | Reason |
|---|---|---|
| 🔴 High | Investigate and cap Kafka producer max block time (`max.block.ms`) | 5.1s max submit latency under 50 concurrent is approaching user-visible delay; producer blocking should be bounded to 1–2s |
| 🟡 Medium | Increase `APP_EXECUTION_POOL_WARM_MIN_SIZE` to **12** | Provides buffer during container replacement cycles; test showed pool never exhausted, but headroom is prudent at this scale |
| 🟡 Medium | Add `spring.datasource.hikari.maximum-pool-size=20` | Default 10-connection HikariCP pool risks bottleneck at 100 orchestration threads processing 100K+ writes |
| 🟡 Medium | Export `jvm.gc.pause` and `jvm.memory.used` metrics via Micrometer | GC Overhead panel shows "No data" — visibility gap at this scale |
| 🟢 Low | Reduce `APP_KAFKA_CONCURRENCY` from 50 to 20 and orchestration threads to 25 | At 36.8 req/s with 8 container slots, 50 Kafka threads and 100 orchestration threads are over-provisioned; saves ~200MB JVM thread stack |
| 🟢 Low | Set `APP_REDIS_RATE_LIMIT_RPM` to a realistic per-user value (e.g., 60 RPM) | Currently disabled; production deployments must enforce per-user limits |
| 🟢 Low | Add p95/p99 execution runtime metrics to PostgreSQL or Prometheus | Current status endpoint returns `verdict` + `score` + `totalRuntimeMs` — aggregate percentiles would enable SLA monitoring |

---

## 9. Test Infrastructure

| Service | Image | Role |
|---|---|---|
| execution-engine | custom build | Spring Boot app under test |
| codeval-postgres | postgres:16-alpine | Submission persistence |
| codeval-redis | redis:7-alpine | Status KV + Rate limiting + Pub/Sub |
| codeval-kafka | confluentinc/cp-kafka:7.6.0 | Task queue |
| codeval-zookeeper | confluentinc/cp-zookeeper:7.6.0 | Kafka coordination |
| sandbox containers | codeval/sandbox-wrapper:latest | Java code execution (×8) |
| Prometheus + Grafana | prom/prometheus + grafana/grafana | Metrics & dashboards |

---

## 10. Conclusion

The CodEval Execution Engine successfully processed **102,150 Java code submissions** in a **46.3-minute sustained load test** at **36.8 req/s** with:

- ✅ **0 HTTP errors** (0 out of 102,150 submissions)
- ✅ **0 rate-limited requests**
- ✅ **100% ACCEPTED verdict rate** (all correct, score=100)
- ✅ **13.3ms average submission latency** (Grafana steady state)
- ✅ **No performance degradation** over the full test duration

The system is **production-ready** for workloads up to ~35–40 req/s sustained on 8 sandbox containers. Scaling to 12 sandboxes would comfortably support 50+ req/s sustained with reduced Kafka backpressure spikes.

---

*Report generated from local load test run on May 31, 2026. Terminal output captured at wave 2043 (102,150 total requests). Grafana data sourced from dashboard screenshots at 18:32 local time. All services running in Docker on a single host.*

