# Loop Primitive — Phased Engineering Plan

> Design decisions: see [README.md](README.md) decision sheet + [RD-009](../../../repo-decisions/RD-009-loop-primitive-foreman-cloned-rows.md).
> All twelve design calls were made by the owner 2026-06-10; this plan implements them, it does not reopen them.

## Architecture in one paragraph

A new control-family step type `loop` carries a **nested body sub-graph** in its config.
At runtime the loop node's row (the **foreman**) stays alive across the whole loop:
it stamps one lap at a time by **inserting fresh `workflow_run_steps` rows** for the
body's nodes with lap-suffixed ids (`call#2`), sleeps, and is **woken by a small engine
hook** when a lap reaches a dead end. Between laps it evaluates the mode's condition
(`while` / `until` / `forEach` items) against lap results, then stamps the next lap or
exits via normal result-code transitions (`SUCCEEDED` / `EXHAUSTED` / `LOOP_FAILED` /
`ZERO_LAPS`). All loop bookkeeping lives in the foreman's `step_state` (durable,
crash-safe). The static graph stays cycle-free; existing claim/retry/stale-recovery
machinery is reused unchanged.

## Key integration points (today's code)

| Mechanism | Where | Why it matters here |
|---|---|---|
| One row per (run, node_id), UNIQUE | `WorkflowRunStepEntity` (~:29) | Lap rows need fresh ids → `#N` suffix; constraint doubles as double-stamp guard |
| Poller claims `due_at <= now` | `WorkflowExecutionDueWorker`, `JdbcWorkflowRunStepClaimRepository` | Laps and foreman wake-ups ride the existing scheduler |
| Result codes → transitions | `WorkflowStepExecutionService.applyTransition` (~:295) | Loop exits are ordinary transitions; wake-hook lands here |
| Node lookup by id in snapshot | `findNodeInGraph` (~:422), `resolveStepDueAt` (~:377) | Must learn to resolve `call#2` → body node `call` (suffix strip + body descent) |
| Durable per-step pocket + reschedule | `step_state`, `scheduleReschedule` (~:530) | Foreman's notebook: lap counter, last-lap results, forEach cursor |
| Validator (10 rules incl. cycle ban) | `WorkflowGraphValidator` | Gains body recursion; cycle ban stays — loops never add graph edges |
| Run-completion sweep | `checkRunCompletion` (~:394) | Must not finalize a run while a foreman is alive (foreman row is non-terminal — verify + test) |

## Phases

Each phase is independently reviewable and lands behind tests; no phase requires a
later phase to be safe to merge. Phase docs follow the repo convention
(`phase-N-implementation.md` in this folder as each lands).

### Phase 0 — Step-type categories ([RD-010](../../../repo-decisions/RD-010-step-type-categories.md))
**Scope**
- `StepCategory` enum (`CONTROL` / `UTILITY` / `BUSINESS`) + `category()` on
  `WorkflowStepType`; override in all 13 existing step classes per the RD-010
  classification table.
- Expose category in the admin step-catalog endpoint DTO.
- Architecture test (CI): CONTROL and UTILITY step classes must not import
  host/FUB packages — the executable form of the RD-007 step boundary.
- Add a Category column to `steps/README.md` index.
**Done signal:** all steps tagged, catalog returns categories, boundary test green
in CI; zero behavior change.

### Phase 1 — Graph contract + validator
**Scope**
- Define the `loop` node JSON contract: `mode` (`while`|`until`|`forEach`), `condition`
  (JSONata, while/until), `items` + optional `itemVar`/`indexVar` (forEach),
  **mandatory `maxIterations`**, optional `lapDelayMinutes`, nested
  `body: { entryNode, nodes[] }`.
- Validator: recurse into bodies — unique ids (define scope: body-local, reject
  collisions with ancestor scope), known step types, config schemas, transitions
  resolve **within the body only** (no edges out; terminal markers forbidden inside
  bodies in v1), cycle check per body, body reachability from `body.entryNode`.
- Validator: forbid `#` in author node ids (reserve for lap suffixes).
- Validator: `maxIterations` required, integer ≥ 1, ≤ engine ceiling (config, default 100).
- Nested loops validate recursively (allowed day one).
**Done signal:** validator test suite covering accept/reject matrix; zero runtime change.

### Phase 2 — Engine substrate
**Scope**
- Suffix-aware node resolution: `call#2` (and nested `call#2#5`) resolves to its body
  node definition; descent into nested bodies. No-op for normal ids. Applied at the
  `findNodeInGraph` boundary so `applyTransition` / `resolveStepDueAt` inherit it.
- Wake-hook: when a step completes and is a body member whose lap has reached a dead
  end (no runnable/pending body rows for that lap), set the owning foreman's
  `due_at = now`. Generic, ~small, in the transition path; carries lap-tail result
  summary into foreman-readable state.
- Idempotent lap stamping helper: insert body rows for lap N in one transaction;
  on UNIQUE violation treat as already-stamped (swallow, mirroring the
  `WorkflowExecutionManager` idempotency-conflict pattern).
- `materializeSteps` skips body nodes (they materialize per lap, never at plan time).
**Done signal:** unit tests for resolution/stamping/wake; full existing test suite
green — existing workflows provably unaffected.

