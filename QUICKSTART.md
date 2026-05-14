# Quick Start Guide - CodEval Execution Engine

## Current Status
✅ All services running and healthy

## Test the System (5 minutes)

### Option 1: Browser-Based Test (Recommended)

#### Step 1: Generate JWT Token
Open PowerShell in the execution directory and run:
```powershell
cd C:\Users\DevendraKishorMahaja\Downloads\execution
mvn -pl execution-engine-app exec:java -Dexec.mainClass="org.codeval.execution.util.TestTokenGenerator"
```

**Output example:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcklkIjoxLCJpYXQiOjE2MzE2MzAwMDB9.XXXXX
```

Copy this token (everything starting with `eyJ...`).

#### Step 2: Open Test Client
1. Open file: `C:\Users\DevendraKishorMahaja\Downloads\execution\test-client.html`
2. Paste the JWT token into the "JWT Token" field
3. Click **"Connect WebSocket"** (should turn green)

#### Step 3: Submit Code
Paste this sample code into the "Source Code" field:
```java
public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
```

4. Click **"Submit Execution"**
5. Watch messages appear in real-time showing:
   - Execution PENDING → PROCESSING → COMPLETED
   - Test case results with verdicts

---

### Option 2: REST API Test (curl/PowerShell)

#### Step 1: Get Token
```powershell
$token = "eyJ...XXXX"  # From step above
```

#### Step 2: Submit Code
```powershell
$body = @{
    problemId = 1
    language = "java"
    mode = "competitive"
    sourceCode = 'public class Solution { public static void main(String[] args) { System.out.println("Test"); } }'
} | ConvertTo-Json

$response = Invoke-WebRequest `
    -Uri "http://localhost:8080/api/executions" `
    -Method POST `
    -Headers @{"Authorization" = "Bearer $token"; "Content-Type" = "application/json"} `
    -Body $body

# Extract executionId
$executionId = ($response.Content | ConvertFrom-Json).executionId
Write-Host "Execution ID: $executionId"
```

#### Step 3: Check Status (REST Fallback)
```powershell
Invoke-WebRequest `
    -Uri "http://localhost:8080/api/executions/$executionId/status" `
    -Headers @{"Authorization" = "Bearer $token"}
```

---

## System Components Status

```powershell
docker ps
```

Expected output:
```
CONTAINER ID   IMAGE                        STATUS
xxxxx          execution-execution-engine   Up (healthy) port 8080
xxxxx          postgres:16-alpine           Up (healthy) port 5432
xxxxx          redis:7-alpine               Up (healthy) port 6379
xxxxx          confluentinc/cp-kafka        Up (healthy) port 9092
xxxxx          codeval/sandbox-wrapper      Up            port 5000
```

---

## Troubleshooting

### Issue: WebSocket Connection Failed
**Solution**: Check JWT token validity. Regenerate a new token and try again.

### Issue: 403 Unauthorized
**Solution**: Token is invalid or expired. Get a fresh token.

### Issue: 429 Too Many Requests
**Solution**: Rate limit exceeded (5 requests/minute). Wait 60 seconds.

### Issue: Sandbox Container Not Running
**Solution**: Run `docker ps` to verify. If missing, restart docker-compose:
```powershell
docker-compose down
docker-compose up -d
```

### Issue: Database Connection Failed
**Solution**: Verify PostgreSQL is running:
```powershell
docker logs codeval-postgres
```

---

## Key Endpoints

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| POST | `/api/executions` | Bearer JWT | Submit code for execution |
| GET | `/api/executions/{id}/status` | Bearer JWT | Check execution status |
| WS | `/ws` | Bearer JWT | WebSocket for real-time updates |
| GET | `/actuator/health` | None | Health check |

---

## Architecture Highlights

✅ **Modular Monolith** - Single Spring Boot app with clean package separation  
✅ **Async Processing** - Kafka-based queue + thread pool orchestration  
✅ **Container Pool** - Pre-warmed Docker sandbox containers  
✅ **State Isolation** - Fresh ClassLoader per test case prevents state bleed  
✅ **Real-time Results** - WebSocket STOMP messaging  
✅ **Configurable** - All parameters via environment variables  
✅ **Production Ready** - ECS task definitions included  

---

## Configuration (Optional)

Edit `docker-compose.yml` environment variables:

```yaml
environment:
  APP_EXECUTION_POOL_WARM_MIN_SIZE: 5          # Pre-warmed containers
  APP_EXECUTION_TIMEOUT_MS: 3000                # Max execution time
  APP_EXECUTION_ORCHESTRATION_THREADS: 50       # Pool B size
  APP_KAFKA_CONCURRENCY: 25                     # Pool A size
  SANDBOX_HOST: host.docker.internal            # Sandbox connection host
```

Then restart:
```powershell
docker-compose down
docker-compose up -d
```

---

## Next: Production Deployment

1. Push Docker images to AWS ECR
2. Update ECS task definition with ECR image URIs
3. Deploy via AWS CloudFormation or Terraform (see `/deploy/` folder)
4. Configure RDS PostgreSQL, ElastiCache Redis, MSK Kafka
5. Enable ECS auto-scaling based on CPU + Kafka lag

---

**Questions?** Check `STATUS-REPORT.md` or `SRS.md` for full documentation.

