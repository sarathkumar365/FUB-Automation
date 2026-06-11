# `loop` — Reference

> **Status: ⏸️ SHELVED — not implemented.** Designed, stress-tested, and
> deliberately parked 2026-06-10 (single-step retry loops are covered by
> `wait_and_check_communication`'s built-in retry — Path A). On resume, read
> [loop-primitive/resume-briefing.md](../loop-primitive/resume-briefing.md)
> FIRST — this contract has known items to patch (e.g. the config sketch's
> body and `onSupersede` placement). Rev 2 (2026-06-10) after the
> [stress-test audit](../../../audits/loop-primitive-stress-test-2026-06-10.md).

Control-flow step. Repeats a **nested body sub-graph** — any steps, any number
of them — until a condition is met, a list is consumed, or a configurable lap
cap is reached. The loop node ("foreman") stays alive for the whole loop,
stamping fresh per-lap step rows (`call#1`, `call#2`, …) and deciding between
laps.

## At a glance

| | |
|---|---|
| **Step ID** | `loop` |
| **Class** | `LoopWorkflowStep` (planned) |
| **Family** | CONTROL ([RD-010](../../../repo-decisions/RD-010-step-type-categories.md)) |
| **Side effects** | None directly; body steps keep their own side effects |
| **Result codes** | `SUCCEEDED`, `EXHAUSTED`, `LOOP_FAILED`, `ZERO_LAPS` — **all success-typed** (see Semantics) |
| **Modes** | `while` (pre-checked), `until` (do-while), `forEach` (once per item) |
| **Body** | Nested sub-graph in config — multi-step, branches allowed |
| **Durability** | Per-lap rows + foreman `step_state`; crash/restart-safe |
| **Kill-switch** | `workflow.loop.enabled` (runtime); `workflow.loop.poke-enabled` (wake-hook lever/test hook) |

## Capabilities — v1 vs deferred

**This table is the memory.** Owner-decided; deferred ≠ rejected.

| Capability | v1 | Notes |
|---|---|---|
| Repeat a multi-step group | ✅ | Nested body sub-graph |
| `until` (do-while) — body runs ≥ once | ✅ | MVP retry shape |
| `while` — condition checked before lap 1 | ✅ | May exit `ZERO_LAPS` (must be wired — validator-enforced) |
| `forEach` over a JSONata list | ✅ | Sequential; list **pinned at loop start** (see Semantics) |
| Configurable cap (`maxIterations`, mandatory) | ✅ | 1..100 (engine ceiling) + total-rows-per-run ceiling |
| Wait between laps (`lapDelayMinutes`) | ✅ | Static integer; applies before laps 2..N only — lap 1 due immediately |
| Branches inside the body | ✅ | Untaken-path rows swept SKIPPED at lap end |
| Any step type in the body — incl. future ones | ✅ | Registry-wide conformance test |
| Lap failure caught by loop (run survives) | ✅ | `onLapFailure: CONTINUE \| EXIT`; rows get `LAP_FAILED` status (run-failure paths are lap-aware) |
| Supersede behavior configurable | ✅ | `onSupersede: RESTART \| KEEP` (decision #14); default RESTART; MVP = RESTART + churn metric |
| Lean outputs (lapCount, exitReason, lastLap) | ✅ | Per-lap history visible as rows, excluded from expressions |
| **Nested loops (loop inside a body)** | ⏳ **deferred** (2026-06-10, post stress-test) | Validator rejects in v1. Schema is already instance-keyed (`parent_loop_step_id`), so enabling later = remove the check + tests; no rework |
| **Parallel laps (`concurrency > 1`)** | ⏳ deferred | Foreman/cloned-rows design supports it; +2–3 wks when needed |
| **Builder UI authoring** | ⏳ deferred (decision #15) | v1 is JSON-only authoring; run-detail *viewing* groups laps (Phase 6) |
| `break` / `continue` from inside a body | ⏳ deferred | v1 exits are condition-only |
| Collected per-lap output history in expressions | ⏳ deferred | `collectResults` opt-in later |
| Carry lap budget across supersede restarts | ⏳ deferred | Needs cross-run counter; `loop.supersede.restarts` metric decides if it's worth it |
| Templated `lapDelayMinutes` / body delays | ⏳ deferred | v1 static integers (templated delays silently resolve to 0 in today's engine) |
| Child-run-per-lap isolation | ⏳ deferred | Only if scale/isolation demands it |
| Wait for external event between laps | ❌ separate feature | `wait_for_event` rendezvous |

## Config sketch

```json
{
  "id": "retry_contact",
  "type": "loop",
  "config": {
    "mode": "until",
    "condition": "lastLap.wait_check.resultCode = 'CONVERSATIONAL'",
    "maxIterations": 10,
    "lapDelayMinutes": 30,
    "onLapFailure": "EXIT",
    "onSupersede": "RESTART",
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

(`until` mode: `ZERO_LAPS` cannot fire and must not be wired. `while`/`forEach`
must additionally wire `ZERO_LAPS`. All modes must wire `LOOP_FAILED`.)

forEach variant: replace `condition` with `"items": "<jsonata returning array>"`
(+ optional `"itemVar"`/`"indexVar"`, defaults `item`/`index`).

## Semantics (summary — the plan's normative contract is binding)

- **Lap end:** a body path ends at a **declared dead end** — an explicit empty
  `transitions: {}` (or a code explicitly mapped to an empty list). An
  **unmatched result code is an error**, routed to the lap-failure path — never
  a silent lap end, never a direct run failure.
- **Condition scope (between laps):** `lap` (1-based), `item`/`index` (forEach),
  `lastLap.<bodyNodeId>.{resultCode, outputs.*}`, plus `person.* / now.* /
  event.*`. **`steps.*` is not available in conditions** (validator-rejected).
  `lastLap` contains only nodes that **ran in that lap**; a node skipped by a
  branch is absent (JSONata absent → falsy — write conditions accordingly).
- **Inside-lap scope:** `steps.<bodyNodeId>` resolves lap-locally. Lap rows are
  excluded from all other steps' expression scope — per-lap history is rows,
  not expressions.
- **Exit codes are success-typed.** The foreman catches its own errors
  (JSONata, bookkeeping) and exits `LOOP_FAILED`; only an uncaught exception
  falls back to engine-default run failure.
- **forEach freeze:** `items` is evaluated once at loop start and pinned into
  the foreman's `step_state`; laps and crash-recovery read the pinned list.
  `person.*` stays live per step — do not assume `item` still exists on the
  person at lap time.
- **Supersede (`onSupersede`):** `RESTART` (default) = newest event cancels the
  in-flight loop and starts fresh (lap budget resets — monitored via
  `loop.supersede.restarts`); `KEEP` = a run with a live foreman is exempt from
  supersession.
- **Downstream outputs:** `steps.<loopId>.outputs.lapCount`, `.exitReason`,
  `.lastLap.*`.

## Safety

- `maxIterations` mandatory (1..engine ceiling) + total-materialized-rows
  ceiling — a loop can never be authored unbounded.
- Lap stamping: pre-check SELECT inside the foreman's transaction;
  `UNIQUE(run_id, node_id)` as backstop. Crash/stale-recovery cannot double-run
  a lap (S9/S19).
- The wake-hook ("poke") is a guarded JDBC UPDATE, fires unconditionally per
  body-tail completion; the foreman does the authoritative lap-end count.
  Foreman re-sleep uses a min-wins `due_at` write so a poke is never lost.
- Foreman bookkeeping and body side effects commit in separate transactions —
  a stamping failure can never re-fire a body step's external action.
- Run-failure paths are lap-aware: a failed lap row becomes `LAP_FAILED`,
  wakes the foreman, and never fails the run directly.
- `#` reserved in node ids; nested loops rejected in v1.
- Kill-switch: `workflow.loop.enabled=false` → foremen stop stamping new laps
  and exit `LOOP_FAILED`; in-flight lap rows drain normally.