### Phase 3 — Foreman v1: `until` / `while`
**Scope**
- `LoopWorkflowStep` (control step): on first claim, validate runtime config, stamp
  lap 1 (`until`) or evaluate condition first (`while`, may exit `ZERO_LAPS`), record
  notebook in `step_state`, sleep (reschedule far-future; wake-hook shortens it).
- On wake: read lap-tail results, evaluate condition + `maxIterations`, stamp next lap
  (applying `lapDelayMinutes` to the lap's entry row `due_at`) or finish with
  `SUCCEEDED` / `EXHAUSTED`.
- Lap failure catch: body step permanent failure marks the lap failed and wakes the
  foreman instead of failing the run; foreman routes per config
  (`onLapFailure: CONTINUE | EXIT`, exit code `LOOP_FAILED`).
- Lean outputs: `lapCount`, `exitReason`, `lastLap` (result codes + outputs of the
  final lap). Lap-local sibling resolution in expression scope (`steps.call` inside a
  lap = this lap's `call#N`).
- Belt-and-suspenders: engine ceiling on laps (config) independent of author
  `maxIterations`.
**Done signal:** end-to-end tests — multi-step body, early exit, exhaustion, lap
failure both routes, app-restart mid-lap and mid-sleep recovery.

### Phase 4 — `forEach` (sequential)
**Scope**
- `items` JSONata evaluated once at loop start; cursor in notebook; `item`/`index`
  bound into each lap's expression scope; empty list → `ZERO_LAPS`; list size capped
  by `maxIterations` (validator + runtime).
**Done signal:** forEach e2e incl. empty list, item scope visibility, restart mid-list.

### Phase 5 — Nesting + conformance
**Scope**
- Foreman-in-foreman tests: double suffixes, inner-loop failure routing to outer lap,
  restart inside nested laps.
- Registry-wide conformance test: every registered step type executes inside a loop
  body (auto-generated minimal body per type) — the "any step, including future
  steps" invariant. New step types fail CI if they break in a body.
**Done signal:** conformance suite green and wired into CI.

### Phase 6 — Observability + docs
**Scope**
- Admin run-detail: group lap rows under their loop node, show `lap k / maxIterations`.
- Finalize [../steps/loop.md](../steps/loop.md) reference against shipped behavior.
- Update `how-the-engine-works.md` with a loop lifecycle section.
**Done signal:** MVP acceptance scenario authored + run end-to-end (see README);
run-detail screenshot in phase doc.

## Order & dependencies

0 → 1 → 2 → 3 → 4 → 5 → 6. Phase 0 is independent (pure additive tagging) and lands
first so the loop arrives pre-tagged CONTROL. Phase 4 depends only on 3; Phase 5
hardens 3+4; Phase 6 is shippable any time after 3 (UI part after 3, docs part
after 5).

## Risks & mid-flight detection signals

| Risk | Signal to watch | Mitigation |
|---|---|---|
| Double-stamped lap under crash/stale recovery | duplicate-key errors in logs on lap insert | UNIQUE constraint + swallow-as-success (Phase 2); recovery e2e test |
| Run finalized while foreman sleeps | run COMPLETED with foreman row non-terminal | foreman row stays PENDING/PROCESSING (non-terminal) → `checkRunCompletion` cannot finalize; explicit test |
| Wake-hook misses a lap end (foreman sleeps forever) | loops stuck with no due foreman | foreman's reschedule is a far-future *fallback poll*, not infinite — self-heals slowly; alert on foreman age |
| Suffix ambiguity with author ids | validator rejects `#` (Phase 1) | conformance test includes id-edge cases |
| Supersede/cancel mid-loop leaves orphan lap rows | cancelled runs with PENDING lap rows | terminal sweep already SKIPs PENDING/WAITING rows — verify covers lap rows; test in Phase 3 |
| JSONata condition errors at runtime | foreman wake fails | treat as lap-failure path (`LOOP_FAILED` route), never silent retry-forever |

## Validation criteria (feature-level)

- MVP acceptance scenario runs end-to-end (README).
- Kill-and-restart the app mid-lap, mid-sleep, and mid-stamp: loop resumes correctly
  in all three.
- Existing workflow test suite untouched and green after every phase.
- Conformance suite: all registered step types pass inside a body.
- A workflow with `maxIterations: 3` and a never-true condition exits `EXHAUSTED`
  after exactly 3 laps — no more rows, no hot-spin.

## Linked decisions

- **[RD-009](../../../repo-decisions/RD-009-loop-primitive-foreman-cloned-rows.md)** —
  loop architecture (foreman + inline cloned rows) — created with this plan.
- **[RD-010](../../../repo-decisions/RD-010-step-type-categories.md)** — step-type
  categories (CONTROL/UTILITY/BUSINESS) + CI boundary enforcement — implemented as
  Phase 0 of this plan.
- [RD-006](../../../repo-decisions/RD-006-engine-echo-exclusion-safe-by-default.md) —
  engine-echo gating applies unchanged to FUB-writing steps inside bodies.
- [RD-007](../../../repo-decisions/RD-007-engine-as-standalone-library.md) — the loop
  primitive is kernel code; keep it free of host-glue imports so the engine-extraction
  boundary stays clean (no new coupling beyond the verified list).
