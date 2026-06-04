# Engine Extraction — Open-Source Lift

> **Status:** Boundary verified against code (2026-06-04). Ready to start; design no longer blocked.
>
> **Unblocked:** `feature/domain-events` (Phases 0–5) is merged. The Lead→Person rename and the field-validator are in their final shape, so the SPI design can be drafted against stable code. Verified: Phase 4/5 did **not** leak domain events into the kernel — the engine's step-time scope still uses the generic `ExpressionScope`; all domain-event logic is confined to the host-side `trigger/` package.

> **Verification method (2026-06-04):** Full structural inventory + outbound import census of every file under `service/workflow/` + reverse-dependency census + schema/FK audit of all Flyway migrations + config classification + direct reads of every Phase 4/5-changed kernel file. Findings below supersede the earlier "3 leaks" summary — there are 3 **code** leaks **plus 2 schema FK leaks** the first pass missed.

## Goal

Lift the workflow engine out of this repo as a standalone, domain-agnostic library anyone can adopt. Users plug in their own step types via the `WorkflowStepType` SPI; the engine ships with no knowledge of FUB, real estate, leads, persons, or webhooks.

Steps and triggers are **not** the deliverable. They are demonstrated as examples only. The deliverable is the *engine*: execution, planning, expressions, persistence, the step SPI, the scheduler/worker.

The engine's true public entry point is a single method: **`WorkflowExecutionManager.plan(WorkflowPlanRequest)`**. Anything that calls it — a webhook router, a cron, a REST controller, a Kafka consumer — is a *trigger source* and belongs to the host, not the engine.

## Why

The kernel is genuinely well-factored — it was built behind a `Map<String,Object>` SPI from day one. Verified facts that make this a clean lift:

- Only **4 files** outside `service/workflow/` import from inside it (2 thin admin controllers + 2 client adapters). The engine's surface is already tightly encapsulated.
- The JPA entities (`AutomationWorkflowEntity`, `WorkflowRunEntity`, `WorkflowRunStepEntity`) have **zero** `@ManyToOne`/`@OneToMany`/`@JoinColumn` to business entities — business IDs are loose scalar columns, not object graphs.
- The engine's expression layer, step SPI, graph model, and run state are pure (no outbound non-workflow imports).

## Boundary — inside vs outside (verified)

### INSIDE (ships in the open-source module)

