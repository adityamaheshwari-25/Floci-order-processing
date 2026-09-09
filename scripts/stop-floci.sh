#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command podman
: "${XDG_RUNTIME_DIR:=/run/user/$(id -u)}"
export XDG_RUNTIME_DIR
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    if podman container exists floci; then
      podman stop floci
      podman rm floci
    fi
    ;;
  *) podman compose -f "$REPO_ROOT/compose.yaml" down ;;
esac

if podman network exists floci-net && [[ -z "$(podman network inspect floci-net --format '{{range .Containers}}{{.Name}}{{end}}')" ]]; then
  podman network rm floci-net >/dev/null

fi
