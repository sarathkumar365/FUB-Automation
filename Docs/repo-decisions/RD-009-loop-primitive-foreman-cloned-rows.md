# RD-009: Loops are a first-class control step — foreman-driven, inline cloned lap rows

## Status
Accepted (2026-06-10). Implementation planned in
[`Docs/features/workflow-engine/loop-primitive/`](../features/workflow-engine/loop-primitive/);
no code yet.

## Context
The engine is a cycle-free DAG executor: the validator rejects cycles, runs
materialize exactly one step row per node (`UNIQUE(run_id, node_id)`), and the join
model (`pending_dependency_count`) decrements exactly once per edge per run. There is
no way to express "repeat this group of steps." The MVP workflow needs a bounded
retry loop, and the platform will keep needing reliable iteration (retry-until,
for-each).

A fresh feasibility analysis (2026-06-10) evaluated five mechanisms against the code:

1. **In-step self-reschedule** (`scheduleReschedule` + `step_state`, the `ai_call`
   pattern) — works today but loops a *single step only*; a point-hack, not a primitive.
2. **Compile-time unroll** (expand N laps into chained nodes at plan time) — reliable,
   zero new runtime code, but N frozen per snapshot and no true condition-driven laps.
3. **Runtime expansion / cloned rows** — per-lap rows with suffixed ids; the
   Conductor `DO_WHILE` / Step Functions `Map`(inline) model.
4. **Real static-graph cycles** — rejected: the one-shot dependency-decrement join
   silently kills a loop on lap 2; fixing it means re-architecting the join model,
   plus single-valued outputs destroy per-lap audit.
5. **Self-re-trigger via new runs** — rejected for the primitive: fights the
   idempotency-key shape and `RunSupersedePolicy` (attempt N+1 would supersede
   attempt N); no cross-run counter exists.

Industry-wide, declarative engines converged on **first-class loop constructs with
per-iteration materialized state** (Conductor `DO_WHILE`, Step Functions `Map`,
BPMN loop/multi-instance markers). Imperative replay engines (Temporal) solve it
differently but require a different execution substrate entirely — out of scope.

## Decision
Build a first-class **`loop` control step** with these properties (full decision
sheet in the [feature README](../features/workflow-engine/loop-primitive/README.md)):

1. **Body is a nested sub-graph** inside the loop node's config — structurally
   impossible to mis-wire from the main flow; nesting-ready; matches industry shape.
2. **Each lap stamps fresh step rows inline in the same run** with lap-suffixed ids
   (`call#2`, nested `call#2#5`). The static graph stays cycle-free; the
   `UNIQUE(run_id, node_id)` constraint doubles as the double-stamp idempotency guard.
3. **A foreman drives the loop**: the loop node's own row stays alive (non-terminal)
   across the loop, holding all bookkeeping in its durable `step_state`. The engine
   core gains only a small generic wake-hook ("body lap reached a dead end → set the
   foreman's `due_at = now`"). Loop *logic* lives in the pluggable step class, not in
   `applyTransition`.
4. **Foreman bookkeeping and body side effects commit in separate transactions** —
   chosen explicitly so a lap-stamping failure can never re-fire a body step's
   external action (the decisive safety argument against driving loops from inside
   the engine's transition path).
5. **Bounded by construction**: `maxIterations` is validator-mandatory and capped by
   an engine-level ceiling; lap waits ride `due_at` (no hot-spin).
6. **Universal body compatibility is a tested invariant**: the foreman depends only
   on the `WorkflowStepType` contract; a registry-wide conformance test proves every
   registered step type (current and future) executes inside a body.

v1 scope: modes `while`/`until`/`forEach`, sequential laps, lap-failure catch, lean
outputs, nesting allowed. Deferred (not rejected): parallel laps, `break`/`continue`,
collected lap history, child-run-per-lap. The deferred ledger lives in
[`steps/loop.md`](../features/workflow-engine/steps/loop.md).

## Consequences
- Rejected mechanisms (static cycles, self-re-trigger) must not be reintroduced ad
  hoc; iteration goes through the loop primitive.
- The `#` character becomes reserved in node ids (lap-suffix namespace) — validator
  enforces.
- `materializeSteps` no longer materializes every graph node (body nodes materialize
  per lap) — the first deliberate exception to plan-time-only row creation.
- The engine-extraction boundary ([RD-007](RD-007-engine-as-standalone-library.md))
  applies: the loop primitive is kernel code and must not add host-glue coupling.
- Parallel laps, when needed, are an additive follow-up on this same model (foreman
  stamps K laps and joins) — no rework expected; this is the explicit forward path.
