# Why Floci and Lambda use separate containers

Floci implements the emulated SQS, DynamoDB, and S3 services itself. Lambda
function execution is different: it needs to run your application code in an
appropriate runtime. In this project, that is Java 21 running
`order-processor.jar` in a separate container managed by Floci.

## Why SQS, DynamoDB, and S3 share one container

Floci contains built-in implementations of the AWS API operations needed for
local development:

| Emulated service | What Floci handles |
| --- | --- |
| SQS | Messages, delivery, visibility timeouts, retries, and dead-letter queues |
| DynamoDB | Items, reads, writes, and conditional updates |
| S3 | Object storage and retrieval |

These implementations are components of the same Floci application. They can
share one process and container, with API requests entering through port 4566.
Floci routes each request to the appropriate service implementation.

Floci is emulating AWS APIs and behavior; it is not starting the actual AWS
implementations of SQS, DynamoDB, or S3. Combining these emulations makes the
local environment simpler to start and manage.

## Why Lambda has a separate runtime container

Lambda has two responsibilities in this local setup:

| Responsibility | Where it runs |
| --- | --- |
| Register functions, store configuration, and coordinate invocation | Floci container |
| Execute your Java handler from `order-processor.jar` | Separate Lambda runtime container |

The runtime container provides:

- **The required runtime.** This function needs Java 21. Other functions can
  require different languages or runtime versions.
- **Separation of execution environments.** The function has its own processes,
  dependencies, and environment variables. This is useful isolation, not a
  claim that the local emulator provides AWS's production security boundaries.
- **An independent lifecycle.** Floci can manage the function's execution
  environment separately from its queue and storage implementations.

The separate container is therefore for executing application code. It does
not mean every emulated AWS service needs an individual container.

## How this repository creates the runtime

1. Terraform supplies the function JAR, Java 21 runtime setting, handler name,
   and environment variables in [`infra/main.tf`](../infra/main.tf).
2. The Floci container has access to the Podman socket through the volume
   mapping in [`compose.yaml`](../compose.yaml).
3. Floci uses that connection to manage a separate Lambda runtime container.
4. The configured SQS event source mapping connects order events to function
   execution.

The processor is not a separate service declared in Compose. Floci manages
its execution container.

## Where `floci-net` comes from

`floci-net` is a project-defined Podman network, not an AWS service.

[`scripts/start-floci.sh`](../scripts/start-floci.sh) creates it if it does not
already exist:

```bash
podman network inspect floci-net >/dev/null 2>&1 || podman network create floci-net >/dev/null
```

[`compose.yaml`](../compose.yaml) declares the network, connects Floci to it,
and passes this configuration to Floci:

```yaml
FLOCI_SERVICES_LAMBDA_DOCKER_NETWORK: floci-net
```

That setting tells Floci to connect its Lambda runtime containers to the same
network. The processor can then reach Floci by the hostname `floci`.

## Why the two endpoint addresses differ

| Caller | Address used to reach Floci | Connection path |
| --- | --- | --- |
| Terraform and the Order API running on the host | `http://localhost:4566` | Through the published Podman port `4566:4566` |
| Processor running inside the Lambda container | `http://floci:4566` | Directly over `floci-net` |

Both addresses lead to the same Floci service. Inside the Lambda container,
`localhost` refers to that Lambda container, not to the host or Floci.

The Terraform variable `lambda_endpoint_url` supplies the processor's
`AWS_ENDPOINT_URL` environment variable. It is the destination for outgoing
AWS API calls, not an address for invoking Lambda.

Publishing a port on the Lambda container would allow incoming connections
to it. It would not change the destination of its outgoing requests to Floci.

![Local container network](diagrams/local-container-network.png)

## Could everything run in one container?

An emulator could be designed to run service implementations and function
code together. Separate runtime containers are a design choice that supports
different language runtimes and independent execution environments.

Conversely, local queue and storage emulators could run as separate services.
Floci combines them to simplify local development. This container arrangement
describes this local emulator setup, not AWS's production infrastructure.

See [the architecture document](ARCHITECTURE.md) for the complete order flow.
