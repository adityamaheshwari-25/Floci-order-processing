#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command podman
: "${XDG_RUNTIME_DIR:=/run/user/$(id -u)}"
export XDG_RUNTIME_DIR
podman compose -f "$REPO_ROOT/compose.yaml" down

if podman network exists floci-net && [[ -z "$(podman network inspect floci-net --format '{{range .Containers}}{{.Name}}{{end}}')" ]]; then
  podman network rm floci-net >/dev/null
fi
