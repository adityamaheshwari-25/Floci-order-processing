# Floci Order Processing Demo

A complete Spring Boot order-processing application demonstrating SQS, Lambda,
DynamoDB, S3, Terraform, Testcontainers, Podman, and Floci in one connected
local scenario. It never needs real AWS credentials.

## What the finished application demonstrates

1. `POST /api/v1/orders` validates an order, stores it as `RECEIVED` in
   DynamoDB, and publishes an `OrderPlaced` event to SQS.
2. An SQS-triggered Java Lambda changes the state to `PROCESSING`, creates a
   JSON receipt in S3, and marks the order `COMPLETED`.
3. `GET /api/v1/orders/{orderId}` retrieves the current DynamoDB state.
4. Failed messages are retried and eventually moved to an SQS dead-letter
   queue.
5. The processor is idempotent so duplicate SQS delivery does not create
   duplicate effects.

## Target stack

- Java 21
- Spring Boot 4.x
- Maven multi-module project and Maven Wrapper
- AWS SDK for Java v2
- Terraform AWS provider
- Floci AWS emulator
- Rootless Podman
- JUnit 5, Mockito, AssertJ and Floci Testcontainers
- GitHub Actions

Tested pins are Spring Boot 4.1.1, AWS SDK 2.46.8, Testcontainers 2.0.5,
`io.floci:testcontainers-floci` 2.13.0, Floci 1.5.34, Terraform 1.15.8, and the
AWS provider 6.57.1. CI and the local compose file use the same Floci tag.

## Expected repository layout after Codex builds it

```text
.
├── AGENTS.md
├── README.md
├── compose.yaml
├── pom.xml
├── mvnw
├── mvnw.cmd
├── shared-domain/
├── order-api/
├── order-processor/
├── infra/
├── scripts/
├── docs/
└── .github/workflows/ci.yml
```

## Prerequisites

Install:

- Java 21
- Podman 5+
- AWS CLI v2
- Terraform
- `curl` and `jq`

Confirm them:

```bash
java -version
podman --version
aws --version
terraform version
curl --version
jq --version
```

For rootless Podman, ensure the API socket is running:

```bash
systemctl --user enable --now podman.socket
podman info
```

## Local environment variables

Use fake credentials only for Floci:

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

Application defaults:

```bash
export ORDERS_TABLE=orders
export ORDERS_QUEUE_NAME=order-events
export ORDERS_DLQ_NAME=order-events-dlq
export RECEIPTS_BUCKET=order-receipts
```

## Start Floci with rootless Podman

The named network and socket are needed because Lambda runs in a container
created by Floci.

```bash
podman network inspect floci-net >/dev/null 2>&1 || \
  podman network create floci-net

podman run -d --replace --name floci \
  --network floci-net \
  -p 4566:4566 \
  -v /run/user/$(id -u)/podman/podman.sock:/var/run/docker.sock:z \
  -e FLOCI_SERVICES_LAMBDA_DOCKER_NETWORK=floci-net \
  -e FLOCI_HOSTNAME=floci \
  -e FLOCI_SERVICES_LAMBDA_DOCKER_HOST_OVERRIDE=floci \
  floci/floci:1.5.34
```

The image is deliberately pinned; change it only together with a full local
rehearsal and CI verification.

Check Floci:

```bash
podman ps --filter name=floci
podman logs floci
aws --endpoint-url http://localhost:4566 sts get-caller-identity
```

If SELinux socket access fails even with `:z`, try the documented fallback
`--security-opt label=disable` after confirming that this is acceptable under
your company security policy.

## Build the application

Start Floci before running the integration tests, then build all modules:

```bash
./mvnw clean verify
```

In Windows PowerShell, use `.\mvnw.cmd clean verify`.

On Windows Podman, the Testcontainers named-pipe adapter is not reliable with
the tested Podman 5.8.6 setup. Start the pinned Floci container first; the same
integration test then exercises S3, SQS, and DynamoDB at
`http://localhost:4566`. Linux and CI use `FlociContainer` directly. You can
select an already-running local emulator explicitly with
`FLOCI_EXTERNAL_ENDPOINT=http://localhost:4566`. This is a transport workaround,
not a skipped test; all client assertions still run and their resources are
deleted afterward.

