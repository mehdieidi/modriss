#!/usr/bin/env bash
# Backup the Varka PostgreSQL database to a timestamped custom-format dump.
# Usage: ./deploy/scripts/backup-postgres.sh [output-directory]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${1:-${ROOT}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

POSTGRES_DB="${POSTGRES_DB:-varka}"
POSTGRES_USER="${POSTGRES_USER:-varka}"
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
export PGPASSWORD="${POSTGRES_PASSWORD:-${VARKA_DB_PASSWORD:-varka}}"

mkdir -p "${OUTPUT_DIR}"
OUTPUT_FILE="${OUTPUT_DIR}/varka-${TIMESTAMP}.dump"

echo "Backing up ${POSTGRES_DB}@${POSTGRES_HOST}:${POSTGRES_PORT} -> ${OUTPUT_FILE}"
pg_dump \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --format=custom \
  --verbose \
  --file="${OUTPUT_FILE}" \
  "${POSTGRES_DB}"

echo "Backup complete: ${OUTPUT_FILE}"
echo "Verify with: pg_restore --list ${OUTPUT_FILE}"
