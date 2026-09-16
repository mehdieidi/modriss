#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

command -v docker >/dev/null 2>&1 || {
  echo "docker is required" >&2
  exit 1
}

case "${1:-}" in
  "")
    docker compose down
    ;;
  -v|--volumes)
    echo "Removing the development stack and its persistent volumes."
    docker compose down --volumes
    ;;
  *)
    echo "Usage: $0 [--volumes]" >&2
    exit 2
    ;;
esac
