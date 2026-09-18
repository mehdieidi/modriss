#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

for command_name in docker git curl; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "$command_name is required" >&2
    exit 1
  }
done

if [ ! -f .env ]; then
  echo "Production deployment requires $REPO_ROOT/.env. Copy .env.example and fill in production values." >&2
  exit 1
fi

if [ -n "${DEPLOY_ALLOWED_BRANCH:-}" ]; then
  current_branch=$(git symbolic-ref --quiet --short HEAD || true)
  if [ "$current_branch" != "$DEPLOY_ALLOWED_BRANCH" ]; then
    echo "Refusing deployment from branch '$current_branch'; expected '$DEPLOY_ALLOWED_BRANCH'." >&2
    exit 1
  fi
fi

echo "Updating the checkout with fast-forward-only Git pull..."
git pull --ff-only

compose_args=(--env-file "$REPO_ROOT/.env" -f deploy/compose.base.yaml -f deploy/compose.prod.yaml)

echo "Validating the production Compose configuration..."
docker compose "${compose_args[@]}" config -q

if docker compose "${compose_args[@]}" config | grep -Eq '(POSTGRES_PASSWORD|GF_SECURITY_ADMIN_PASSWORD): (modriss|admin|change-me|replace-me)$'; then
  echo "Refusing production deployment: replace the development database/Grafana password in .env." >&2
  exit 1
fi

if docker compose "${compose_args[@]}" config | grep -Eq '(MODRISS_FRONTEND_BACKEND_BASE_URL|MODRISS_ADMIN_BACKEND_BASE_URL|MODRISS_ALLOWED_ORIGINS|VITE_BACKEND_BASE_URL|VITE_APP_URL):.*(localhost|127\.0\.0\.1)'; then
  echo "Refusing production deployment: rendered browser configuration contains localhost or 127.0.0.1." >&2
  echo "Set the production URL and CORS values in .env." >&2
  exit 1
fi

echo "Building and starting the production stack..."
docker compose "${compose_args[@]}" up -d --build --remove-orphans

# The editor is a static Python server. Its startup command copies the bind-mounted
# checkout into /srv, so an ordinary `up` does not refresh an already-running
# frontend container after git pull. Recreate it explicitly, together with Caddy
# so changes to the production edge configuration are loaded as well.
echo "Refreshing the static editor frontend and production edge configuration..."
docker compose "${compose_args[@]}" up -d --no-deps --force-recreate frontend caddy

echo "Production services:"
docker compose "${compose_args[@]}" ps

check_url() {
  url=$1
  attempts=${2:-30}
  attempt=1
  while [ "$attempt" -le "$attempts" ]; do
    if curl --fail --silent --show-error --location --max-time 15 "$url" >/dev/null; then
      echo "OK  $url"
      return 0
    fi
    echo "Waiting for $url (attempt $attempt/$attempts)..." >&2
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "Health check failed: $url" >&2
  return 1
}

check_url "https://modriss.site"
check_url "https://editor.modriss.site"
check_url "https://admin.modriss.site"
check_url "https://api.modriss.site/actuator/health/readiness"

echo "Checking editor cache policy..."
editor_headers=$(curl --fail --silent --show-error --location --max-time 15 \
  --dump-header - --output /dev/null "https://editor.modriss.site/")
if ! printf '%s\n' "$editor_headers" | grep -Eiq '^Cache-Control:.*no-store'; then
  echo "Editor cache policy check failed: Cache-Control does not include no-store." >&2
  exit 1
fi
echo "OK  editor cache policy (no-store)"

echo "Production deployment completed successfully."
