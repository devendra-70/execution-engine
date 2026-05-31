#!/usr/bin/env pwsh
# ============================================================
# reset_metrics.ps1
# Resets all metrics + dashboard data for a clean load test run
# Usage: .\reset_metrics.ps1
# ============================================================

Write-Host ""
Write-Host ">>> Resetting metrics for a clean test run..." -ForegroundColor Cyan
Write-Host ""

# 1. Flush Redis — clears rate-limit keys + execution status keys
Write-Host "  [1/3]  Flushing Redis (rate-limit + execution status keys)..."
docker exec codeval-redis redis-cli FLUSHDB | Out-Null
Write-Host "         OK  Redis flushed" -ForegroundColor Green

# 2. Restart execution-engine — resets all JVM / Micrometer counters to zero
Write-Host "  [2/3]  Restarting execution-engine (resets JVM metrics)..."
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

# 3. Restart Grafana — forces dashboard to reload from time "now" so old data is not shown
Write-Host "  [3/3]  Restarting Grafana (clears cached panel data)..."
docker restart codeval-grafana | Out-Null
Start-Sleep -Seconds 3
Write-Host "         OK  Grafana restarted" -ForegroundColor Green

Write-Host ""
Write-Host "All done! Everything is reset." -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dashboard : http://localhost:3000  (auto-refreshes every 1s, last 5 min window)"
Write-Host "  Run test  : python load_test.py"
Write-Host ""
