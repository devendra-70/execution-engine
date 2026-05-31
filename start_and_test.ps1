#!/usr/bin/env pwsh
# start_and_test.ps1 — Full startup + load test runner
# Run this in a NEW PowerShell window:
#   cd "C:\Users\DevendraKishorMahaja\Downloads\execution-engine-service"
#   .\start_and_test.ps1

Set-Location "C:\Users\DevendraKishorMahaja\Downloads\execution-engine-service"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 1: Build JAR with semaphore fix"
Write-Host "========================================" -ForegroundColor Cyan

Set-Location "execution-engine-service"
.\mvnw.cmd "-DskipTests" "-Dmaven.test.skip=true" package -q
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }
Write-Host "  JAR built OK" -ForegroundColor Green
Set-Location ".."

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 2: Rebuild Docker image"
Write-Host "========================================" -ForegroundColor Cyan

docker-compose build --no-cache execution-engine
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }
Write-Host "  Image built OK" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 3: Start full stack"
Write-Host "========================================" -ForegroundColor Cyan

docker-compose up -d
Write-Host "  Stack started" -ForegroundColor Green

Write-Host ""
Write-Host "  Waiting 30s for services to be healthy..."
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 4: Verify 10 sandbox containers"
Write-Host "========================================" -ForegroundColor Cyan

docker logs codeval-execution-engine 2>&1 | Select-String "Pool"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 5: Seed test cases"
Write-Host "========================================" -ForegroundColor Cyan

docker exec -i codeval-postgres psql -U codeval -d codeval -c "\dt" 2>&1 | Select-String "test_cases"
$hasTestCases = docker exec codeval-postgres psql -U codeval -d codeval -t -c "SELECT COUNT(*) FROM test_cases;" 2>&1
Write-Host "  Test cases in DB: $hasTestCases"

if ($hasTestCases -match "^\s*0\s*$") {
    Write-Host "  Seeding test cases..." -ForegroundColor Yellow
    Get-Content "seed_testcases.sql" | docker exec -i codeval-postgres psql -U codeval -d codeval
    Write-Host "  Seeded OK" -ForegroundColor Green
} else {
    Write-Host "  Test cases already present" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 6: Verify app health"
Write-Host "========================================" -ForegroundColor Cyan

$health = (Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5).Content
Write-Host "  Health: $health"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " STEP 7: Reset metrics for clean run"
Write-Host "========================================" -ForegroundColor Cyan

docker exec codeval-redis redis-cli FLUSHDB | Out-Null
Write-Host "  Redis flushed" -ForegroundColor Green
docker restart codeval-grafana | Out-Null
Write-Host "  Grafana restarted" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " ALL READY - Open dashboard then run:"
Write-Host "   http://localhost:3000  (Grafana)"
Write-Host "   http://localhost:8090  (Kafka UI)"
Write-Host ""
Write-Host "   python load_test.py"
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

