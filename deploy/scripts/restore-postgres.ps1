# Restore a MODRISS PostgreSQL backup created by backup-postgres.sh
# Usage: .\deploy\scripts\restore-postgres.ps1 -BackupFile backups\modriss-YYYYMMDDTHHMMSSZ.dump

param(
    [Parameter(Mandatory = $true)]
    [string] $BackupFile,

    [string] $PostgresHost = $(if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }),
    [string] $PostgresPort = $(if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { "5432" }),
    [string] $PostgresDb = $(if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "modriss" }),
    [string] $PostgresUser = $(if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "modriss" }),
    [string] $PostgresPassword = $(if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } elseif ($env:MODRISS_DB_PASSWORD) { $env:MODRISS_DB_PASSWORD } else { "modriss" })
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
