# CodEval Execution Engine — Startup Guide

## Prerequisites
- Docker Desktop running
- Java 21 + Maven (or use `mvnw`)
- Git

---

## Full Stack Startup (Recommended)

### Step 1 — Build Maven modules (jar files)
```powershell
./mvnw clean package -DskipTests
```

### Step 2 — Build the sandbox-wrapper Docker image
```powershell
docker build -t codeval/sandbox-wrapper:latest -f sandbox-wrapper/Dockerfile .
```

### Step 3 — Start infrastructure (Postgres, Redis, Zookeeper, Kafka, Kafka-UI)
```powershell
docker compose up postgres redis zookeeper kafka kafka-ui -d
```
Wait ~20–30 seconds for Kafka to be fully healthy before proceeding.

### Step 4 — Build & start the execution engine container
```powershell
docker compose up --build execution-engine -d
```
The execution engine will automatically spawn sandbox containers (min pool size) on startup.

### Step 5 — Verify everything is running
```powershell
docker ps
```
Expected containers:
- `codeval-postgres`
- `codeval-redis`
- `codeval-zookeeper`
- `codeval-kafka`
- `codeval-kafka-ui`
- `codeval-execution-engine`
- `codeval-sandbox-*` (N containers, as per `SANDBOX_POOL_MIN` in `.env`)

---

## Quick One-Liner (builds everything from scratch)
```powershell
./mvnw clean package -DskipTests ; docker build -t codeval/sandbox-wrapper:latest -f sandbox-wrapper/Dockerfile . ; docker compose up --build -d
```

---

## Restart Only Execution Engine + Sandboxes (infra already running)
```powershell
# Stop execution engine (sandboxes are auto-managed by it)
docker compose stop execution-engine
docker compose rm -f execution-engine

# Rebuild and restart
docker compose up --build execution-engine -d
```

---

## Stop Everything
```powershell
docker compose down
```

## Stop Only Execution Engine (keep infra)
```powershell
docker compose stop execution-engine
```

---

## View Logs
```powershell
# Execution engine logs
docker compose logs -f execution-engine

# All services
docker compose logs -f

# Sandbox container logs (replace NAME with actual container name)
docker logs -f codeval-sandbox-<NAME>
```

---

## Test the Flow

### Generate a JWT token
```powershell
Invoke-RestMethod "http://localhost:8080/api/dev/token?userId=1"
```
Copy the `token` value from the response. (Available only when `SPRING_PROFILES_ACTIVE=dev`, which is the default in `.env`.)

### Health check
```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

### Submit code via REST
```powershell
$token = "<paste-your-token-here>"
$body = @{
    problemId  = 1
    language   = "java"
    mode       = "competitive"
    sourceCode = 'public class Solution { public static void main(String[] args) { System.out.println("Hello"); } }'
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/executions" `
    -Method Post `
    -Headers @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" } `
    -Body $body
```

### Full E2E Test Script
```powershell
./test-e2e.ps1
```

### WebSocket Test UI
Open `test-client.html` in a browser, paste your JWT token, and submit code.

---

## Useful URLs
| Service         | URL                          |
|-----------------|------------------------------|
| Execution API   | http://localhost:8080        |
| Health Check    | http://localhost:8080/actuator/health |
| WebSocket       | ws://localhost:8080/ws       |
| Kafka UI        | http://localhost:8090        |
| Test Page (UI)  | Open `test-client.html` in browser (file:///.../execution/test-client.html) |

---

## Configuration
All tunable parameters live in `.env` at the project root:

| Variable              | Description                                      | Default |
|-----------------------|--------------------------------------------------|---------|
| `SANDBOX_POOL_MIN`    | Minimum warm sandbox containers kept alive       | 2       |
| `SANDBOX_POOL_MAX`    | Maximum sandbox containers allowed              | 10      |
| `SANDBOX_IMAGE`       | Docker image used for sandbox containers         | `codeval/sandbox-wrapper:latest` |
| `SERVER_PORT`         | Host port for the execution engine               | 8080    |
| `DB_PORT`             | Host port for Postgres                           | 5432    |
| `REDIS_PORT`          | Host port for Redis                              | 6379    |
| `KAFKA_UI_PORT`       | Host port for Kafka UI                           | 8090    |
| `JWT_SECRET`          | HS256 secret for JWT validation                  | —       |

> **No rebuild needed** for `.env` changes that are environment variables read at runtime.  
> **Rebuild required** only if you change `application.yml` values that are baked into the jar (rare).

---

## Troubleshooting

### WebSocket keeps disconnecting
- Ensure the execution engine container is fully started: `docker compose logs execution-engine`
- Check JWT token is valid and not expired
- Confirm port 8080 is not blocked by another process

### Sandboxes not spawning
- Confirm Docker socket is mounted: `docker compose config` → check `/var/run/docker.sock`
- Check execution engine logs for pool initialization errors
- Ensure `codeval/sandbox-wrapper:latest` image exists: `docker images | grep sandbox`

### Kafka consumers shutting down
- This is normal on application shutdown; they restart with the engine
- If they crash on startup, wait for Kafka to be fully healthy before starting the engine

### Code not reaching sandbox
- Check Kafka topic `code-execution-requests` has messages: http://localhost:8090
- Check execution engine logs for orchestrator/pool errors

