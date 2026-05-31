# 📘 Execution Engine Service — API Documentation

**Base URL:** `http://<host>:8080`

---

## 🔐 Authentication

All `/api/**` endpoints (except `/api/dev/**`) require a **JWT Bearer Token** in the HTTP header:

```http
Authorization: Bearer <jwt-token>
```

The JWT must contain a `userId` claim (numeric). Tokens are validated using HMAC-SHA with the configured secret key.

---

## 📡 REST Endpoints

### 1. Submit Code for Execution

**`POST /api/executions`**

Submits source code for execution against problem test cases. The request is processed **asynchronously** via Kafka.

#### Request Headers

| Header          | Value                |
|-----------------|----------------------|
| `Authorization` | `Bearer <jwt-token>` |
| `Content-Type`  | `application/json`   |

#### Request Body

```json
{
  "problemId": 42,
  "language": "JAVA",
  "mode": "run",
  "sourceCode": "public class Solution { ... }"
}
```

| Field        | Type     | Required | Description                                               |
|--------------|----------|----------|-----------------------------------------------------------|
| `problemId`  | `Long`   | ✅        | ID of the problem to run/submit against                   |
| `language`   | `String` | ✅        | Programming language (e.g., `JAVA`)                       |
| `mode`       | `String` | ✅        | `"run"` (sample tests only) or `"submit"` (all test cases)|
| `sourceCode` | `String` | ✅        | The source code to execute                                |

#### Response — `202 Accepted`

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

#### Error Responses

| Status                  | Description                                                   |
|-------------------------|---------------------------------------------------------------|
| `400 Bad Request`       | Validation failed (missing or blank required fields)          |
| `401 Unauthorized`      | Missing or invalid JWT token                                  |
| `429 Too Many Requests` | Rate limit exceeded (default: **5 requests/minute** per user) |

```json
{ "error": "Rate limit exceeded. Try again later." }
```

---

### 2. Get Execution Status

**`GET /api/executions/{executionId}/status`**

Polls the current status of a previously submitted execution. Status is stored in Redis with a TTL (default: **10 minutes**).

#### Request Headers

| Header          | Value                |
|-----------------|----------------------|
| `Authorization` | `Bearer <jwt-token>` |

#### Path Parameters

| Parameter     | Type            | Description                                        |
|---------------|-----------------|----------------------------------------------------|
| `executionId` | `String` (UUID) | The execution ID returned from the submit endpoint |

#### Response — `200 OK`

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED"
}
```

#### Possible Status Values

| Status       | Description                                           |
|--------------|-------------------------------------------------------|
| `PENDING`    | Submitted, waiting to be picked up by a worker        |
| `PROCESSING` | Currently being executed in the sandbox               |
| `COMPLETED`  | Execution finished — check WebSocket for full results |
| `FAILED`     | Execution failed unexpectedly                         |

#### Error Responses

| Status             | Description                                      |
|--------------------|--------------------------------------------------|
| `404 Not Found`    | `executionId` not found or Redis TTL has expired |
| `401 Unauthorized` | Missing or invalid JWT token                     |

---

## 🔌 WebSocket API (Real-Time Results)

The service pushes final execution results to the client over **STOMP over SockJS**.

### Connection Endpoint

```
ws://<host>:8080/ws
```

> SockJS fallback is also supported (HTTP long-polling, etc.)

### Authentication (STOMP CONNECT frame)

Send the JWT in one of these STOMP headers:

```
CONNECT
Authorization: Bearer <jwt-token>
```

Or alternatively:

```
CONNECT
token: <jwt-token>
```

### Subscribing to Results

After connecting, subscribe to your personal result queue:

```
SUBSCRIBE /user/queue/execution-results
```

> The `/user/` prefix ensures messages are routed **only** to the authenticated user's session.

### Result Message Payload

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": 123,
  "problemId": 42,
  "problemName": "Two Sum",
  "verdict": "ACCEPTED",
  "score": 100,
  "totalRuntimeMs": 250,
  "memoryBytes": 10485760,
  "testCaseResults": [
    {
      "testCaseId": 1,
      "verdict": "ACCEPTED",
      "errorMessage": null
    }
  ]
}
```

### Verdict Values

| Verdict                 | Description                                           |
|-------------------------|-------------------------------------------------------|
| `ACCEPTED`              | All test cases passed                                 |
| `WRONG_ANSWER`          | Output did not match expected result                  |
| `COMPILE_ERROR`         | Code failed to compile                                |
| `RUNTIME_ERROR`         | Code threw an exception during execution              |
| `TIME_LIMIT_EXCEEDED`   | Execution exceeded time limit (default: **3000 ms**)  |
| `MEMORY_LIMIT_EXCEEDED` | Execution exceeded memory limit (default: **256 MB**) |
| `PENDING`               | Result not yet available                              |

---

## ❤️ Health & Monitoring (Actuator)

These endpoints are **public** — no authentication required.

| Endpoint            | Method | Description                              |
|---------------------|--------|------------------------------------------|
| `/actuator/health`  | `GET`  | Service health status (DB, Redis, Kafka) |
| `/actuator/info`    | `GET`  | Application build/version info           |
| `/actuator/metrics` | `GET`  | Application performance metrics          |

---

## 🔄 Typical Client Flow

```
1. POST /api/executions
      → Receive { executionId, status: "PENDING" }

2. Connect WebSocket to /ws
      → STOMP CONNECT with JWT
      → SUBSCRIBE /user/queue/execution-results

3. (Optional) Poll GET /api/executions/{executionId}/status
      → Wait for status: "COMPLETED"

4. Receive WebSocket push message
      → Display verdict, score, and per-test-case results
```

---

## ⚙️ Default Configuration Reference

| Setting                   | Default Value | Environment Variable           |
|---------------------------|---------------|--------------------------------|
| Server port               | `8080`        | `SERVER_PORT`                  |
| Execution timeout         | `3000 ms`     | `APP_EXECUTION_TIMEOUT_MS`     |
| Sandbox memory limit      | `256 MB`      | `APP_SANDBOX_MEMORY_LIMIT_MB`  |
| Rate limit (per user/min) | `5`           | `APP_REDIS_RATE_LIMIT_RPM`     |
| Execution status TTL      | `600 sec`     | `APP_REDIS_STATUS_TTL_SECONDS` |

