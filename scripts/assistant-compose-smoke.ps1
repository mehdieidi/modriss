# Docker compose smoke for assistant stack readiness
# Run from repository root: ./scripts/assistant-compose-smoke.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "Starting docker compose stack..."
docker compose up --build -d

Write-Host "Waiting for backend readiness..."
$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { "8080" }
$deadline = (Get-Date).AddMinutes(5)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/actuator/health/readiness" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200 -and $response.Content -match "UP") {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 5
    }
}

if (-not $ready) {
    Write-Error "Backend did not become ready within 5 minutes."
}

Write-Host "Running compose smoke test..."
$env:VARKA_RUN_COMPOSE_SMOKE = "true"
$env:VARKA_BACKEND_URL = "http://127.0.0.1:$backendPort"
Push-Location packages/java/platform-assistant
try {
    mvn -q test `
        "-DVARKA_RUN_COMPOSE_SMOKE=true" `
        "-Dtest=AssistantComposeSmokeTest"
} finally {
    Pop-Location
}

Write-Host "Compose smoke passed. Manual exploratory scenarios remain in .idea/ai-modeling-agent-redesign-plan.md."
