# Restore a Modless PostgreSQL backup created by backup-postgres.sh
# Usage: .\deploy\scripts\restore-postgres.ps1 -BackupFile backups\modless-YYYYMMDDTHHMMSSZ.dump

param(
    [Parameter(Mandatory = $true)]
    [string] $BackupFile,

    [string] $PostgresHost = $(if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }),
    [string] $PostgresPort = $(if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { "5432" }),
    [string] $PostgresDb = $(if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "modless" }),
    [string] $PostgresUser = $(if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "modless" }),
    [string] $PostgresPassword = $(if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } elseif ($env:MODLESS_DB_PASSWORD) { $env:MODLESS_DB_PASSWORD } else { "modless" })
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

$env:PGPASSWORD = $PostgresPassword

Write-Host "Restoring $BackupFile into ${PostgresDb}@${PostgresHost}:${PostgresPort}"
pg_restore `
    --host=$PostgresHost `
    --port=$PostgresPort `
    --username=$PostgresUser `
    --dbname=$PostgresDb `
    --clean `
    --if-exists `
    --no-owner `
    --verbose `
    $BackupFile

Write-Host "Restore complete."
