# 🚀 Local Demo Guide — Execution Engine Service

> **Branch:** `demo-run`  
> **Last Updated:** May 11, 2026

---

## 📋 Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Complete Startup Sequence](#2-complete-startup-sequence)
3. [Generate Google JWT Token](#3-generate-google-jwt-token)
4. [Set Token in Postman](#4-set-token-in-postman)
5. [Import & Run Postman Collection](#5-import--run-postman-collection)
6. [Verify Everything is Working](#6-verify-everything-is-working)
7. [Shutdown](#7-shutdown)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites

Make sure these are installed before starting:

| Tool | Download Link | Purpose |
|---|---|---|
| **Docker Desktop** | https://www.docker.com/products/docker-desktop/ | Runs Redis, Kafka, PostgreSQL |
| **Java 21** | https://adoptium.net/ | Runs the Spring Boot app |
| **Maven** | https://maven.apache.org/install.html | Builds the app (or use IntelliJ) |
| **Postman** | https://www.postman.com/downloads/ | API testing |

Verify Docker is running:
```powershell
docker --version
docker compose version
```

---

## 2. Complete Startup Sequence

Run these commands **in order**, one at a time.

### Step 1 — Navigate to project root

```powershell
cd C:\Users\DevendraKishorMahaja\Downloads\execution-engine-service
```

### Step 2 — Checkout the demo branch

```powershell
git checkout demo-run
```

### Step 3 — Start all Docker services (Redis + Kafka + PostgreSQL + Zookeeper)

```powershell
docker compose up -d
```

### Step 4 — Wait for services to be healthy (~30 seconds)

```powershell
# Check status — all should show 'healthy' or 'Up'
docker ps
```

Expected output:
```
execution-engine-postgres    Up (healthy)   0.0.0.0:5432->5432/tcp
execution-engine-redis       Up (healthy)   0.0.0.0:6379->6379/tcp
execution-engine-kafka       Up             0.0.0.0:9092->9092/tcp
execution-engine-zookeeper   Up             0.0.0.0:2181->2181/tcp
execution-engine-kafka-ui    Up             0.0.0.0:8888->8080/tcp
```

### Step 5 — Verify each service individually

```powershell
# PostgreSQL
docker exec execution-engine-postgres pg_isready -U postgres
# Expected: /var/run/postgresql:5432 - accepting connections

# Redis
docker exec execution-engine-redis redis-cli ping
# Expected: PONG

# Kafka
docker exec execution-engine-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
# Expected: localhost:9092 (id: 1 rack: null) -> ( ...list of APIs... )
```

### Step 6 — Start the Spring Boot Application

**Option A: IntelliJ (Recommended)**
1. Open IntelliJ IDEA
2. Navigate to:
   ```
   execution-engine-service/src/main/java/com/epam/execution_engine_service/ExecutionEngineServiceApplication.java
   ```
3. Right-click → **Run 'ExecutionEngineServiceApplication.main()'**

**Option B: Command Line**
```powershell
cd C:\Users\DevendraKishorMahaja\Downloads\execution-engine-service\execution-engine-service
mvn spring-boot:run
```

### Step 7 — Confirm App is Running

Watch for this in the logs:
```
Tomcat started on port 8080 (http) with context path ''
Started ExecutionEngineServiceApplication in X seconds
```

Then verify:
```powershell
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

---

## 3. Generate Google JWT Token

The app uses Google OAuth2 JWT for authentication. Follow these steps to get a valid token.

### Step 1 — Open Google OAuth 2.0 Playground

👉 Go to: **https://developers.google.com/oauthplayground/**

### Step 2 — Select a Scope

1. In the left panel under **"Step 1 — Select & authorize APIs"**
2. Scroll down and find **"Google OAuth2 API v2"**
3. Expand it and check: ✅ `https://www.googleapis.com/auth/userinfo.email`
4. Click the blue **"Authorize APIs"** button

### Step 3 — Sign In with Google

1. A Google login popup appears
2. Sign in with any Google account (personal Gmail works)
3. Click **"Allow"** to grant permissions

### Step 4 — Exchange Code for Tokens

1. You are redirected back to the playground at **"Step 2"**
2. Click the blue **"Exchange authorization code for tokens"** button
3. You will see a JSON response on the right panel:

```json
{
  "access_token": "ya29.a0AfH6SMB...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 3599,
  "token_type": "Bearer"
}
```

### Step 5 — Copy the `id_token`

> ⚠️ **Important:** Copy the **`id_token`** value (starts with `eyJ`), **NOT** the `access_token`.  
> The app validates Google-issued JWT tokens (`id_token`).

The token is very long (hundreds of characters). Copy the **entire** string.

### Step 6 — Verify Token (Optional)

Paste your `id_token` at **https://jwt.io** to decode and verify it shows:
```json
{
  "iss": "https://accounts.google.com",
  "email": "yourname@gmail.com",
  "exp": 1747000000
}
```
The `iss` field **must be** `https://accounts.google.com`.

> ⏰ **Token expires in 1 hour.** When you start getting `401` errors again, return to the playground and click **"Refresh access token"** to get a new `id_token`.

---

## 4. Set Token in Postman

### Step 1 — Import the Postman Collection

1. Open **Postman**
2. Click **Import** (top left)
3. Browse to and select:
   ```
   documents/ExecutionEngine.postman_collection.json
   ```

### Step 2 — Open Collection Variables

1. In the left sidebar, click on **"Execution Engine Service"** collection
2. Click the **"Variables"** tab at the top

### Step 3 — Paste the Token

Find the `jwt_token` row and paste your `id_token` in the **"Current Value"** column:

| Variable | Initial Value | Current Value |
|---|---|---|
| `base_url` | `http://localhost:8080` | `http://localhost:8080` |
| `jwt_token` | `PASTE_YOUR_GOOGLE_JWT_TOKEN_HERE` | `eyJhbGciOiJSUzI1NiIsInR5cCI6...` ← paste here |
| `execution_id` | _(empty)_ | _(auto-filled after first request)_ |

> ✅ Paste the token **without quotes** — just the raw `eyJ...` string.  
> Postman automatically adds `Bearer ` prefix in the Authorization header.

### Step 4 — Save

Press **Ctrl+S** (or click **Save**) to save the variable.

---

## 5. Import & Run Postman Collection

### Collection Structure

| Folder | Requests | Expected Status |
|---|---|---|
| **1. Health & Monitoring** | Health Check | `200 OK` |
| **2. Submit Execution (Happy Path)** | RUN (Java), SUBMIT (Java), RUN (Python) | `202 Accepted` |
| **3. Validation Errors (400)** | Missing fields, invalid mode, empty body | `400 Bad Request` |
| **4. Authentication Errors (401)** | No token, invalid token, missing Bearer | `401 Unauthorized` |
| **5. Rate Limiting (429)** | 31+ rapid requests | `429 Too Many Requests` |
| **6. Redis Verification** | Health + terminal commands | `200 OK` |

### Running Order (Recommended)

1. ✅ **Health Check** — confirm app is up (no auth needed)
2. ✅ **Submit Code - RUN mode (Java)** — first real API test (saves `execution_id` automatically)
3. ✅ **Submit Code - SUBMIT mode** — second submission
4. ✅ **Validation Errors** — test all 400 scenarios
5. ✅ **Auth Errors** — test 401 scenarios
6. ✅ **Rate Limit** — run via Collection Runner with 35 iterations

### Running the Rate Limit Test (429)

1. Click **"Trigger Rate Limit"** request
2. Click **"Run"** (Collection Runner button, top right of request)
3. Set **Iterations: 35**, **Delay: 0ms**
4. Click **"Run Execution Engine Service"**
5. After 30 requests → responses switch from `202` to `429`

---

## 6. Verify Everything is Working

### Check Redis stored the execution status

After a successful submission, verify the key was written to Redis:

```powershell
# List all execution status keys
docker exec execution-engine-redis redis-cli KEYS "execution:status:*"

# Get value for a specific execution
docker exec execution-engine-redis redis-cli GET "execution:status:<paste-executionId-here>"
# Expected: "PENDING"

# Check rate limit keys
docker exec execution-engine-redis redis-cli KEYS "rate-limit:*"
```

### Check Kafka received the message

Open **http://localhost:8888** (Kafka UI) in your browser:
1. Select the **"local"** cluster
2. Go to **Topics** → `execution-tasks`
3. Click **Messages** tab
4. You should see your submitted message with `userId`, `problemId`, `language`, `mode`

### Check PostgreSQL (for SUBMIT mode only)

```powershell
# Connect to postgres
docker exec -it execution-engine-postgres psql -U postgres -d execution_engine

# Inside psql - list submissions
SELECT execution_id, user_id, problem_id, mode, status, verdict FROM submissions;

# Exit
\q
```

---

## 7. Shutdown

### Stop the Spring Boot App
- IntelliJ: Click the **red Stop button** in the Run panel
- CLI: Press **Ctrl+C** in the terminal running `mvn spring-boot:run`

### Stop Docker Services

```powershell
cd C:\Users\DevendraKishorMahaja\Downloads\execution-engine-service

# Stop containers but KEEP data volumes (use this for normal shutdown)
docker compose down

# Stop containers AND DELETE all data (fresh start next time)
docker compose down -v
```

---

## 8. Troubleshooting

### ❌ `UnknownHostException: kafka`
**Cause:** Kafka advertises internal Docker hostname to host-machine clients.  
**Fix:** Already fixed in `docker-compose.yml` on `demo-run` branch (`PLAINTEXT_HOST://localhost:9092`). Restart Kafka:
```powershell
docker compose stop kafka
docker compose rm -f kafka
docker compose up -d kafka
```

---

### ❌ `BeanDefinitionOverrideException: redisTemplate`
**Cause:** Two `RedisConfig.java` classes both define a `redisTemplate` bean.  
**Fix:** Already fixed — duplicate `config/RedisConfig.java` deleted on `demo-run` branch.

---

### ❌ Redis connection refused / `spring.redis.*` not working
**Cause:** Spring Boot 3.x renamed Redis properties from `spring.redis.*` to `spring.data.redis.*`.  
**Fix:** Already fixed in `application.properties` on `demo-run` branch.

---

### ❌ `401 Unauthorized` on all API calls
**Cause:** JWT token expired (tokens expire after 1 hour) or wrong token type.  
**Fix:**
1. Go to https://developers.google.com/oauthplayground/
2. Click **"Refresh access token"** on Step 2
3. Copy the **new `id_token`** (not `access_token`)
4. Update `jwt_token` in Postman collection variables

---

### ❌ App fails to start — Google issuer-uri connection error
**Cause:** `issuer-uri` causes Spring to fetch Google's discovery document at startup.  
**Fix:** Already fixed — `issuer-uri` commented out in `application.properties` on `demo-run` branch.

---

### ❌ `validate` Flyway/Hibernate schema error
**Cause:** PostgreSQL not running when app starts, so Flyway can't run migrations.  
**Fix:** Always start Docker services first (Step 3 above) and wait for `healthy` before launching the app.

---

### ❌ Kafka UI shows no messages
**Cause:** The `kafka-ui` connects to Kafka via internal Docker network (`kafka:29092`), which is correct.  
**Note:** If you submitted a request and see no messages, check the Spring Boot app logs for Kafka producer errors.

---

## Quick Reference

| Service | URL / Port | Credentials |
|---|---|---|
| **Spring Boot App** | http://localhost:8080 | — |
| **Health Check** | http://localhost:8080/actuator/health | — |
| **Kafka UI** | http://localhost:8888 | — |
| **PostgreSQL** | localhost:5432 | user: `postgres` / pass: `postgres` / db: `execution_engine` |
| **Redis** | localhost:6379 | no password |
| **Kafka** | localhost:9092 | — |
| **Zookeeper** | localhost:2181 | — |

| File | Location |
|---|---|
| **Main Application Class** | `execution-engine-service/src/main/java/com/epam/execution_engine_service/ExecutionEngineServiceApplication.java` |
| **Application Properties** | `execution-engine-service/src/main/resources/application.properties` |
| **Docker Compose** | `docker-compose.yml` (project root) |
| **Postman Collection** | `documents/ExecutionEngine.postman_collection.json` |
| **Flyway Migration SQL** | `execution-engine-service/src/main/resources/db/migration/V001__create_persistence_schema.sql` |

