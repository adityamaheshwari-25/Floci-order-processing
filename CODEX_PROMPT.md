# Prompt to give Codex

Open this repository and read `AGENTS.md`, `README.md`, and every file under
`docs/` before changing anything.

Implement the complete Floci order-processing demo in the phases defined in
`docs/IMPLEMENTATION_PLAN.md`. Continue until all acceptance criteria pass.
Use Podman and Floci for local AWS integration tests, Terraform for local AWS
resource provisioning, and GitHub Actions for CI.

After every phase:

1. run the relevant unit and integration tests;
2. fix failures rather than bypassing tests;
3. update the implementation checklist;
4. briefly report what was completed and what remains.

Do not deploy anything to a real AWS account. Do not ask for real AWS
credentials. Use only the local Floci endpoint and fake credentials while
building and verifying this project.

