# Multi-Event Triggers — Implementation Plan

> Single-phase (small, additive, no schema change). Status: PLANNED, not approved.
> Code grounding verified 2026-06-11 against `feature/multi-event-triggers` (= dev).

## Contract (binding)

### Accepted trigger shapes

1. **Flat (existing, unchanged):**
   `{ "on": "<kind>", "filter": "<jsonata?>", "reactToEngineEvents": <bool?> }`
2. **Multi (new):**
   `{ "anyOf": [ { "on": "<kind>", "filter": "<jsonata?>" }, ... ], "reactToEngineEvents": <bool?> }`

### Rules

| # | Rule | Rationale |
|---|---|---|
| C1 | `on` XOR `anyOf` — exactly one present; both or neither = validation error | unambiguous shape detection |
| C2 | `anyOf` must be a non-empty array of objects; each entry: required valid `on` (known-kind catalog), optional non-blank `filter` | mirrors flat rules per entry |
| C3 | **Duplicate kinds across entries rejected** | OR-of-filters for one kind is expressible inside a single filter; duplicates would make entry order semantically load-bearing |
| C4 | `reactToEngineEvents` allowed **top-level only**; inside an entry = validation error | RD-006's echo gate is evaluated per-trigger before matching — per-entry would be unenforceable ambiguity |
| C5 | Each entry's filter is field-checked against **its own** kind's scope (existing per-kind rules: `change.*` only on `person.state_changed`, `current.*` only on person kinds) | preserves the validator's silent-no-fire guarantee — the reason Shape B was chosen over a shared filter |
| C6 | Match semantics: workflow fires if **any** entry matches (kind equal AND filter absent-or-truthy). Entries evaluated in array order; first match suffices | boolean outcome — order has no behavioral effect except error handling (C7) |
| C7 | An entry whose filter **errors at runtime** counts as non-match (WARN log), remaining entries still evaluated | one bad entry must not veto its siblings; mirrors the router's existing per-workflow error tolerance |
| C8 | The expression scope is built **once per (event, workflow)** and shared across entries | scope build resolves the person snapshot (DB read) — per-entry rebuilds would multiply reads for zero benefit |

## Code changes (verified integration points)

| # | Change | Where | Notes |
|---|---|---|---|
| 1 | **Shared trigger-shape helper** — parse a trigger map into `List<TriggerEntry(on, filter)>` (flat shape → single-entry list) + `subscribesTo(trigger, kind)` | new package-private class in `service/workflow/trigger/` | single source of shape truth; both runtime call sites use it so they cannot drift |
| 2 | `matches(event, config)` iterates entries: kind match → lazy-build scope once (C8) → evaluate entry filter (C7 per-entry catch) | `DomainEventTriggerType.java:47-62` (current single-string logic at :51-54 replaced) | public behavior for flat triggers byte-for-byte identical |
| 3 | Router pre-check uses the helper instead of `String.valueOf(trigger.get("on")).equals(...)` | `WorkflowTriggerRouter.java:84-90` | keeps the cheap pre-filter before the echo gate; echo gate itself untouched (kind-agnostic, top-level) |
| 4 | Validator: shape rules C1–C4; refactor the existing body (:67-127) into `validateEntry(on, filter, errors)`; flat path calls it once, `anyOf` path per entry | `DomainEventTriggerValidator.java` | the per-kind scope checks (:120-127) move into `validateEntry` unchanged — C5 falls out of the refactor |
| 5 | Admin catalog: trigger-shape doc string gains the `anyOf` form | `AdminWorkflowController.getTriggerTypes()` | string-level |
| 6 | `create-workflow-json` skill: document `anyOf` + when to use it | `.claude/skills/create-workflow-json/SKILL.md` | authoring guidance |

**No schema change** (triggers are JSONB in `automation_workflows.trigger`), no
migration, no new properties, no UI code (admin UI renders trigger JSON as-is;
form-based trigger editing for `anyOf` is an explicit non-goal).

## Test matrix

**Validator (unit):**
- V1 flat trigger valid → unchanged acceptance (regression)
- V2 `anyOf` two valid entries → accepted
- V3 both `on` and `anyOf` → rejected (C1)
- V4 empty `anyOf` / non-array / entry missing `on` / unknown kind in entry → rejected (C2)
- V5 duplicate kinds in entries → rejected (C3)
- V6 `reactToEngineEvents` inside an entry → rejected (C4)
- V7 entry filter uses `change.*` with `on=person.created` → rejected; same filter under the `person.state_changed` entry → accepted (C5 — the Shape-B guarantee)

**Trigger type (unit):**
- T1 flat trigger behavior unchanged (regression)
- T2 `anyOf`: event matches first entry / second entry / neither
- T3 entry filter runtime error → that entry non-match, sibling entry still matches (C7)
- T4 scope built once across two filter-bearing entries (verify single `PersonSnapshotResolver.resolve` invocation — C8)

**Router + e2e (integration):**
- R1 one workflow with the MVP `anyOf` trigger: a `person.created` event with pre-set assignment plans a run; a `person.state_changed` with `assignedUserId` change plans a run; a `state_changed` with only `contacted` change does not (the Rita repro, both directions)
- R2 supersede across entry paths: created-assigned run in flight → reassignment event → same-key new run supersedes the old (the correctness bonus, proven)
- R3 echo-gated event (`origin=ENGINE`, no opt-in) skipped before any entry evaluation

## Order of work

1. Helper + trigger-type change + unit tests (T1–T4)
2. Validator refactor + unit tests (V1–V7)
3. Router change + integration tests (R1–R3)
4. Docstrings + skill update
5. Full suite green (`./mvnw clean test` — repo policy)

One PR-sized unit; each step compiles green independently.

## Risks & detection

| Risk | Signal | Mitigation |
|---|---|---|
| Behavior drift for existing flat triggers | any existing trigger test fails | flat path delegates to the same helper + `validateEntry`; regression tests V1/T1 pinned first |
| Router/trigger-type kind-check drift (the pre-existing duplication) | — | both call sites use helper #1; the duplication is removed, not extended |
| Filter valid for kind A leaking into kind B evaluation | silent non-match in prod | prevented at save time (C5/V7); runtime undefined → falsy is the safe direction |
| Admin UI rendering of `anyOf` triggers | workflow detail view breaks on unexpected shape | verify render of an `anyOf` workflow in the run/workflow views during R1; JSON passthrough expected — confirm, don't assume |

## Non-goals

- AND-composition of events, debounce/correlation across events (different feature)
- Per-entry `reactToEngineEvents` (C4)
- UI form-based authoring of `anyOf` (JSON authoring; builder follow-up if ever needed)
- Updating `agent_followup_enforcement_mvp`'s trigger — **separate follow-up step
  after merge** (admin API, new version, Rita-replay verification), deliberately
  not bundled with the engine change

## Validation criteria (feature-level)

- Full suite green; V/T/R matrix green
- A live `anyOf` workflow demonstrably fires from both event kinds in the
  local environment (Rita-style replay), and flat-trigger workflows behave
  identically to before
- Effort check: ~1–1.5 dev-days incl. tests (verified against actual code 2026-06-11)

## Linked decisions

- [RD-006](../../repo-decisions/RD-006-engine-echo-exclusion-safe-by-default.md) — echo gate stays top-level, per-trigger (C4)
- No new RD needed: additive config shape, no architectural boundary moved
  (helper lives in the existing trigger package; RD-007 kernel boundary untouched)