If the active Podman connection is rootful, set
`PODMAN_SOCKET_PATH=/run/podman/podman.sock` before `start-floci.sh`. Rootless
Podman uses `/run/user/$(id -u)/podman/podman.sock` by default.

On Windows Git Bash, the start/stop scripts use Podman directly. This avoids
the external Docker Compose provider failing with `EOF` while connecting to
the Podman Windows named pipe. The startup script disables Git Bash path
conversion for the container socket mount and uses the same pinned Floci image,
network, and Lambda settings as `compose.yaml`. Other platforms use Compose.

Package the Lambda before applying Terraform:

```bash
./mvnw -pl order-processor -am clean package -DskipTests
```

## Provision resources in Floci

```bash
terraform -chdir=infra init
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra validate
terraform -chdir=infra plan \
  -var='aws_endpoint_url=http://localhost:4566' \
  -out=floci.tfplan
terraform -chdir=infra apply floci.tfplan
```

Terraform must be configured with local endpoints, fake credentials, and local
validation-friendly settings such as skipping real AWS account and credential
checks. Those settings must be enabled only for the local configuration.

Verify resources:

```bash
aws --endpoint-url http://localhost:4566 s3 ls
aws --endpoint-url http://localhost:4566 sqs list-queues
aws --endpoint-url http://localhost:4566 dynamodb list-tables
aws --endpoint-url http://localhost:4566 lambda list-functions
```

## Run the API

After building and provisioning, the simplest option is to open a separate Bash
terminal at the repository root and run:

```bash
bash scripts/start-api.sh
```

This sets fake credentials, reads the Terraform queue/table outputs, prepares
the Java socket directory, and starts the API with the `local` profile. Leave
it running while executing the demo in another terminal. Stop with Ctrl+C.
The equivalent manual commands follow.

After the full build and Terraform apply, run the packaged API from the
repository root. Set the queue URL from Terraform's output in the same terminal
that launches Java. A queue name alone is not sufficient.

In Bash:

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export ORDERS_QUEUE_URL="$(terraform -chdir=infra output -raw orders_queue_url)"
export ORDERS_TABLE="$(terraform -chdir=infra output -raw orders_table_name)"
java -jar order-api/target/order-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

In Windows PowerShell:

```powershell
$env:AWS_ENDPOINT_URL = 'http://localhost:4566'
$env:AWS_REGION = 'us-east-1'
$env:AWS_ACCESS_KEY_ID = 'test'
$env:AWS_SECRET_ACCESS_KEY = 'test'
$env:ORDERS_QUEUE_URL = terraform -chdir=infra output -raw orders_queue_url
if ($LASTEXITCODE -ne 0) { throw 'Apply Terraform successfully before starting the API.' }
$env:ORDERS_TABLE = terraform -chdir=infra output -raw orders_table_name
if ($LASTEXITCODE -ne 0) { throw 'Could not read the Terraform table output.' }
New-Item -ItemType Directory -Force target/java-tmp | Out-Null
$socketDir = (Resolve-Path target/java-tmp).Path
java "-Djdk.net.unixdomain.tmpdir=$socketDir" -jar order-api/target/order-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

The Windows socket directory avoids the `Unable to establish loopback connection`
error observed with Java 21.0.10 in this environment. It does not change the AWS
endpoint. If you provisioned a different region, set `AWS_REGION` to that region.
The API stays in the foreground; use a second terminal for demo requests.

Health check:

```bash
curl -fsS http://localhost:8080/actuator/health | jq
```

## Create an order

```bash
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: webinar-order-001' \
  -H 'X-Correlation-Id: webinar-demo-001' \
  -d '{
    "customerId": "customer-101",
    "items": [
      {"sku": "KEYBOARD-01", "quantity": 1, "unitPrice": 2499.00},
      {"sku": "MOUSE-02", "quantity": 2, "unitPrice": 799.00}
    ]
  }'
```

Save the returned `orderId`:

```bash
export ORDER_ID=<returned-order-id>
```

Poll its status:

```bash
curl -fsS "http://localhost:8080/api/v1/orders/${ORDER_ID}" | jq
```

Verify the receipt and database record:

```bash
aws --endpoint-url http://localhost:4566 \
  s3 ls "s3://order-receipts/receipts/"

