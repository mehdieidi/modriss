#!/usr/bin/env bash
set -euo pipefail

action="${1:-start}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$repository_root/.env" ]]; then
  configured_provider="$(sed -n 's/^AWS_EMULATOR=//p' "$repository_root/.env" | tail -n 1)"
fi
provider="${AWS_EMULATOR:-${configured_provider:-floci}}"

case "$provider" in
  floci|localstack) ;;
  *) echo "AWS_EMULATOR must be 'floci' or 'localstack'; found '$provider'." >&2; exit 2 ;;
esac

cd "$repository_root"
case "$action" in
  start)
    if [[ "$provider" == "floci" ]]; then other_provider="localstack"; else other_provider="floci"; fi
    docker compose --profile "$other_provider" stop "$other_provider"
    docker compose --profile "$provider" up -d --wait --no-deps --force-recreate "$provider"
    ;;
  stop) docker compose --profile "$provider" stop "$provider" ;;
  status) docker compose --profile "$provider" ps "$provider" ;;
  logs) docker compose --profile "$provider" logs --tail=200 -f "$provider" ;;
  *) echo "Usage: scripts/aws-emulator.sh {start|stop|status|logs}" >&2; exit 2 ;;
esac