**Orchestration / entry**
- `WorkflowExecutionManager` — `plan(WorkflowPlanRequest)`, idempotency, graph instantiation *(imports: persistence + `KeyNormalizationHelper` only — clean)*
- `WorkflowStepExecutionService` — execute one claimed step, retries, transitions *(holds leaks #1 + #2)*
- `WorkflowExecutionDueWorker` — scheduled poll of due steps *(depends only on `WorkflowWorkerProperties` — engine config)*
- `WorkflowRunControlService` — cancel/supersede *(persistence only)*
- `WorkflowRunQueryService` — run read APIs *(layering inversion → `controller.dto.*`; see Leak L1)*
- `AutomationWorkflowService` — workflow CRUD *(persistence + support — clean)*
- `RunSupersedePolicy` — Phase 5 run-collision policy *(persistence only; person-flavoured **naming** only — operates purely on `WorkflowRunEntity` + payload keys)*

**Step SPI** (the extension point) — all pure
- `WorkflowStepType`, `WorkflowStepRegistry`, `StepExecutionContext`, `StepExecutionResult`, `RetryPolicy`

**Run state**
- `RunContext` *(pure `Map<String,Object>` carrier; naming leak: `sourcePersonId`)*

**Expression layer** — all pure
- `ExpressionEvaluator`, `JsonataExpressionEvaluator`, `ExpressionScope`

**Graph**
- `WorkflowGraphValidator` *(holds leak #3)*, `GraphValidationResult`

**Generic step HTTP infra** — pure
- `http/WorkflowHttpClient` (+ request/response/exception). Generic HTTP abstraction the `http_request`/`slack_notify` example steps use.

**Persistence**
- `AutomationWorkflowEntity`, `WorkflowRunEntity`, `WorkflowRunStepEntity` + status enums + repositories + `WorkflowRunStepClaimRepository` (claim/skip-locked queue)
- Migrations V10 + V11 + V16 *(holds schema leaks S1 + S2)*

**Engine config**
- `TimeConfig` (`Clock` bean), `JacksonConfig` (generic `ObjectMapper`), `WorkflowWorkerProperties` + `WorkflowWorkerSchedulingConfig`, `WorkflowStepHttpProperties` + `WorkflowStepHttpConfig`, `HttpClientConfig`

**Controller DTOs** (must move into the engine module to fix L1)
- `WorkflowRunDetailResponse`, `WorkflowRunStepDetail`, `WorkflowRunSummary`

### OUTSIDE (stays in the FUB app — host glue or business)

- **The entire `trigger/` package** — host glue, confirmed business-coupled:
  - `WorkflowTriggerRouter` (imports `service.event.*`, persistence, `WorkflowTriggerRouterProperties`)
  - `DomainEventTriggerType` (imports `DomainEvent` + `PersonSnapshotResolver`)
  - `DomainEventTriggerValidator` (imports `PersonDiffComputer` + `PersonUpsertService`; hardcodes `person.*`, `call.created`, `note.*` kinds) — *reached by `AdminWorkflowController` at workflow-save time; host must own trigger validation*
  - `EntityRef` (generic value record, travels with the trigger code)
  - *(Note: the older `WorkflowTriggerType` / `WorkflowTriggerRegistry` / `TriggerMatchContext` / `FubWebhookTriggerType` classes referenced in earlier drafts no longer exist — replaced by the domain-event trigger path in Phase 4.)*
- **`expression/DomainEventScopeBuilder`** — physically sits in the engine's `expression/` package but is pure host glue (imports `DomainEvent`; builds `change.*`/`current.*`/`event.origin` for domain-event triggers). Used **only** by `DomainEventTriggerType`, never by the engine. **Packaging leak — move it out with the trigger code.**
- **All step implementations** under `service/workflow/steps/`:
  - *Generic (become OSS examples):* `HttpRequestWorkflowStep`, `DelayWorkflowStep`, `SetVariableWorkflowStep`, `BranchOnFieldWorkflowStep`, `SlackNotifyWorkflowStep`
  - *Business (stay private):* `FubReassign`, `FubAddTag`, `FubCreateNote`, `FubCreateTask`, `FubMoveToPond`, `WaitAndCheckClaim`, `WaitAndCheckCommunication`, `AiCall`
- **`service/workflow/aicall/`** client package — business (AI call integration)
- **Domain / integration / ingestion:** `service.person.*` (`PersonSnapshotResolver`, `PersonUpsertService`, `PersonDiffComputer`), `service.BusinessHoursService`, `service.fub.*`, `FollowUpBossClient`, `service.event.*` (`DomainEvent`, `EngineWriteCoordinator`, `EngineEchoGate`), `service.webhook.*`, `service.call`, `service.note`
- **Business entities + tables:** `PersonEntity`, `WebhookEventEntity`, `EventEntity`, `ProcessedCallEntity`, `AppUserEntity` (+ their repos and migrations)
- **Business config:** `Fub*`, `BusinessHours*`, `CallOutcomeRules*`, `AiCallService*`, `Webhook*`, `Jwt*`, `AdminAuth*`, `SecurityConfig`, `WorkflowTriggerRouterProperties` (serves the host router)

## Coupling inventory (everything that needs decoupling)

### Code leaks (3) — engine files reaching into business code

| # | File | Coupling today | Fix |
|---|------|----------------|-----|
| 1 | `WorkflowStepExecutionService` (import l.11; field l.47; use in `buildRunContext` ~l.233) | Injects `service.person.PersonSnapshotResolver`, populates `RunContext.person` | `List<RunContextContributor>` SPI; host registers a person contributor |
| 2 | `WorkflowStepExecutionService` (import l.10; field l.48; use ~l.240) | Injects `service.BusinessHoursService`, populates `RunContext.now` | Same SPI — host registers a "now" contributor |
| 3 | `WorkflowGraphValidator` (import l.3; `validatePersonFieldReferences` l.306–333) | Calls `service.person.PersonUpsertService.capturedFieldNames()` | Drop the method or replace with a `List<GraphValidationRule>` SPI; host registers the person-field rule |

### Schema leaks (2) — engine tables with FK constraints to business tables

| # | Constraint | Migration | Detail | Fix |
|---|------------|-----------|--------|-----|
| S1 | `fk_workflow_runs_webhook_event`: `workflow_runs.webhook_event_id → webhook_events(id)` | V10 l.52–55 | Nullable, `ON DELETE SET NULL`. Column is a loose correlation id, no JPA relationship. | Drop the FK in the extracted schema; keep `webhook_event_id` as an opaque nullable `BIGINT` (or rename to a generic `trigger_ref_id`). |
| S2 | `fk_workflow_runs_domain_event`: `workflow_runs.domain_event_id → events(id)` | V23 l.10–13 | Nullable, `ON DELETE SET NULL`. Same loose-id shape. | Drop the FK; keep the column as an opaque correlation id. |

*(The referenced `webhook_events` and `events` tables don't exist in a standalone engine DB, so these two FKs must be dropped regardless. `automation_workflows` and `workflow_run_steps` have **no** business FKs — clean.)*

### Layering / packaging issues (mechanical, not coupling)

- **L1 — layering inversion:** `WorkflowRunQueryService` imports `controller.dto.{WorkflowRunDetailResponse, WorkflowRunStepDetail, WorkflowRunSummary}`. Move those three DTOs into the engine module (they're workflow-specific, not business).
- **L2 — packaging:** `DomainEventScopeBuilder` lives under `service/workflow/expression/` but is host glue. Move it to the host's trigger package during the split.

### Naming leak (cosmetic, no behaviour change)

- `RunContext.sourcePersonId`, `StepExecutionContext.sourcePersonId`, `WorkflowRunEntity.sourcePersonId`, and the `workflow_runs.source_person_id` column are all loose `String`/`VARCHAR` ids with no FK. Rename to `subjectId` / `entityId` / `source_entity_id` during the lift. `RunSupersedePolicy` carries the same person-flavoured naming.

## Effort

**Clean lift.** With steps and triggers excluded, the engine surgery is small:
- 3 code leaks → one `RunContextContributor` SPI + one `GraphValidationRule` SPI (~half a day).
- 2 schema FK leaks → drop two `ALTER`/constraint lines in the extracted migrations (~1 hour).
- L1/L2 packaging moves + naming rename → mechanical (~half a day).

The bulk of the remaining work is **packaging, not refactoring**: new Maven module, package moves, example steps, README, sample workflow JSON, docker-compose with Postgres, an integration test.

## Success criteria

- New module compiles and tests pass with **zero** imports from `service.fub.*`, `service.person.*`, `service.event.*`, `service.webhook.*`, `service.BusinessHoursService`, and **zero** FK constraints to `webhook_events` / `events` / `persons`.
- A consumer can register a `WorkflowStepType`, define a workflow as JSON, call `WorkflowExecutionManager.plan(...)`, and watch the run execute end-to-end (including a delay that survives a JVM restart) with no FUB-shaped infrastructure present.
- A "build your own step" walkthrough in ~30 lines of user code.
- Sample workflow JSON + docker-compose (Postgres) + one integration test.

## Decisions locked

- **Name:** `Inline` (thesis: runs inline with the host app, not as a separate cluster). Maven artifact likely `inline-engine`; namespace `io.github.<handle>.inline.*` to start.
- **License:** Apache 2.0.
- **v0.1.0:** Spring Boot only (JPA + scheduled worker), shipped as-is.
- **v0.2.0 (roadmap):** split `inline-core` (pure-Java SPI + execution, persistence behind a `RunStore` interface) from `inline-spring` (Boot starter), enabling Quarkus/Micronaut/plain-JVM hosts.

## Out of scope

- Migrating the FUB app to consume the extracted module (separate effort).
- Multi-tenant, auth, admin UI, observability — the OSS artifact is the engine library + an example app, not a SaaS.
- A bundled trigger router. Hosts call `plan()` from whatever entry point they like; a `POST /triggers` helper, if ever shipped, is a separate companion module.

## Phase docs (write once design starts)

```
phases.md                    ← phase list
phase-0-spike.md             ← scratch-module compile check (stub the 2 beans, drop the 2 FKs)
phase-1-contributor-spi.md   ← RunContextContributor + GraphValidationRule SPIs
phase-2-module-split.md      ← new Maven module, package moves, DTO move (L1), scope-builder move (L2), rename sourcePersonId
phase-3-examples-readme.md   ← 5 generic example steps, sample workflow JSON, README, docker-compose
```

A repo-decision record (`Docs/repo-decisions/RD-007-engine-as-standalone-library.md`) should accompany Phase 1.

## Open questions

- Trigger validation seam: the host needs to plug trigger-shape validation (today `DomainEventTriggerValidator`) into the engine's workflow-save path. Expose it via the same `GraphValidationRule` SPI, or a dedicated `TriggerValidator` hook?
- `events`/domain-event correlation: keep `domain_event_id` as an opaque column, or drop it entirely from the OSS schema (it's meaningless without the host's event store)?
- Persistence flavour for v0.2: keep JPA in `-core`, or define a thin `RunStore` port with a JPA adapter in `-spring`?