aws --endpoint-url http://localhost:4566 \
  s3 cp "s3://order-receipts/receipts/${ORDER_ID}.json" - | jq

aws --endpoint-url http://localhost:4566 dynamodb get-item \
  --table-name orders \
  --key "{\"orderId\":{\"S\":\"${ORDER_ID}\"}}" | jq
```

## Test idempotency

Run the same POST command again with the same `Idempotency-Key`. The API must
return the original logical order instead of publishing another order.

For processor idempotency, redrive the same event payload to the queue. The
order must remain `COMPLETED`, and the receipt key must remain
`receipts/<orderId>.json`.

The API transaction writes the order and a dedicated
`IDEMPOTENCY#<key>` item atomically. Concurrent requests therefore have one
winner; a retry with the same canonical request hash returns that winner, while
reusing the key for different content returns `409 Conflict`. Publishing to SQS
is still a DynamoDB/SQS dual write. A production design should use a durable
outbox rather than treating this understandable demo sequence as atomic.

The processor conditionally claims `RECEIVED`, acknowledges an already
`COMPLETED` order, and always uses `receipts/<orderId>.json`. This combines a
DynamoDB state guard with a deterministic S3 key for at-least-once delivery.

## Inspect the dead-letter queue

Codex must add `scripts/send-poison-message.sh`. Run:

```bash
./scripts/send-poison-message.sh
./scripts/show-dlq-messages.sh
```

The demo should use a short visibility timeout and small `maxReceiveCount` so
the DLQ result is visible without making the webinar wait several minutes.

## Run tests

```bash
./mvnw test
./mvnw verify
```

Expected test layers:

- unit tests with Mockito;
- Spring Boot integration tests using Floci Testcontainers;
- an external end-to-end test against the running Podman Floci container;
- Terraform formatting and validation.

## Stop and clean up

```bash
terraform -chdir=infra destroy \
  -var='aws_endpoint_url=http://localhost:4566' \
  -auto-approve
podman rm -f floci
podman network rm floci-net
```

Only remove `floci-net` if no other local container is using it.

## Troubleshooting

### Floci is not AWS

The local run verifies request/response contracts and the connected service
flow, but Floci does not enforce production IAM, VPC networking, quotas,
latency, regional behavior, or AWS resilience semantics. The demo uses batch
size one plus partial-batch responses for deterministic DLQ behavior. Validate
those operational concerns in a separately authorized AWS environment before
production; this repository's default workflow intentionally cannot do so.

### Floci is not reachable

```bash
podman ps -a --filter name=floci
podman logs floci
curl -v http://localhost:4566
```

### Lambda cannot call Floci

Confirm all three values are consistent:

- Floci container name/hostname: `floci`
- network: `floci-net`
- `FLOCI_SERVICES_LAMBDA_DOCKER_HOST_OVERRIDE=floci`

Inspect spawned containers:

```bash
podman ps --filter label=floci=true
podman network inspect floci-net
```

### Permission denied or broken pipe on the socket

```bash
systemctl --user status podman.socket
ls -l /run/user/$(id -u)/podman/podman.sock
```

Use lowercase `:z` for the shared SELinux relabel. Do not switch blindly to a
privileged container.

### AWS CLI accidentally calls real AWS

For every manual command, use either `AWS_ENDPOINT_URL` or an explicit
`--endpoint-url http://localhost:4566`. Use only fake local credentials.

## Webinar guidance

Keep the live demonstration focused on this path:

```text
REST API -> DynamoDB RECEIVED -> SQS -> Lambda -> S3 receipt
                                    -> DynamoDB COMPLETED
```

Explain that Floci is for local development and integration testing. It does
not replace final testing in a real AWS account, especially for IAM fidelity,
service limits, networking, performance, resilience, and security validation.

## Official references

- Floci: https://floci.io/floci/
- Docker/Podman configuration:
  https://floci.io/floci/configuration/docker/
- Docker Compose and CI:
  https://floci.io/floci/configuration/docker-compose/
- Java Testcontainers integration:
  https://floci.io/floci/testcontainers/java/
- Source repository: https://github.com/floci-io/floci
