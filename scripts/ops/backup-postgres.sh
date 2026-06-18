#!/usr/bin/env bash
# Backup the Modless PostgreSQL database to a timestamped custom-format dump.
# Usage: ./scripts/ops/backup-postgres.sh [output-directory]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${1:-${ROOT}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

POSTGRES_DB="${POSTGRES_DB:-modless}"
POSTGRES_USER="${POSTGRES_USER:-modless}"
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
export PGPASSWORD="${POSTGRES_PASSWORD:-${MODLESS_DB_PASSWORD:-modless}}"

mkdir -p "${OUTPUT_DIR}"
OUTPUT_FILE="${OUTPUT_DIR}/modless-${TIMESTAMP}.dump"

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
