#!/bin/sh
set -eu

# Docker Desktop and Linux hosts assign different socket groups. Join the mounted
# socket's actual group before dropping privileges; never change socket permissions.
if [ "$(id -u)" = "0" ]; then
  if [ -S /var/run/docker.sock ]; then
    socket_gid=$(stat -c '%g' /var/run/docker.sock)
    socket_group=$(getent group "$socket_gid" | cut -d: -f1)
    if [ -z "$socket_group" ]; then
      socket_group="modriss-docker-$socket_gid"
      groupadd --gid "$socket_gid" "$socket_group"
    fi
    usermod --append --groups "$socket_group" modriss
  fi
  exec gosu modriss "$@"
fi

exec "$@"
