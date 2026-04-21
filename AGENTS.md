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
- UI priority (v0.1): internal admin UI for webhook operations visibility and replay workflows

## Rules reference
- Implementation and structure rules source of truth: `developer-rules.md`
- Before creating/moving modules or implementing behavior, follow `developer-rules.md`
- **System-wide implementation deep-dive:** `Docs/deep-dive/` — 12 documents covering every backend flow, configuration value, database schema, and design decision. Start with `Docs/deep-dive/README.md` for the index and reading order. Read these before making changes to understand how the system works end-to-end.
- UI implementation plan source of truth: `ui/Docs/ui-0.1-plan.md`
- UI style source of truth: `Docs/ui-style-guide-v1.md` + `Docs/ui-figma-reference.md` + `ui/src/styles/tokens.css`

## Delivery style and workflow
- Make only small, reviewable, incremental changes.
- Avoid large one-shot changes unless explicitly requested.
- Keep each increment coherent: code + config + tests + docs where needed.
- Prefer vertical slices (small end-to-end behavior) over broad incomplete scaffolding.

## Branch strategy (must follow)
- Treat `main` as production-only; do not use `main` as the base branch for feature or bug-fix work.
- Use `dev` as the base only for creating a feature parent branch.
- Required branch hierarchy for all new feature work:
  - create one feature parent branch from `dev`
  - feature parent branch names must be feature-only (no phase identifiers), for example:
    - `feature/lead-management-platform`
    - not `feature/lead-management-platform-phase-3`
  - create phase planning and phase implementation branches from that feature parent branch
  - do not create phase branches directly from `dev`
  - merge phase branches back into the feature parent branch first
  - merge the completed feature parent branch into `dev` through normal review flow
- Keep feature and phase branches short-lived and purpose-specific.

## Feature documentation workflow (must follow)
- For every new feature, create a dedicated folder under `Docs/features/<feature-slug>/`.
- Each feature folder must include:
  - `research.md` for discovery, analysis, and references
  - `plan.md` for the approved implementation plan
  - `phases.md` for phase definitions and status tracking
  - `phase-<n>-implementation.md` files to document what was implemented in each phase
- Before implementing any code change, read the feature's `research.md`, `plan.md`, and current phase docs.
- After completing a phase, update:
  - the corresponding `phase-<n>-implementation.md`
  - `phases.md` status
- After completing any implementation step (not just full phases), update the corresponding feature docs immediately:
  - mark the step as completed in the relevant doc/checklist
  - keep status/progress current so the next agent can continue without re-discovery
- Keep entries concise, chronological, and handoff-friendly so the next agent can continue without rediscovery.
- Before implementing any feature/code change, read in this order:
  1. `Docs/repo-decisions/README.md`
  2. all `Accepted` repo decisions relevant to touched modules
  3. feature docs under `Docs/features/<feature-slug>/`
- If a feature RFC introduces a repo-wide decision, promote it to `Docs/repo-decisions/` in the same phase.
- If a user request does not mention this documentation workflow, the agent must still follow it and briefly remind the user that the repo uses:
  - repo-wide decisions in `Docs/repo-decisions/`
  - feature workflow docs in `Docs/features/<feature-slug>/`

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
- Before writing custom utility/parsing/infrastructure code, look for existing safe, credible, and well-maintained packages that solve the same problem.
- Extend existing components safely instead of duplicating behavior.
- Introduce new abstractions only when they reduce complexity or improve testability.

## Unknowns, assumptions, and confirmation
- If requirements are ambiguous or only partially known, stop and confirm with the user before implementing.
- Do not ship speculative behavior for external API contracts.
- When uncertain, document assumptions clearly and ask for approval.

## Spec adherence and scope discipline (must follow)
This section exists because of a real incident (2026-04-21, `ai_call` Phase 3 work) where an agent shipped code that quietly deviated from an explicit spec line (`default retry policy: NO_RETRY`). The deviation was framed as "hardening" and was caught only by an independent review. These rules are designed to prevent a repeat.

