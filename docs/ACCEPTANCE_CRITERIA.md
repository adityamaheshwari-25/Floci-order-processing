# Acceptance criteria

## Functional

- A valid order returns `202 Accepted`, a generated UUID, `RECEIVED`, and the
  correlation ID.
- An invalid order returns `400` Problem Details with field errors.
- A repeated `Idempotency-Key` with the same payload returns the original
  logical order.
- Reusing the same idempotency key with a different payload returns `409`.
- The processor changes the order to `COMPLETED` and records the S3 receipt key.
- `GET /api/v1/orders/{id}` returns the latest state; an unknown ID returns
  `404` Problem Details.
- A duplicate event produces no additional logical side effect.
- A poison event is visible in the DLQ after the configured retries.

## Local/cloud separation

- The local profile uses `http://localhost:4566` and fake credentials.
- The AWS profile contains no endpoint override and no hard-coded credentials.
- No command in the default local workflow can contact real AWS accidentally.
- Terraform local settings that skip AWS checks are not enabled for real AWS.

## Tests

- Unit tests cover validation, total calculation, publishing, idempotency,
  conditional transitions, receipt generation, and error mapping.
- Integration tests use a real Floci container, not mocked AWS clients.
- Tests prove the event and receipt JSON contracts.
- `./mvnw clean verify` succeeds.
- Terraform formatting and validation succeed.

## Operability

- `/actuator/health` reports application health.
- Logs correlate API and processor activity using `orderId` and
  `correlationId`.
- Scripts are repeatable and clean up only resources belonging to this demo.
- README commands work from a clean checkout.
- The entire happy-path webinar demo finishes within five minutes after images
  and dependencies have been pre-pulled.

## Security and correctness

- Money uses `BigDecimal`, an explicit currency, and deterministic rounding.
- Inputs have size and range limits.
- Credentials and raw customer payloads are not logged.
- The real AWS IAM policy follows least privilege.
- S3 is private in the real AWS configuration.
- Known emulator-versus-AWS differences are documented.

