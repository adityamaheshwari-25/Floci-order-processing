#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command terraform

cd "$REPO_ROOT"
./mvnw -pl order-processor -am package -DskipTests

terraform -chdir="$REPO_ROOT/infra" init
terraform -chdir="$REPO_ROOT/infra" fmt -check -recursive
terraform -chdir="$REPO_ROOT/infra" validate
terraform -chdir="$REPO_ROOT/infra" plan -var='local_mode=true' -var="aws_endpoint_url=$AWS_ENDPOINT_URL" -out=floci.tfplan
terraform -chdir="$REPO_ROOT/infra" apply -auto-approve floci.tfplan
