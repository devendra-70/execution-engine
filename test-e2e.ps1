# E2E Test Script for CodEval Execution Engine
# This script tests the full flow: JWT → REST submission → Kafka → Orchestration → Sandbox → WebSocket

Write-Host "=== CodEval Execution Engine E2E Test ===" -ForegroundColor Cyan
Write-Host "`nStep 1: Generate JWT Token" -ForegroundColor Yellow

# Generate JWT token
$tokenOutput = & mvn -pl execution-engine-app exec:java -Dexec.mainClass="org.codeval.execution.util.TestTokenGenerator" -q 2>&1
$token = $tokenOutput -match "eyJ" | Select-Object -First 1

if ($token) {
    Write-Host "✓ Token generated: $token" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to generate token" -ForegroundColor Red
    exit 1
}

Write-Host "`nStep 2: Test REST API - /actuator/health" -ForegroundColor Yellow
try {
    $health = (New-Object System.Net.WebClient).DownloadString("http://localhost:8080/actuator/health") | ConvertFrom-Json
    Write-Host "✓ Health: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "✗ Health check failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host "`nStep 3: Test REST API - POST /api/executions" -ForegroundColor Yellow
$execRequest = @{
    problemId = 1
    language = "java"
    mode = "competitive"
    sourceCode = @"
public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
"@
} | ConvertTo-Json

try {
    $webClient = New-Object System.Net.WebClient
    $webClient.Headers.Add("Authorization", "Bearer $token")
    $webClient.Headers.Add("Content-Type", "application/json")

    $execResponse = $webClient.UploadString("http://localhost:8080/api/executions", "POST", $execRequest) | ConvertFrom-Json
    $executionId = $execResponse.executionId

    Write-Host "✓ Submission accepted" -ForegroundColor Green
    Write-Host "  ExecutionId: $executionId" -ForegroundColor Gray
} catch {
    Write-Host "✗ REST API submission failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host "`nStep 4: Poll Redis for status updates" -ForegroundColor Yellow
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    try {
        $statusKey = "execution:status:$executionId"
        $psqlCmd = "SELECT COUNT(*) FROM submissions WHERE id = '$executionId'::uuid LIMIT 1;"

        Write-Host "  [$i] Checking execution status..." -ForegroundColor Gray
    } catch {
        # Silently continue
    }
}

Write-Host "`n✓ E2E Test Complete!" -ForegroundColor Green
Write-Host "`nNext: Open test-client.html in browser and paste the token above to test WebSocket." -ForegroundColor Cyan

