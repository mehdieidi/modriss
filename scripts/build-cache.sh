#!/usr/bin/env sh
set -eu

case "${1:---inspect}" in
  --inspect)
    docker buildx du
    ;;
  --prune)
    age=${2:-168h}
    echo "Removing unused build-cache records not used for $age from the selected builder."
    echo "Docker will ask for confirmation. Images, containers, and volumes are preserved."
    docker buildx prune --filter "until=$age"
    ;;
  *)
    echo "Usage: sh scripts/build-cache.sh [--inspect | --prune [age, default 168h]]" >&2
    exit 1
    ;;
esac
