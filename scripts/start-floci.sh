#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command podman
require_command curl

: "${XDG_RUNTIME_DIR:=/run/user/$(id -u)}"
export XDG_RUNTIME_DIR
export PODMAN_SOCKET_PATH="${PODMAN_SOCKET_PATH:-$XDG_RUNTIME_DIR/podman/podman.sock}"
podman network inspect floci-net >/dev/null 2>&1 || podman network create floci-net >/dev/null
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    # Avoid the external Compose provider's Windows named-pipe transport.
    # Prevent Git Bash from rewriting Linux socket paths as Windows paths.
    if podman container exists floci; then
      podman start floci >/dev/null
    else
      MSYS_NO_PATHCONV=1 podman run -d --name floci \
        --network floci-net -p 4566:4566 \
        -v "$PODMAN_SOCKET_PATH:/var/run/docker.sock:z" \
        -e FLOCI_HOSTNAME=floci \
        -e FLOCI_SERVICES_LAMBDA_DOCKER_NETWORK=floci-net \
        -e FLOCI_SERVICES_LAMBDA_DOCKER_HOST_OVERRIDE=floci \
        floci/floci:1.5.34
    fi
    ;;
  *) podman compose -f "$REPO_ROOT/compose.yaml" up -d ;;
esac

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
