#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

command -v docker >/dev/null 2>&1 || {
  echo "docker is required" >&2
  exit 1
}

echo "Validating the development Compose configuration..."
docker compose config -q
echo "Starting the MODRISS development stack..."
docker compose up -d --build --remove-orphans

cat <<'EOF'

Development URLs:
  Landing:    http://localhost:8088
  Editor:     http://editor.localhost:8088
  Admin:      http://admin.localhost:8088
  API:        http://api.localhost:8088
  Grafana:    http://grafana.localhost:8088
  Prometheus: http://prometheus.localhost:8088
  Logs:       http://logs.localhost:8088
EOF
