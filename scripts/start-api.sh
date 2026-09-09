#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command java
require_command terraform

cd "$REPO_ROOT"
readonly API_JAR="order-api/target/order-api-1.0.0-SNAPSHOT.jar"
if [[ ! -f "$API_JAR" ]]; then
  echo "API JAR not found. Run: sh ./mvnw clean verify" >&2
  exit 1
fi

# Keep assignments separate from export so Terraform failures stop startup.
ORDERS_QUEUE_URL="$(terraform -chdir=infra output -raw orders_queue_url)" || {
  echo "Could not read the queue URL. Run bash scripts/provision-local.sh first." >&2
  exit 1
}
ORDERS_TABLE="$(terraform -chdir=infra output -raw orders_table_name)" || {
  echo "Could not read the table name. Run bash scripts/provision-local.sh first." >&2
  exit 1
}
if [[ -z "$ORDERS_QUEUE_URL" || -z "$ORDERS_TABLE" ]]; then
  echo "Terraform outputs are empty. Provision local infrastructure before starting the API." >&2
  exit 1
fi
export ORDERS_QUEUE_URL ORDERS_TABLE

mkdir -p target/java-tmp
socket_dir="$REPO_ROOT/target/java-tmp"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) socket_dir="$(cygpath -m "$socket_dir")" ;;
esac

echo "Starting order-api with the local profile. Stop with Ctrl+C."
exec java "-Djdk.net.unixdomain.tmpdir=$socket_dir" \
  -jar "$API_JAR" --spring.profiles.active=local
