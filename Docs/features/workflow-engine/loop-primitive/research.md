# Loop Primitive — Research

> Captures the 2026-06-10 fresh-eyes analysis that produced RD-009 (previously
> only in session transcript). Summarized; the binding decision text is RD-009.

## Question

The engine is a cycle-free DAG executor (validator rejects cycles; one row per
node per run; one-shot dependency-decrement joins). How should it gain a
reliable, configurable loop mechanism — as a platform primitive, not a
point-fix for one workflow?

## Five mechanisms evaluated against the code

| # | Mechanism | Verdict | Why |
|---|---|---|---|
| 1 | In-step self-reschedule (`scheduleReschedule` + `step_state`; the `ai_call` pattern) | ❌ for the primitive | Works today, zero engine change — but loops a *single step* only; no multi-node body, no per-lap observability beyond one row; a point-hack |
| 2 | Compile-time unroll (expand N laps into chained nodes at plan time) | ❌ (kept as fallback) | Zero new runtime code, fully reliable — but N frozen per snapshot, no condition-driven laps, no forEach |
| 3 | **Runtime expansion / per-lap cloned rows + foreman** | ✅ **chosen** | Reuses claim/due_at/retry/recovery wholesale; per-lap rows = audit + crash-safety; the Conductor `DO_WHILE` / Step Functions Map(inline) model |
| 4 | Real static-graph cycles (guarded back-edge + row reset) | ❌ | One-shot `pending_dependency_count` join silently kills a loop on lap 2; fixing means re-architecting the join model + history table for outputs |
| 5 | Self-re-trigger via new runs (cross-run iteration) | ❌ | Fights the idempotency-key shape and `RunSupersedePolicy` (attempt N+1 supersedes N); no cross-run counter exists |

## Industry survey (2026-06)

Declarative engines converge on first-class loop constructs with per-iteration
materialized state: Netflix Conductor `DO_WHILE` (loopOver body, `__i`-suffixed
task refs, outputs indexed by iteration; nesting via SUB_WORKFLOW), AWS Step
Functions `Map` (Inline: iterations in parent history; Distributed: child
executions), BPMN standard-loop + multi-instance markers. Imperative replay
engines (Temporal et al.) solve loops as code over an event-history substrate —
a different engine category, evaluated and rejected as a rewrite
("Option C").

## Driver decision (foreman vs engine-transition)

Foreman (loop node stays alive, drives laps from a pluggable step class +
minimal wake-hook) chosen over embedding loop logic in `applyTransition`:
smaller blast radius, failure isolation between body side effects and lap
bookkeeping (separate transactions), negligible latency cost (one poller hop
per lap vs laps that wait minutes by design).

## Stress-test (2026-06-10)

The plan (rev 1) was adversarially attacked by 4 independent reviewers across
24 iterations — architecture held, plan amended (rev 2):
[audit](../../../audits/loop-primitive-stress-test-2026-06-10.md).
