# Webinar demo runbook

Target duration: 12–15 minutes.

## Before the session

1. Pin the Floci image used in rehearsal.
2. Pull Floci and the Lambda runtime images.
3. Run the entire scenario once on the presentation network.
4. Reset local state.
5. Open terminals for Floci logs, API logs, and commands.
6. Keep a successful backup recording available.

## Live sequence

### 1. Establish the baseline

Show that the AWS CLI is using fake credentials and the local endpoint. Explain
that no AWS account is involved.

### 2. Start Floci

Run `scripts/start-floci.sh`, then show the Floci container and its logs.

Teaching point: lightweight services are emulated in-process, while Lambda
uses a container for runtime fidelity.

### 3. Provision with Terraform

Run `scripts/provision-local.sh`. Show the plan summary and then list S3, SQS,
DynamoDB, and Lambda resources using the AWS CLI.

Teaching point: the Terraform resource definitions remain AWS-shaped; the
local provider configuration changes their destination.

### 4. Run the Spring Boot API

Follow the Bash or PowerShell commands in [README.md](../README.md#run-the-api):
read `ORDERS_QUEUE_URL` and `ORDERS_TABLE` from Terraform outputs, then start
the packaged API JAR with the `local` profile. Show `/actuator/health`.

Teaching point: application business logic does not know whether the SDK is
talking to Floci or AWS.

### 5. Submit an order

Run `scripts/run-demo.sh`. Pause after the `202` response and ask the audience
what they expect to happen next.

Show:

1. the `RECEIVED` order;
2. the SQS/Lambda processing log;
3. the final `COMPLETED` order;
4. the JSON receipt in S3.

### 6. Prove idempotency

Submit the same request again using the same idempotency key, then redrive the
same event. Show that the same order and receipt key remain.

Teaching point: SQS provides at-least-once delivery, so idempotency is an
application responsibility.

### 7. Demonstrate failure handling

Send the poison event and inspect the DLQ.

Teaching point: local integration testing can cover failure paths without
creating shared cloud resources.

### 8. Close honestly
State that Floci accelerates development and CI but does not replace real AWS
testing for IAM, networking, service quotas, performance, resilience, or final
acceptance.

## Recovery plan

- If the application fails, switch to the already-running backup environment.
- If Lambda networking fails, show the SQS message and explain the Podman named
  network while restarting from the known-good script.
- If an image pull fails, use pre-pulled pinned images.
- If recovery exceeds two minutes, play the backup recording and continue the
  explanation live.

