# Developer Rules

This file defines repository structure rules and implementation guardrails for this project.

## Source of truth
- Use this file as the default reference for project structure and implementation decisions.
- If a task conflicts with these rules, confirm with the user before deviating.

## Repository structure (MVP)
Use the following package and resource layout for Scenario 1 implementation:

- `src/main/java/com/fuba/automation_engine/config`
- `src/main/java/com/fuba/automation_engine/controller`
- `src/main/java/com/fuba/automation_engine/client/fub`
- `src/main/java/com/fuba/automation_engine/client/fub/dto`
- `src/main/java/com/fuba/automation_engine/service`
- `src/main/java/com/fuba/automation_engine/rules`
- `src/main/java/com/fuba/automation_engine/persistence/entity`
- `src/main/java/com/fuba/automation_engine/persistence/repository`
- `src/main/resources/db/migration`
- `src/test/java/com/fuba/automation_engine/controller`
- `src/test/java/com/fuba/automation_engine/service`
- `src/test/java/com/fuba/automation_engine/client/fub`
- `src/test/java/com/fuba/automation_engine/integration`

## Delivery style and workflow
- Make only small, reviewable, incremental changes.
- Avoid large one-shot changes unless explicitly requested.
- Keep each increment coherent: code + config + tests + docs where needed.
- Prefer vertical slices (small end-to-end behavior) over broad incomplete scaffolding.

## Engineering and architecture standards
- Follow clean code and SOLID principles.
- Keep module boundaries clear (controller, service, client, repository, model).
- Favor composition and interface-driven design where extension is expected.
- Use explicit DTOs for external APIs and avoid leaking transport models internally.
- Handle idempotency, retries, and failure states explicitly for automation flows.
- Never log secrets (API keys, tokens, webhook signing keys).

## Placement rules
- Controllers only handle HTTP concerns and delegate logic.
- External Follow Up Boss API code must stay inside `client/fub`.
- Rule classification logic must stay inside `rules`.
- Orchestration/business flow should stay inside `service`.
- JPA entities and repositories must stay under `persistence`.
- SQL schema changes must be added as versioned Flyway files under `db/migration`.

## Reuse and ambiguity policy
- Reuse existing modules, files, conventions, and patterns before adding new ones.
- Extend existing components safely instead of duplicating behavior.
- Introduce new abstractions only when they reduce complexity or improve testability.
- If requirements are ambiguous or partially known, stop and confirm with the user before implementing.
- Do not ship speculative behavior for external API contracts.

## Code quality expectations
- Add or update tests with behavior changes.
- Keep methods focused and naming explicit.
- Prefer deterministic logic and guard edge cases.
- Update docs when behavior or setup changes.

## Mandatory testing policy
- For every code change, add at least one newly created test that validates the change.
- Every newly added test must be executed before marking a task complete.
- For every code change, run the previously existing test suite in addition to the new test(s).
- A change is acceptable only if overall test success is greater than 85%.
- If test execution is blocked (environment, credentials, infra), clearly report the blocker and do not claim validation as complete.

## Naming guidance
- Prefer feature-specific names over generic names.
- Avoid catch-all utility classes unless there is proven reuse.
- Keep package names lowercase and class names explicit.
