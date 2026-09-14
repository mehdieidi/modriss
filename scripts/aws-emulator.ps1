[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status', 'logs')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repositoryRoot '.env'
$settings = @{}

if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') {
            $settings[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
}

$provider = if ($env:AWS_EMULATOR) { $env:AWS_EMULATOR } elseif ($settings.AWS_EMULATOR) { $settings.AWS_EMULATOR } else { 'floci' }
if ($provider -notin @('floci', 'localstack')) {
    throw "AWS_EMULATOR must be 'floci' or 'localstack'; found '$provider'."
}

Push-Location $repositoryRoot
try {
    switch ($Action) {
        'start' {
            $otherProvider = if ($provider -eq 'floci') { 'localstack' } else { 'floci' }
            docker compose --profile $otherProvider stop $otherProvider
            if ($LASTEXITCODE -ne 0) {
                throw "Could not stop the previously selected AWS emulator."
            }
            docker compose --profile $provider up -d --wait --no-deps --force-recreate $provider
        }
        'stop' {
            docker compose --profile $provider stop $provider
        }
        'status' {
            docker compose --profile $provider ps $provider
        }
        'logs' {
            docker compose --profile $provider logs --tail=200 -f $provider
        }
    }
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
