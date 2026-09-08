# Codex instructions

Build the application described in this repository. Treat `README.md`,
`docs/ARCHITECTURE.md`, and `docs/ACCEPTANCE_CRITERIA.md` as the product
contract.

## Working rules

- Use Java 21, Spring Boot 4.x, Maven Wrapper, AWS SDK for Java v2, JUnit 5,
  AssertJ, Mockito, Terraform, Floci, and Podman.
- Produce a Maven multi-module repository with `order-api`, `order-processor`,
  and `shared-domain` modules.
- Keep AWS endpoint selection in configuration. Never scatter
  `http://localhost:4566` through application code.
- The `local` profile must use Floci and fake credentials. The `aws` profile
  must use the standard AWS credential/provider chain and must not override AWS
  endpoints.
- Use immutable DTOs/records where appropriate and `BigDecimal` for money.
- Validate every external request. Return RFC 9457 Problem Details responses.
- Make SQS processing idempotent because delivery is at least once.
- Never log credentials or complete customer payloads.
- Use structured logs containing `orderId` and `correlationId`.
- Infrastructure names must be configurable but default to the names in the
  architecture document.
- Pin dependency, plugin, provider, and container versions after verifying
  them. Do not leave `latest` in the final CI configuration.
- Do not silently skip unsupported Floci operations. Document any compatibility
  workaround in `README.md`.

## Required quality gates

Before declaring the project complete, run and report:

```bash
./mvnw clean verify
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra validate
```

Also execute the end-to-end local scenario from `docs/DEMO_RUNBOOK.md` and
confirm that:

1. an order is accepted by the REST API;
2. SQS triggers the processor;
3. DynamoDB contains the completed order;
4. S3 contains the receipt;
5. duplicate processing does not create a second receipt;
6. a poison message reaches the dead-letter queue after the configured retry
   count.

## Implementation sequence

Follow `docs/IMPLEMENTATION_PLAN.md` one phase at a time. At the end of every
phase, run its tests and update the checklist in that document. Preserve all
working user changes and keep commits small if the user asks Codex to commit.