- **Re-read the relevant spec before editing, every time.** Do not rely on memory of what a feature doc, repo decision, or phased plan says. If you are about to change the behavior of a step, step type, adapter, or contract, open the corresponding `Docs/features/<slug>/phase-<n>-implementation.md`, `plan.md`, and any `Docs/repo-decisions/` entry, and reread the lines that constrain the change. The cost is seconds; the cost of skipping it is silent spec drift.
- **Treat the spec as the source of truth and your judgment as a hypothesis.** When your instinct disagrees with a written spec line, the spec wins by default. Your instinct may be right — but it earns a code change only by first earning a spec amendment in a separate, explicit conversation.
- **Do not widen scope from "fix X" to "also improve Y".** A fix addresses the reported defect and nothing else. If you notice an adjacent issue while fixing the reported one, record it as a follow-up in `phases.md` (or the feature's tracking doc) — do not bundle it into the same change. "Root-cause fix" means fixing the root cause of the reported defect, not rewriting the surrounding design.
- **Surface any spec deviation loudly, in-conversation, before shipping it.** If a task cannot be completed without deviating from a written spec line, stop and say so explicitly: quote the spec line, state the proposed deviation, and ask for approval. Do not bury deviations under words like "hardening", "resilience", or "cleanup" in commit messages or docs.
- **Tests encode the spec, not the implementation.** Before writing an assertion, ask: "does this assertion match what the spec says should happen, or what my code happens to do?" If the two disagree, the test must be written against the spec and the code must be fixed to match. A passing test that locks in a spec deviation is worse than no test.
- **Self-review before declaring done.** After implementing a change, re-read the spec lines you identified at the start and walk the diff against each one. If any spec line is no longer satisfied by the code, either revert the offending change or raise the deviation before declaring the task complete.
- **When a second opinion lands, verify against the source first.** If a reviewer (human or agent) flags a deviation, the first response is to re-read the spec and compare, not to defend the existing code. Only after the spec has been reread may you argue for or against the reviewer's reading.

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

### Frontend stack (UI 0.1)
- React + TypeScript + Vite
- Tailwind CSS + shadcn/ui
- React Router
- TanStack Query
- Zod
- Native `EventSource` for SSE
- Vitest + Testing Library
- Playwright (smoke/e2e)

### External integration target
- Follow Up Boss REST API (`/v1`) via Basic Auth
- Webhook ingestion and signature verification for event-driven automation

## UI architecture and delivery model (must follow)
- Frontend root must remain `ui/` and be treated as a standalone app module.
- Use a hybrid model:
  - Dev: run Vite (`:5173`) and Spring (`:8080`) together with proxy-based API access.
  - Prod: package built UI assets into Spring static output and serve under `/admin-ui/*`.
- Keep frontend API paths relative (`/admin/*`, `/webhooks/*`) to avoid environment-specific hardcoding.
- Do not couple UI code to server-side templates; UI must consume HTTP contracts only.
- Keep module boundaries in UI clear:
  - `ui/src/app`: router/providers/layout
  - `ui/src/platform`: API client/session boundary/query setup/error mapping
  - `ui/src/modules/webhooks`: webhook list/live/detail features
  - `ui/src/modules/processed-calls`: processed calls list/replay features
  - `ui/src/shared`: reusable types/utilities/primitives

## UI engineering rules for smooth development
- Build UI in small vertical slices (feature + tests + docs/runbook update where needed).
- Reuse existing backend endpoints and DTO contracts; do not invent UI-only backend behavior.
- Use Zod at API boundaries to validate response shapes before data enters feature state.
- Keep server state in TanStack Query; avoid duplicating fetched data in local component state.
- Keep SSE handling isolated in a small platform wrapper/hook; include:
  - explicit event-name handling (`webhook.received`, `heartbeat`)
  - deduplication by stable identifier (`id`)
  - reconnect-safe behavior and connection state reporting
- Keep form/filter state serializable and URL-friendly where practical.
- Prefer composition over large page components; separate data hooks, view components, and mapping utilities.
- Keep UI accessible by default:
  - semantic elements, labels, keyboard-accessible controls, and focus-safe modals/drawers
  - color contrast and status indicators that are not color-only
- Keep styling consistent via design tokens/CSS variables and shared primitives; avoid ad-hoc one-off styles.
- Never log secrets or sensitive payloads in browser console output.
- For replay/destructive-like actions, require explicit user confirmation and clear success/error feedback.

## UI test policy additions
- For each UI behavior change, add at least one new frontend test.
- Run new frontend tests and existing frontend suite before marking UI work complete.
- For hybrid/runtime changes (scripts/build wiring), include at least one smoke validation path.
- Preserve backend test validation requirement for cross-layer changes.

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
