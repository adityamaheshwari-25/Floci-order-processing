# Implementation plan

Codex should implement and verify these phases in order.

## Phase 1 — Repository and domain

- [x] Create Maven parent and wrapper.
- [x] Create `shared-domain`, `order-api`, and `order-processor` modules.
- [x] Add formatting, compiler, test and coverage configuration.
- [x] Implement immutable shared contracts and JSON round-trip tests.
- [x] Add `.gitignore`, `.editorconfig`, and initial documentation.

Exit condition: `./mvnw clean verify` succeeds for the domain module.

## Phase 2 — Order API

- [x] Implement request/response records and Bean Validation.
- [x] Calculate totals using `BigDecimal` and reject client-supplied totals.
- [x] Implement DynamoDB repository with conditional writes.
- [x] Implement SQS publisher.
- [x] Implement POST/GET endpoints and Problem Details errors.
- [x] Propagate or create `X-Correlation-Id`.
- [x] Implement request idempotency.
- [x] Add unit tests.

Exit condition: controller, service, repository and publisher unit tests pass.

## Phase 3 — Order processor

- [x] Implement the Lambda handler for SQS.
- [x] Validate event schema version.
- [x] Implement idempotent state transitions.
- [x] Write a deterministic JSON receipt to S3.
- [x] Handle retryable versus non-retryable failures explicitly.
- [x] Add unit tests for success, duplicate, poison and partial failure cases.

Exit condition: repeated processing produces one logical completion and one
receipt key.

## Phase 4 — Terraform and Floci

- [x] Configure an AWS provider for the local endpoint without affecting the
  AWS profile.
- [x] Create S3, SQS, DLQ, DynamoDB, Lambda, IAM and event mapping resources.
- [x] Package the Lambda artifact deterministically.
- [x] Export useful Terraform outputs.
- [x] Add local environment scripts.
- [x] Verify `fmt`, `validate`, `plan`, `apply`, and `destroy` against Floci.

Exit condition: AWS CLI lists all resources in Floci and Lambda is attached to
the SQS queue.

## Phase 5 — Integration and end-to-end tests

- [x] Add Floci Testcontainers for Spring Boot 4/Testcontainers 2.
- [x] Test S3, SQS and DynamoDB clients through Floci.
- [x] Test POST -> SQS -> Lambda -> DynamoDB/S3.
- [x] Test duplicate API requests.
- [x] Test duplicate event processing.
- [x] Test poison-message DLQ behaviour.
- [x] Ensure containers and resources are cleaned after tests.

Exit condition: `./mvnw clean verify` passes from a clean checkout with Podman
configured as the Testcontainers-compatible runtime.

## Phase 6 — Developer experience and CI

- [x] Add `scripts/start-floci.sh`, `stop-floci.sh`, `provision-local.sh`,
  `run-demo.sh`, `send-poison-message.sh`, and `show-dlq-messages.sh`.
- [x] Make scripts fail fast and safe to rerun.
- [x] Add GitHub Actions with a pinned Floci service image.
- [x] Run Maven tests and Terraform checks in CI.
- [x] Add dependency caching without caching generated credentials or state.
- [x] Complete README troubleshooting and architecture documentation.

Exit condition: a new developer can follow only `README.md` and finish the demo.

## Phase 7 — Webinar rehearsal

- [x] Time a clean demo from Floci startup through receipt verification.
- [x] Pre-pull all images.
- [ ] Capture a backup recording.
- [ ] Test on the company network and presentation machine.
- [x] Pin every tested version.
- [x] Confirm cleanup does not remove unrelated Podman resources.

Local rehearsal evidence (2026-08-26): Terraform created all nine resources in
about 51 seconds; the first SQS-triggered Java 21 cold start completed in about
23 seconds including its runtime-image pull. The complete prebuilt happy path
was comfortably below five minutes. Terraform then destroyed all nine
resources, and `floci-net` was inspected as empty before removal. Backup
recording and company-network/presentation-machine checks remain manual event
owner tasks.

## Readiness fixes - 2026-09-08

- [x] Apply Spotless formatting to the three processor files; `mvnw clean verify`
  passes with 16 tests and no failures or skipped tests.
- [x] Replace the README API startup command with the packaged JAR and read the
  queue URL and table name from Terraform outputs. Execute the documented
  PowerShell block successfully and confirm the health endpoint reports `UP`.
- [x] Pass Terraform formatting and validation checks, provision all nine local
  resources, and repeat the full end-to-end scenario: accepted order, SQS-triggered
  Lambda, completed DynamoDB order, S3 receipt, duplicate delivery without another
  receipt, and poison-message redrive after exactly two failed handler attempts.
