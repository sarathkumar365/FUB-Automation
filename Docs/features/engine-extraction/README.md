# Engine Extraction — Open-Source Lift

> **Status:** Stub. Intent captured; design deferred until `feature/domain-events` lands.
>
> **Blocked on:** `feature/domain-events` merge (Lead → Person rename and field-validator changes touch the only two real coupling points in the kernel).

## Goal

Lift the workflow engine out of this repo as a standalone, domain-agnostic library that anyone can adopt to build their own workflow-driven automation. Users plug in their own step types via the existing `WorkflowStepType` SPI; the engine ships with no knowledge of FUB, real estate, leads, or persons.

Steps are **not** part of what we ship. They are demonstrated as examples only. The deliverable is the *engine* — execution, planning, expressions, persistence, step SPI, trigger SPI.

## Why

The kernel is genuinely well-factored — it was built behind a `Map<String,Object>` SPI from day one. Showcasing it as open source:

- demonstrates the engineering artifact (JSONata templating + DAG validation + pluggable step SPI + resumable runs with at-least-once execution),
- forces the boundary to stay clean as the FUB app evolves,
- gives the project an identity separable from any one CRM integration.

## Boundary — inside vs outside the engine

### INSIDE (ships in the open-source module)

**Orchestration**
- `WorkflowExecutionManager` — `plan(WorkflowPlanRequest)`, idempotency, graph instantiation
- `WorkflowStepExecutionService` — execute one claimed step, retries, transitions
- `WorkflowExecutionDueWorker` — poll due steps
- `WorkflowRunControlService`, `WorkflowRunQueryService`, `AutomationWorkflowService`

**Step SPI**
- `WorkflowStepType` (interface)
- `WorkflowStepRegistry`
- `StepExecutionContext`, `StepExecutionResult`, `RetryPolicy`

**Run state**
- `RunContext` (pure `Map<String,Object>` carrier)

**Expression layer**
- `ExpressionEvaluator`, `JsonataExpressionEvaluator`, `ExpressionScope`

**Graph**
- `WorkflowGraphValidator` (minus one method — see Leak #3 below)
- `GraphValidationResult`

**Trigger SPI** (the abstract SPI, not the routers)
- `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `TriggerMatchContext`, `EntityRef`

**Persistence**
- `AutomationWorkflowEntity`, `WorkflowRunEntity`, `WorkflowRunStepEntity`
- Their repositories
- Schema migrations

### OUTSIDE (stays in the FUB app)

- Domain model: `PersonEntity`, `LeadEntity`, `WebhookEventEntity`, `PersonSnapshotResolver`, `PersonUpsertService`, `BusinessHoursService`
- FUB integration: `FollowUpBossClient`, `FubCallHelper`, `ProcessedCallRepository`
- Webhook ingestion: `NormalizedWebhookEvent`, `NormalizedDomain`, `NormalizedAction`, `WorkflowTriggerRouter`, `FubWebhookTriggerType`
- All step implementations under `service/workflow/steps/` and `service/workflow/aicall/` (a curated subset becomes open-source *examples*; the FUB-specific ones stay private)

## The three leaks (all that needs decoupling)

| # | File | Today | After |
|---|------|-------|-------|
| 1 | `WorkflowStepExecutionService` (l. 47, 233) | Injects `PersonSnapshotResolver`, populates `RunContext.person` | Injects `List<RunContextContributor>`; host registers a person contributor |
| 2 | `WorkflowStepExecutionService` (l. 48, 240–242) | Injects `BusinessHoursService`, populates `RunContext.now` | Same SPI — host registers a "now" contributor |
| 3 | `WorkflowGraphValidator` (l. 306–333) | `validatePersonFieldReferences()` calls `PersonUpsertService.capturedFieldNames()` | Drop the method or replace with `List<GraphValidationRule>` SPI; host registers a "person fields" rule |

Naming-only leak (not coupling): `RunContext.sourcePersonId` and `StepExecutionContext.sourcePersonId` are `String` IDs. Rename to `subjectId` or `entityId` during the lift — no behavior change.

## Success criteria

- New module compiles and tests pass with **zero** imports from `service.fub.*`, `service.person.*`, `service.webhook.*`, `service.BusinessHoursService`.
- A consumer can register a `WorkflowStepType`, define a workflow as JSON, fire `WorkflowExecutionManager.plan(...)`, and see the run execute end-to-end with no FUB-shaped infrastructure present.
- A "build your own step" walkthrough in ~30 lines of user code.
- Sample workflow JSON + docker-compose with Postgres + an integration test.

## Out of scope

- Migrating the FUB app to consume the extracted module (separate effort).
- Multi-tenant features, auth, admin UI, observability stack — the open-source artifact is the engine library and its example app, not a SaaS.
- Versioned public API guarantees — initial release is `0.x`.

## Blockers / sequencing

`feature/domain-events` is mid-flight and is actively reshaping the exact two coupling points the extraction would touch:

- Lead → Person rename touches `PersonSnapshotResolver` (leak #1).
- Field-validator changes touch `PersonUpsertService.capturedFieldNames()` (leak #3).

Designing the `RunContextContributor` / `GraphValidationRule` SPIs against these in their half-migrated state would force rework. **Wait for `feature/domain-events` to merge before writing phase docs.**

Permitted now (no merge conflict risk):
- Throwaway spike: copy kernel packages into a scratch module, stub the two beans, confirm it compiles standalone. De-risks the "is it really this clean?" assumption without committing to design choices.

## Future phase docs (do not write yet)

```
phases.md                    ← phase list
phase-0-spike.md             ← scratch-module compile check
phase-1-contributor-spi.md   ← RunContextContributor + GraphValidationRule
phase-2-module-split.md      ← new Maven module, package moves, rename sourcePersonId
phase-3-examples-readme.md   ← example steps, sample workflow JSON, README
```

A repo-decision record (`Docs/repo-decisions/RD-006-engine-as-standalone-library.md`) should accompany Phase 1 to capture the "why" once design choices are settled.

## Open questions (to resolve after unblock)

- Module layout: single `workflow-engine-core` artifact, or split `-api` (SPI only) and `-runtime` (Spring beans + JPA)?
- Trigger router: ship a generic `POST /triggers` REST entrypoint in the OSS artifact, or leave the entry-point entirely to the host?
- Persistence flavour: keep JPA, or offer a thinner JDBC implementation to reduce the Spring footprint?
- License: TBD.
