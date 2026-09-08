#!/usr/bin/env bash
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
readonly AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"

if [[ "$AWS_ENDPOINT_URL" != "http://localhost:4566" && "$AWS_ENDPOINT_URL" != "http://127.0.0.1:4566" ]]; then
  echo "Refusing to run: AWS_ENDPOINT_URL must point to loopback Floci on port 4566." >&2
  exit 2
fi

export AWS_ENDPOINT_URL AWS_DEFAULT_REGION
export AWS_REGION="$AWS_DEFAULT_REGION"
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 127; }
}

aws_local() {
  aws --endpoint-url "$AWS_ENDPOINT_URL" --region "$AWS_DEFAULT_REGION" "$@"
}
