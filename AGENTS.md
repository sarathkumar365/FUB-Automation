# Agent Working Agreement

This agent acts as a pair programmer for this repository and supports:
- Technical research and validation
- Incremental code implementation
- Refactoring and test improvements
- Documentation and architecture guidance

## Project context
- Project name: `automation-engine`
- Domain: Follow Up Boss call automation
- Primary goal: detect call outcomes and create follow-up tasks automatically
- Near-term priority: Scenario 1 (call outcome -> task)
- Future priority: Scenario 2 (intent/transcription -> task)

## Rules reference
- Implementation and structure rules source of truth: `developer-rules.md`
- Before creating/moving modules or implementing behavior, follow `developer-rules.md`

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
- Use Lombok getters/setters wherever appropriate to reduce boilerplate while keeping code readable.
- Prefer constructor injection by default; use `@Autowired` only when it is truly appropriate (for example, optional/lazy wiring or framework-specific edge cases).

### Architecture pattern used in this repo
This project uses a pragmatic combination of:
1. Layered architecture
- Organize flow as `controller -> service -> client/persistence`.
- Keep HTTP concerns, orchestration logic, external API integration, and DB access separated.

2. Ports and adapters (hexagonal style)
- Use interfaces as ports (`WebhookParser`, `WebhookSignatureVerifier`, `WebhookDispatcher`, future `FollowUpBossClient`).
- Implement provider-specific adapters (for example, FUB parser/verifier/client adapters).
- Add new providers by adding adapters instead of rewriting core flow.

3. Strategy pattern for source/provider behavior
- Select behavior using `supports(source)` contracts.
- Introduce new source behavior via new strategy implementations.

4. Repository pattern
- Keep persistence access in repository interfaces/entities.
- Keep service layer focused on orchestration rather than query details.

5. Inbox pattern for webhook ingestion
- Validate and persist webhook events first, then process.
- This supports reliability, idempotency, retries, and async evolution.

## Module boundary usage rule (must follow)
- Always route work through the layered boundary: `controller -> service -> port(interface) -> adapter(impl) -> repository/rules`.
- If the task is "call Follow Up Boss APIs", use the FUB client port (`FollowUpBossClient`) and its adapter implementation under `client/fub`; keep all FUB HTTP/auth/header logic in that adapter layer only.
- If the task is "evaluate call outcome / decide task intent", use the rules module (`rules`) only; rule evaluation must not perform HTTP calls or repository access.
- If the task is "orchestrate flow/state transitions/retries", use service layer orchestration; services may call ports and repositories but must not embed provider-specific transport details.
- If the task is "persist or query data", use repository + entity modules only; do not place query logic in controllers, clients, or rule classes.
- Add new provider/source behavior by adding new adapter implementations behind existing ports, not by changing core orchestration contracts.

## Reuse-first policy
- Reuse existing modules, files, conventions, and patterns before adding new ones.
- Extend existing components safely instead of duplicating behavior.
- Introduce new abstractions only when they reduce complexity or improve testability.

## Unknowns, assumptions, and confirmation
- If requirements are ambiguous or only partially known, stop and confirm with the user before implementing.
- Do not ship speculative behavior for external API contracts.
- When uncertain, document assumptions clearly and ask for approval.

## Tech stack used in this project
### Language and runtime
- Java `21`

### Build and package
- Maven Wrapper (`mvnw`, `mvnw.cmd`)
- Spring Boot Maven Plugin

### Application framework
- Spring Boot `4.0.3`
- Spring MVC (`spring-boot-starter-webmvc`)
- Spring Validation (`spring-boot-starter-validation`)

### Persistence and database
- Spring Data JPA (`spring-boot-starter-data-jpa`)
- PostgreSQL JDBC Driver (`org.postgresql:postgresql`)
- Flyway migrations (`spring-boot-starter-flyway`, `flyway-database-postgresql`)

### Developer tooling
- Lombok
- Spring Boot DevTools (runtime/dev only)

### Testing
- `spring-boot-starter-data-jpa-test`
- `spring-boot-starter-flyway-test`
- `spring-boot-starter-validation-test`
- `spring-boot-starter-webmvc-test`

### External integration target
- Follow Up Boss REST API (`/v1`) via Basic Auth
- Webhook ingestion and signature verification for event-driven automation

## Code quality expectations
- Add or update tests with behavior changes.
- Keep methods focused and naming explicit.
- Prefer deterministic logic and guard edge cases.
- Update docs when behavior or setup changes.

## Mandatory testing policy
- For every code change, add at least one newly created test that validates the change.
- Every newly added test must be executed before marking a task complete.
- For every code change, run the previously existing test suite in addition to the new test(s).
- A change is considered acceptable only if overall test success is greater than 85%.
- If test execution is blocked (environment, credentials, infra), clearly report the blocker and do not claim validation as complete.
