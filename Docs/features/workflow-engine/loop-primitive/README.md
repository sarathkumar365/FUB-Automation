# Loop Primitive — First-Class `loop` Control Step

> **Status:** Design complete + stress-tested (2026-06-10, plan rev 2). Phase 0 shipped; Phases 1–6 not started.
> **Entry point** for the loop-primitive feature. Plan: [plan.md](plan.md) · Tracker: [phases.md](phases.md) · Research: [research.md](research.md).
> Binding architecture decision: [RD-009](../../../repo-decisions/RD-009-loop-primitive-foreman-cloned-rows.md).
> Step reference (capabilities, config, deferred items): [../steps/loop.md](../steps/loop.md).
> Adversarial review: [stress-test audit](../../../audits/loop-primitive-stress-test-2026-06-10.md) — 24 attack iterations; architecture held, plan amended (rev 2).

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
| 11 | Nesting | ~~Allowed from day one~~ → **Deferred from v1** (revised 2026-06-10 after stress-test finding B4/D1.6 + estimate evidence; schema stays instance-keyed so enabling later is validator-only) |
| 12 | Concurrency | Sequential-only v1; parallel deferred (+2–3 wks later, no rework — see loop.md) |
| 13 | Lap-row ↔ loop relation | **Both**: `#N` id suffix (uniqueness backstop) **+** explicit **`parent_loop_step_id` FK (foreman instance row)** & `lap_number` columns — re-keyed from node-id to instance 2026-06-10 (stress-test B4: node-id collides across outer laps) |
| 14 | Supersede × loop | **Configurable `onSupersede: RESTART \| KEEP`** (default RESTART = today's engine behavior made explicit); MVP = RESTART + `loop.supersede.restarts` churn metric; carry-budget deferred — decided 2026-06-10 (stress-test C1) |
| 15 | v1 authoring surface | **JSON-only** (builder's flat-list model can't render nested bodies; 1–2 wks deferred); run-detail lap *viewing* in scope (Phase 6) — decided 2026-06-10 |

## Phase tracker

Moved to **[phases.md](phases.md)** (live tracker, per repo convention).
Summary: Phase 0 ✅; Phases 1–6 (+5.5 metrics) not started; re-baselined
~20–27 dev-days with nesting + builder deferred.

## Acceptance scenario

The MVP retry loop authored as real workflow JSON and run end-to-end:
*repeat (call → wait → check) until CONVERSATIONAL, max N (configurable), then
escalate to task-or-pond.* See
[`Docs/product-discovery/WORKFLOWS/MVP WF/MVP-WF.md`](../../../product-discovery/WORKFLOWS/MVP%20WF/MVP-WF.md).

## Explicit non-goals (v1)

- **Nested loops** — deferred 2026-06-10 (decision #11 rev); validator rejects; instance-keyed schema makes later enablement validator-only
- Parallel laps (`concurrency > 1`) — deferred, design is forward-compatible
- **Builder UI authoring** — JSON-only v1 (decision #15)
- `break` / `continue` from inside a body — deferred result-code convention
- Carry lap budget across supersede restarts — deferred pending `loop.supersede.restarts` metric evidence
- Child-run-per-lap execution — deferred (only if isolation/scale demands it)
- Waiting on external events between laps (`wait_for_event`) — separate feature
- Bounded step-execution executor — pre-existing engine work, tracked separately
