# Run from repository root with provider credentials in .env
# Requires: VARKA_RUN_LIVE_ASSISTANT_EVAL=true, OPENAI_COMPATIBLE_API_KEY, OPENAI_COMPATIBLE_BASE_URL

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not (Test-Path ".env")) {
    Write-Host "Copy .env.example to .env and set provider credentials first." -ForegroundColor Yellow
}

$env:VARKA_RUN_LIVE_ASSISTANT_EVAL = "true"
$env:VARKA_WRITE_EVAL_REPORT = "true"

Write-Host "Running live assistant eval matrix with quality gate assertions..."
mvn -q -pl packages/java/platform-assistant test `
    -DVARKA_RUN_LIVE_ASSISTANT_EVAL=true `
    -DVARKA_WRITE_EVAL_REPORT=true `
    -Dtest=AssistantLiveEvalTest#liveGatePromptsPassQualityGatesWithinTurnBudget

Write-Host "Gate report: docs/internal/ai/live-eval-gate-report.md"
