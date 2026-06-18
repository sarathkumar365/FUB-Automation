# Multi-Event Triggers — Phases

Status tracker for the `anyOf` trigger feature. Design + contract: [plan.md](plan.md).

| Phase | Title | Status |
|---|---|---|
| 1 | Shape authority + matcher | ✅ Done |
| 2 | Validator | ✅ Done |
| 3 | Router + integration | ☐ Not started |
| 4 | Catalog/docs + MVP repoint | ☐ Not started |

## Phase definitions

### Phase 1 — Shape authority + matcher
- New `service/workflow/trigger/ParsedTrigger.java` (record) + `TriggerEntry.java` (record), package-private.
- `from(Map)`: flat `{on,filter}` → one entry; `{anyOf:[...]}` → N entries. Methods: `entries()`, `subscribesTo(kind)`, `shapeErrors()` (pure C1–C4, no infra).
- `DomainEventTriggerType.matches()`: iterate `entries()`, build scope once lazily (C8), per-entry filter eval in per-entry try/catch (C7), first match wins. Flat unchanged.
- Tests: `ParsedTriggerTest` (shape parse); `DomainEventTriggerTypeTest` T1–T4.
- Done: unit green; flat behaviour identical. anyOf dormant (not yet saveable/routable).
- Repo decisions impact: No.
- Implementation notes: [phase-1-implementation.md](phase-1-implementation.md)

### Phase 2 — Validator
- `DomainEventTriggerValidator.validate()`: prepend `ParsedTrigger.shapeErrors()` (C1–C4); refactor body into `validateEntry(on, filter, errors)`; flat once, anyOf per entry; per-kind scope checks unchanged (C5). `reactToEngineEvents` stays top-level.
- SPI call-sites unchanged (`AutomationWorkflowService:284,346`).
- Tests: `DomainEventTriggerValidatorTest` V1–V7.
- Done: validator green; flat validates identically.
- Dependency: anyOf still not routable until Phase 3.
- Repo decisions impact: No.
- Implementation notes: [phase-2-implementation.md](phase-2-implementation.md)

### Phase 3 — Router + integration
- `WorkflowTriggerRouter.route()`: replace `:81` null-`on` skip + `:85` kind equality with `ParsedTrigger.from(trigger)` → skip if no entries; `subscribesTo(event.eventKind())`. Echo gate (`:89`) + `matches()` try/catch (`:97`) untouched.
- Tests: `WorkflowTriggerRouterTest` / `WorkflowTriggerRouterIntegrationTest` R1–R3; `person-21007-*` replay fixture for R2 (supersede across entry paths).
- Done: anyOf live end-to-end; `./mvnw clean test` green.
- Repo decisions impact: No.

### Phase 4 — Catalog/docs + MVP repoint
- `AdminWorkflowController.getTriggerTypes()`: document the `anyOf` shape.
- `.claude/skills/create-workflow-json/SKILL.md`: `anyOf` authoring guidance.
- Repoint `agent_followup_enforcement_mvp` trigger to `anyOf` (admin API new version + `mvp-wf.workflow.json`); verify the Rita repro/replay.
- Confirm the admin UI renders an `anyOf` trigger (JSON passthrough — verify, don't assume).
- Done: catalog shows `anyOf`; MVP fires for created-assigned + reassigned; suite green.
- Repo decisions impact: No.
