#!/usr/bin/env pwsh
# ============================================================
# reset_metrics.ps1
# Resets all metrics + dashboard data for a clean load test run
# Postgres and Redis data are preserved.
# Usage: .\reset_metrics.ps1
# ============================================================

Write-Host ""
Write-Host ">>> Resetting metrics for a clean test run..." -ForegroundColor Cyan
Write-Host ""

# 1. Flush Redis — clears rate-limit keys + execution status keys
Write-Host "  [1/4]  Flushing Redis (rate-limit + execution status keys)..."
docker exec codeval-redis redis-cli FLUSHDB | Out-Null
Write-Host "         OK  Redis flushed" -ForegroundColor Green

# 2. Restart execution-engine — resets all JVM / Micrometer counters to zero
Write-Host "  [2/4]  Restarting execution-engine (resets JVM metrics)..."
docker restart codeval-execution-engine | Out-Null

# Wait for app to be healthy
Write-Host "         Waiting for app to be ready..."
$attempts = 0
do {
    Start-Sleep -Seconds 3
    $attempts++
    try {
        $status = (Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 3).StatusCode
    } catch { $status = 0 }
} while ($status -ne 200 -and $attempts -lt 20)

if ($status -eq 200) {
    Write-Host "         OK  App is up" -ForegroundColor Green
} else {
    Write-Host "         WARN  App did not respond in time -- check docker logs" -ForegroundColor Yellow
}

# 3. Wipe Prometheus + Grafana volumes — full clean slate for observability
#    (Postgres and Redis volumes are NOT touched)
Write-Host "  [3/4]  Wiping Prometheus & Grafana data (clean observability slate)..."
docker stop codeval-grafana codeval-prometheus | Out-Null
docker rm   codeval-grafana codeval-prometheus | Out-Null

# Detect the compose project volume name prefix (default: folder name)
$projectName = (Get-Item $PSScriptRoot).Name.ToLower() -replace '[^a-z0-9]', ''
docker volume rm "${projectName}_prometheus_data" "${projectName}_grafana_data" 2>$null | Out-Null

# Recreate containers with fresh empty volumes
Push-Location $PSScriptRoot
docker compose up -d prometheus grafana | Out-Null
Pop-Location

# Wait for Prometheus to be ready
$attempts = 0
do {
    Start-Sleep -Seconds 2
    $attempts++
    try { $ps = (Invoke-WebRequest -Uri "http://localhost:9090/-/ready" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $ps = 0 }
} while ($ps -ne 200 -and $attempts -lt 15)

# Wait for Grafana to be ready
$attempts = 0
do {
    Start-Sleep -Seconds 2
    $attempts++
    try { $gs = (Invoke-WebRequest -Uri "http://localhost:3000/api/health" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $gs = 0 }
} while ($gs -ne 200 -and $attempts -lt 15)

if ($ps -eq 200 -and $gs -eq 200) {
    Write-Host "         OK  Prometheus and Grafana are up with clean data" -ForegroundColor Green
} else {
    Write-Host "         WARN  One or more services did not respond in time (Prometheus=$ps Grafana=$gs)" -ForegroundColor Yellow
}

# 4. Final confirmation
Write-Host "  [4/4]  Done." -ForegroundColor Green

Write-Host ""
Write-Host "All done! Everything is reset." -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dashboard : http://localhost:3000  (auto-refreshes every 1s, last 5 min window)"
Write-Host "  Prometheus: http://localhost:9090"
Write-Host "  Run test  : python load_test.py"
Write-Host ""
