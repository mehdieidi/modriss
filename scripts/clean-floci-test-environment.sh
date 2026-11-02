#!/usr/bin/env bash
set -euo pipefail

OWNER_LABEL='io.modriss.test-environment'
PROJECT_LABEL='io.modriss.project-id'

usage() {
  cat <<'EOF'
Usage:
  bash scripts/clean-floci-test-environment.sh --project-id <project-id> [--dry-run]
  bash scripts/clean-floci-test-environment.sh --all [--dry-run]

Removes only MODRISS-owned Floci test containers, child resources, networks, and data volumes.
The MODRISS project, artifacts, and execution history are preserved. Shared Compose resources
and reusable Docker images are not removed.

Options:
  --project-id ID  Clean one project's Floci test environment.
  --all            Clean all MODRISS Floci test environments (asks per project).
  --dry-run        Show the resources that would be removed without changing Docker.
  -h, --help       Show this help.
EOF
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

docker_list() {
  docker "$@"
}

project_id=''
all=false
dry_run=false
while (($#)); do
  case "$1" in
    --project-id)
      (($# >= 2)) || die '--project-id requires a value.'
      project_id="$2"
      shift 2
      ;;
    --all)
      all=true
      shift
      ;;
    --dry-run)
      dry_run=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  die 'Docker CLI was not found. Start Docker and try again.'
fi
docker info >/dev/null 2>&1 || die 'Docker is unavailable. Start Docker and try again.'

if [[ "$all" == true && -n "$project_id" ]]; then
  die 'Choose either --all or --project-id, not both.'
fi
if [[ "$all" != true && -z "$project_id" ]]; then
  usage >&2
  exit 2
fi

project_ids=()
add_project_id() {
  local candidate="$1"
  [[ -n "$candidate" ]] || return 0
  local existing
  for existing in "${project_ids[@]}"; do
    [[ "$existing" == "$candidate" ]] && return 0
  done
  project_ids+=("$candidate")
}

resource_project_id() {
  local type="$1" name="$2"
  if [[ "$type" == container ]]; then
    docker inspect --format "{{ index .Config.Labels \"$PROJECT_LABEL\" }}" "$name"
  else
    docker inspect --format "{{ index .Labels \"$PROJECT_LABEL\" }}" "$name"
  fi
}

if [[ "$all" == true ]]; then
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    add_project_id "$(resource_project_id container "$id")"
  done < <(docker ps -aq --filter "label=$OWNER_LABEL=true")
  while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    add_project_id "$(resource_project_id volume "$name")"
  done < <(docker volume ls -q --filter "label=$OWNER_LABEL=true")
  while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    add_project_id "$(resource_project_id network "$name")"
  done < <(docker network ls -q --filter "label=$OWNER_LABEL=true")
else
  add_project_id "$project_id"
fi

if ((${#project_ids[@]} == 0)); then
  printf 'No MODRISS Floci test environments found.\n'
  exit 0
fi

for id in "${project_ids[@]}"; do
  suffix="$(printf '%s' "$id" | sha256sum | cut -c1-24)"
  namespace="modriss-floci-$suffix"
  owner_filter="label=$OWNER_LABEL=true"
  project_filter="label=$PROJECT_LABEL=$id"
  child_filter="label=floci_namespace=$namespace"

  mapfile -t main_containers < <(docker ps -aq --filter "$owner_filter" --filter "$project_filter")
  mapfile -t child_containers < <(docker ps -aq --filter "$child_filter")
  mapfile -t project_volumes < <(docker volume ls -q --filter "$owner_filter" --filter "$project_filter")
  mapfile -t child_volumes < <(docker volume ls -q --filter "$child_filter")
  mapfile -t project_networks < <(docker network ls -q --filter "$owner_filter" --filter "$project_filter")
  mapfile -t child_networks < <(docker network ls -q --filter "$child_filter")

  for container in "${main_containers[@]}"; do
    image="$(docker inspect --format '{{.Config.Image}}' "$container")"
    name="$(docker inspect --format '{{.Name}}' "$container")"
    name="${name#/}"
    container_owner="$(docker inspect --format "{{ index .Config.Labels \"$OWNER_LABEL\" }}" "$container")"
    container_project="$(docker inspect --format "{{ index .Config.Labels \"$PROJECT_LABEL\" }}" "$container")"
    [[ "$image" == floci/floci:* && "$name" == "$namespace" && "$container_owner" == true && "$container_project" == "$id" ]] \
      || die "Refusing to remove an unexpected container associated with project $id: $name"
  done

  volumes=()
  for name in "${project_volumes[@]}" "${child_volumes[@]}"; do
    [[ -n "$name" ]] || continue
    seen=false
    for existing in "${volumes[@]}"; do [[ "$existing" == "$name" ]] && seen=true; done
    [[ "$seen" == true ]] || volumes+=("$name")
  done
  networks=()
  for name in "${project_networks[@]}" "${child_networks[@]}"; do
    [[ -n "$name" ]] || continue
    seen=false
    for existing in "${networks[@]}"; do [[ "$existing" == "$name" ]] && seen=true; done
    [[ "$seen" == true ]] || networks+=("$name")
  done

  resource_count=$((${#main_containers[@]} + ${#child_containers[@]} + ${#volumes[@]} + ${#networks[@]}))
  if ((resource_count == 0)); then
    printf 'No dedicated Floci resources found for project %s.\n' "$id"
    continue
  fi

  printf 'Project %s — %d Floci container(s), %d child container(s), %d volume(s), %d network(s).\n' \
    "$id" "${#main_containers[@]}" "${#child_containers[@]}" "${#volumes[@]}" "${#networks[@]}"
  if [[ "$dry_run" == true ]]; then
    printf 'Dry run: no Docker resources were changed.\n'
    continue
  fi

  read -r -p "Remove this project's Floci environment and data? [y/N] " answer
  if [[ ! "$answer" =~ ^([Yy]|[Yy][Ee][Ss])$ ]]; then
    printf 'Skipped project %s.\n' "$id"
    continue
  fi

  for container in "${child_containers[@]}"; do
    [[ -n "$container" ]] && docker rm -f -v "$container" >/dev/null
  done
  for container in "${main_containers[@]}"; do
    [[ -n "$container" ]] && docker rm -f -v "$container" >/dev/null
  done
  for volume in "${volumes[@]}"; do docker volume rm "$volume" >/dev/null; done
  for network in "${networks[@]}"; do docker network rm "$network" >/dev/null; done
  printf 'Removed Floci test resources for project %s. Project models, artifacts, and execution history were preserved.\n' "$id"
done
