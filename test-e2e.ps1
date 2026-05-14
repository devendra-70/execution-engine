# E2E Test Script for CodEval Execution Engine
# Tests the full flow: JWT -> REST submission -> Kafka -> Orchestration -> Sandbox -> WebSocket

Write-Host "=== CodEval Execution Engine E2E Test ===" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Step 1: Generate JWT Token" -ForegroundColor Yellow

$tokenOutput = & mvn -pl execution-engine-app "exec:java" "-Dexec.mainClass=org.codeval.execution.util.TestTokenGenerator" 2>&1

$token = ($tokenOutput | Where-Object { $_ -match "^eyJ" } | Select-Object -First 1)

if ($token) {
    Write-Host "[OK] Token generated: $token" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Failed to generate token. Output was:" -ForegroundColor Red
    $tokenOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    exit 1
}

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Step 2: Health check - GET /actuator/health" -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
    Write-Host "[OK] Health: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Health check failed: $_" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Step 3: Submit execution - POST /api/executions" -ForegroundColor Yellow

$execRequest = @{
    problemId  = 1
    language   = "java"
    mode       = "competitive"
    sourceCode = 'public class Solution { public static void main(String[] args) { System.out.println("Hello"); } }'
} | ConvertTo-Json

try {
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type"  = "application/json"
    }
    $execResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/executions" `
        -Method Post -Headers $headers -Body $execRequest
    $executionId = $execResponse.executionId

    Write-Host "[OK] Submission accepted" -ForegroundColor Green
    Write-Host "     ExecutionId: $executionId" -ForegroundColor Gray
} catch {
    Write-Host "[FAIL] REST API submission failed: $_" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Step 4: Poll for status - 10 attempts" -ForegroundColor Yellow
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    $step = $i + 1
    try {
        $statusHeaders = @{ "Authorization" = "Bearer $token" }
        $statusResponse = Invoke-RestMethod `
            -Uri "http://localhost:8080/api/executions/$executionId/status" `
            -Method Get -Headers $statusHeaders
        $currentStatus = $statusResponse.status
        Write-Host ("  [{0}/10] Status: {1}" -f $step, $currentStatus) -ForegroundColor Gray
        if ($currentStatus -notin @("PENDING", "RUNNING")) {
            Write-Host "  Final status reached: $currentStatus" -ForegroundColor Cyan
            break
        }
    } catch {
        Write-Host ("  [{0}/10] Could not fetch status: {1}" -f $step, $_) -ForegroundColor DarkGray
    }
}

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "[DONE] E2E Test Complete!" -ForegroundColor Green
Write-Host "  Kafka UI  -> http://localhost:8090" -ForegroundColor Cyan
Write-Host "  WebSocket -> open test-client.html in browser and paste the token above" -ForegroundColor Cyan
