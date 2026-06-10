# `loop` — Reference

> **Status: PLANNED — not yet implemented.** Contract defined by
> [loop-primitive/plan.md](../loop-primitive/plan.md); this doc is written ahead of
> code so capabilities (and deliberate v1 limits) are recorded. Update each section
> to match shipped behavior as phases land.

Control-flow step. Repeats a **nested body sub-graph** — any steps, any number of
them — until a condition is met, a list is consumed, or a configurable lap cap is
reached. The loop node ("foreman") stays alive for the whole loop, stamping fresh
per-lap step rows (`call#1`, `call#2`, …) and deciding between laps.

## At a glance

| | |
|---|---|
| **Step ID** | `loop` |
| **Class** | `LoopWorkflowStep` (planned) |
| **Family** | Control step — runs other steps; no business action of its own |
| **Side effects** | None directly; body steps keep their own side effects |
| **Result codes** | `SUCCEEDED`, `EXHAUSTED`, `LOOP_FAILED`, `ZERO_LAPS` (+ fixed config-failure codes) |
| **Modes** | `while` (pre-checked), `until` (do-while), `forEach` (once per item) |
| **Body** | Nested sub-graph in config — multi-step, branches allowed |
| **Durability** | Per-lap rows + foreman `step_state`; fully crash/restart-safe |
| **Retry policy** | Body steps: their own policies, per lap. Foreman bookkeeping: `NO_RETRY` semantics — errors route to `LOOP_FAILED` |

## Capabilities — v1 vs deferred

**This table is the memory.** Decisions made 2026-06-10 (owner: Sarath); deferred ≠ rejected.

| Capability | v1 | Notes |
|---|---|---|
| Repeat a multi-step group | ✅ | Nested body sub-graph |
| `until` (do-while) — body runs ≥ once | ✅ | MVP retry shape |
| `while` — condition checked before lap 1 | ✅ | May exit `ZERO_LAPS` |
| `forEach` over a JSONata list | ✅ | Sequential, `item`/`index` in scope |
| Configurable cap (`maxIterations`, mandatory) | ✅ | Plus engine-level hard ceiling |
| Wait between laps (`lapDelayMinutes`) | ✅ | Rides `due_at`; waits of days are fine |
| Branches inside the body | ✅ | Any dead end ends the lap |
| **Any step type in the body — incl. future ones** | ✅ | Guaranteed by registry-wide conformance test, not by a support list |
| Lap failure caught by loop (run survives) | ✅ | `onLapFailure: CONTINUE \| EXIT` → `LOOP_FAILED` route |
| Nested loops (loop inside a body) | ✅ | Double-suffixed lap rows (`call#2#5`) |
| Lean outputs (lapCount, exitReason, lastLap) | ✅ | Per-lap history visible as rows, not in expressions |
| **Parallel laps (`concurrency > 1`)** | ⏳ deferred | Foreman/cloned-rows design supports it without rework; +2–3 wks when needed (join bookkeeping, partial-failure semantics, per-item results contract) |
| **`break` / `continue` from inside a body** | ⏳ deferred | Planned as reserved transition targets; v1 exits are condition-only |
| Collected per-lap output history in expressions | ⏳ deferred | `collectResults` opt-in flag; lean-by-default chosen to prevent bloat |
| Child-run-per-lap isolation | ⏳ deferred | Only if scale/nesting-isolation demands it |
| Wait for external event between laps | ❌ separate feature | `wait_for_event` rendezvous — different infrastructure |

## Config sketch

```json
{
  "id": "retry_contact",
  "type": "loop",
  "config": {
    "mode": "until",
    "condition": "steps.wait_check.resultCode = 'CONVERSATIONAL'",
    "maxIterations": 10,
    "lapDelayMinutes": 30,
    "onLapFailure": "EXIT",
    "body": {
      "entryNode": "call",
      "nodes": [
        { "id": "call",       "type": "ai_call", "config": { "...": "..." },
          "transitions": { "DONE": ["wait_check"] } },
        { "id": "wait_check", "type": "wait_and_check_communication",
          "config": { "delayMinutes": 3, "lookbackMinutes": 5 },
          "transitions": {} }
      ]
    }
  },
  "transitions": {
    "SUCCEEDED":   ["engaged"],
    "EXHAUSTED":   ["create_task"],
    "LOOP_FAILED": ["create_task"]
  }
}
```

forEach variant: replace `condition` with `"items": "<jsonata returning array>"`
(+ optional `"itemVar"`/`"indexVar"`, defaults `item`/`index`).

## Semantics

- **Lap end:** a lap finishes when its path reaches a body node with no onward
  transition (any dead end). Bodies cannot transition out of themselves or declare
  run-terminal markers (validator-enforced, v1).
- **Condition scope (between laps):** `lap` (number, 1-based), `lastLap.<nodeId>`
  (that lap's result codes/outputs), plus the normal `person.*` / `now.*` / `event.*`.
  forEach adds `item` / `index` inside lap scope.
- **Inside-lap scope:** `steps.<bodyNodeId>` resolves **lap-locally** (this lap's
  clone), so body steps reference siblings naturally.
- **Exit codes:** `SUCCEEDED` (condition met / list consumed), `EXHAUSTED`
  (`maxIterations` hit), `LOOP_FAILED` (lap failed with `onLapFailure: EXIT`, or
  bookkeeping/condition error), `ZERO_LAPS` (`while` false at start / empty forEach list).
- **Downstream outputs:** `steps.<loopId>.outputs.lapCount`, `.exitReason`,
  `.lastLap.*`.

## Safety

- `maxIterations` is **mandatory** (validator) and bounded by an engine ceiling
  (config, default 100) — a loop can never be authored unbounded.
- Lap stamping is idempotent (UNIQUE on `(run_id, node_id)` + swallow-on-conflict):
  crash/stale-recovery cannot double-run a lap.
- Foreman bookkeeping and body side effects commit in **separate transactions**:
  a stamping failure can never re-fire a body step's external action.
- `#` is reserved in node ids for lap suffixes (validator rejects author use).
