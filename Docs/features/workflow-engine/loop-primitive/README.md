# Loop Primitive — First-Class `loop` Control Step

> **Status:** Planned (design complete 2026-06-10). No code yet.
> **Entry point** for the loop-primitive feature. Plan: [plan.md](plan.md).
> Binding architecture decision: [RD-009](../../../repo-decisions/RD-009-loop-primitive-foreman-cloned-rows.md).
> Step reference (capabilities, config, deferred items): [../steps/loop.md](../steps/loop.md).

## Why

The engine is a cycle-free DAG executor — it has no way to express "repeat this group
of steps." The MVP workflow (retry contact up to N times) needs it, and the platform
will keep needing reliable iteration. Rather than a per-step hack, we build the
industry-standard primitive: a first-class loop construct with per-lap materialized
state (the Conductor `DO_WHILE` / Step Functions `Map` model).

Fresh-eyes analysis of alternatives (in-step reschedule, static-graph cycles,
compile-time unroll, self-re-trigger runs) was done 2026-06-10 in-session; the
foreman + inline-cloned-rows model won on safety, observability, and generality.
Rationale captured in RD-009.

## Design decision sheet (owner: Sarath, 2026-06-10)

| # | Decision | Call |
|---|---|---|
| 1 | Platform direction | First-class loop primitive — not reschedule hack, not engine rewrite |
| 2 | Authoring shape | One `loop` control step type, three modes: `while`, `until` (do-while), `forEach` |
| 3 | Body | Multi-step group, declared **nested** inside the loop node's config |
| 4 | Lap mechanics | **Inline cloned rows** per lap (`call#2`) — one run holds the whole story |
| 5 | Driver | **Foreman** — loop node stays alive, drives laps via durable `step_state` + tiny engine wake-hook |
| 6 | Lap end | Any dead end inside the body ends the lap |
| 7 | Early exit | Condition-only in v1 (no break/continue — clean later addition) |
| 8 | Step compatibility | Any step type, current & future — enforced by registry-wide conformance test |
| 9 | Lap failure | Foreman catches it — author routes via `LOOP_FAILED`; run survives |
| 10 | Outputs | Lean: lap count, exit reason, last lap's outputs (+ `item`/`index` in forEach scope) |
| 11 | Nesting | Allowed from day one |
| 12 | Concurrency | Sequential-only v1; parallel deferred (+2–3 wks later, no rework — see loop.md) |

## Phase tracker

| Phase | Scope | Status |
|---|---|---|
| 0 | Step-type categories (CONTROL/UTILITY/BUSINESS + CI boundary test, [RD-010](../../../repo-decisions/RD-010-step-type-categories.md)) | ✅ COMPLETED ([notes](phase-0-implementation.md)) |
| 1 | Graph contract + validator (nested body, modes, `maxIterations`, id rules) | NOT STARTED |
| 2 | Engine substrate (suffix-aware lookup, wake-hook, idempotent lap stamping) | NOT STARTED |
| 3 | Foreman v1: `until`/`while` (lap lifecycle, failure-catch, lean outputs) | NOT STARTED |
| 4 | `forEach` sequential (items expression, `item`/`index` scope, empty list) | NOT STARTED |
| 5 | Nesting + registry-wide conformance suite | NOT STARTED |
| 6 | Observability + docs (admin lap grouping, step reference) | NOT STARTED |

## Acceptance scenario

The MVP retry loop authored as real workflow JSON and run end-to-end:
*repeat (call → wait → check) until CONVERSATIONAL, max N (configurable), then
escalate to task-or-pond.* See
[`Docs/product-discovery/WORKFLOWS/MVP WF/MVP-WF.md`](../../../product-discovery/WORKFLOWS/MVP%20WF/MVP-WF.md).

## Explicit non-goals (v1)

- Parallel laps (`concurrency > 1`) — deferred, design is forward-compatible
- `break` / `continue` from inside a body — deferred result-code convention
- Child-run-per-lap execution — deferred (only if isolation/scale demands it)
- Waiting on external events between laps (`wait_for_event`) — separate feature
