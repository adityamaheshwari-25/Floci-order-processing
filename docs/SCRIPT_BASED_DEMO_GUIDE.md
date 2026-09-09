# Run the Floci demo using the Bash scripts

This guide uses the repository scripts to run the local order-processing demo
on Windows with Git Bash and the previously tested rootful Podman connection.
Terraform provisions the infrastructure inside Floci using fake credentials.

Use two Git Bash terminals: one for setup and demo commands, and one for
the API. Stop at any failed command and resolve the error before continuing.

## Prerequisites

Make these tools available in Git Bash:

- Java 21
- Podman
- Terraform
- AWS CLI v2
- `curl` and `jq`

On Windows Git Bash, the startup and shutdown scripts use Podman directly to
avoid external Docker Compose named-pipe connection failures. On other systems,
they use `podman compose` with `compose.yaml`, requiring a working Compose provider.
The Maven Wrapper is included, so a separate Maven installation is unnecessary.

## 1. Start Floci in Git Bash

```bash
cd /c/Endava/EndevLocal/floci-order-processing

podman machine start
podman info --format '{{.Host.Security.Rootless}}'
```

If the machine is already running, continue. The tested rootful connection
reports `false`. For that connection, run:

```bash
export PODMAN_SOCKET_PATH=/run/podman/podman.sock
bash scripts/start-floci.sh
```

`PODMAN_SOCKET_PATH` tells the startup script where to find Podman's control
socket inside the Linux VM. Floci uses this socket to ask Podman to create the
Java Lambda runtime container. `export` makes the variable available to scripts
started from this terminal; it does not start Podman or create the socket.

Keep using this Git Bash terminal so the variable remains available during
cleanup. If Podman reports `true`, it is rootless: do not use the rootful socket
above; determine the socket path for that connection first.

Expected: `Floci is ready at http://localhost:4566`.

## 2. Build, test, and provision

```bash
export FLOCI_EXTERNAL_ENDPOINT=http://localhost:4566
sh ./mvnw --batch-mode --no-transfer-progress clean verify
```

Expected: `BUILD SUCCESS`, with tests and formatting checks passing.
The environment variable makes integration tests use the running Floci instance.

Then provision:

```bash
bash scripts/provision-local.sh
```

This script packages the processor, initializes and validates Terraform, creates
a local plan, and applies it. It does not pause for approval before applying the
saved plan. On a fresh setup, expect nine resources to be created.

The provision script internally invokes `./mvnw`. If that reports `Permission
denied` in your Bash environment, run `chmod +x mvnw` and retry the script.

## 3. Start the API in a separate Git Bash terminal

Stop any API instance already running from your IDE or another terminal first.
Then run:

```bash
cd /c/Endava/EndevLocal/floci-order-processing
bash scripts/start-api.sh
```

The script sets fake credentials, reads the queue URL and table name from
Terraform outputs, prepares the Windows-compatible Java socket directory, and
starts the packaged API with `--spring.profiles.active=local`. It stops with an
error if the JAR is missing or Terraform outputs cannot be read.

Leave this terminal running for API logs. Confirm the startup log says the
`local` profile is active. In your original Git Bash terminal, check readiness:

```bash
curl -fsS http://localhost:8080/actuator/health | jq .
```

Expected: `status` is `UP`.

## 4. Run the order demo in Git Bash

```bash
bash scripts/run-demo.sh
```

The script:

1. Submits an order to the API.
2. Waits for the order to become `COMPLETED` after SQS triggers Lambda.
3. Reads the receipt from S3.
4. Repeats the API request using the same idempotency key.
5. Checks that the same order ID is returned and one receipt exists.

Expected: `Demo passed for order ...`.

The script polls for about 30 seconds. A first Lambda image pull or cold start
may exceed that; inspect `podman logs floci` and the API logs if it times out.
Re-running with the same default idempotency key targets the same logical order.
For a fresh order on another run:

```bash
IDEMPOTENCY_KEY="demo-$(date +%s)" bash scripts/run-demo.sh
```

## 5. Test failure handling

```bash
bash scripts/send-poison-message.sh
sleep 30
bash scripts/show-dlq-messages.sh
```

The first script sends an event with an unsupported version. Processing fails,
and the queue is configured with `maxReceiveCount=2` before redrive to the DLQ.

Expected: the poison message appears in the dead-letter queue. If the result
is empty, wait a little longer and run the inspection script again. Receiving
a DLQ message temporarily hides it for its visibility timeout; it does not
delete it.

## 6. Clean up

Stop the API with **Ctrl+C** in its Git Bash terminal. In the original Git Bash terminal:

```bash
terraform -chdir=infra destroy -var='local_mode=true'
```

Review the local destruction plan and enter `yes`. After destruction succeeds:

```bash
bash scripts/stop-floci.sh
```

Keep Floci running until Terraform finishes destroying its resources. The stop
script stops Floci and removes `floci-net` only when empty.

## What each script does

| File | Purpose |
| --- | --- |
| `start-floci.sh` | Creates the network, starts Floci, and waits for readiness. |
| `provision-local.sh` | Packages the Lambda and provisions local resources with Terraform. |
| `start-api.sh` | Starts the API with local credentials, Terraform outputs, and the local profile. |
| `run-demo.sh` | Runs the successful order flow and checks duplicate API submissions. |
| `send-poison-message.sh` | Sends an unsupported event to exercise failure handling. |
| `show-dlq-messages.sh` | Receives and displays messages from the dead-letter queue. |
| `stop-floci.sh` | Stops Floci and removes the empty demo network. |
| `common.sh` | Shared environment settings and helper functions; other scripts load it automatically. |

## Verification scope

These scripts automate the successful flow, duplicate API submission checks,
and sending/inspecting a poison message. They do not automatically assert direct
duplicate SQS delivery, an unchanged receipt after that delivery, or exactly two
failed processor attempts. Those require additional verification using processor
logs and S3 metadata.

See also the [README](../README.md) and [presentation runbook](DEMO_RUNBOOK.md).
