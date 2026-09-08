#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command podman

: "${XDG_RUNTIME_DIR:=/run/user/$(id -u)}"
export XDG_RUNTIME_DIR
export PODMAN_SOCKET_PATH="${PODMAN_SOCKET_PATH:-$XDG_RUNTIME_DIR/podman/podman.sock}"
podman network inspect floci-net >/dev/null 2>&1 || podman network create floci-net >/dev/null
podman compose -f "$REPO_ROOT/compose.yaml" up -d

for _ in {1..30}; do
  if curl -fsS "$AWS_ENDPOINT_URL/_localstack/health" >/dev/null 2>&1 || curl -fsS "$AWS_ENDPOINT_URL" >/dev/null 2>&1; then
    echo "Floci is ready at $AWS_ENDPOINT_URL"
    exit 0
  fi
  sleep 1
done
echo "Floci did not become ready within 30 seconds." >&2
podman logs floci >&2
exit 1
