# Workflow Engine — Fresh-Eyes Test Audit

**Author:** Test engineer (fresh eyes, no prior involvement in the engine)
**Date:** 2026-05-31
**Branch:** `feature/domain-events`
**Scope:** Whole-system end-to-end **plus** follow-the-risk on the workflow execution engine
(graph materialization → due-worker claim → step execution → transition/join/terminal handling →
run finalization), with the multi-path (fan-out / branch / join) machinery as the deepest dig.

## Ground rules followed

- **Expose + document, do not fix.** Zero production code was touched. Every test asserts the
  engine's *correct* expected behavior. Where a test fails because of a confirmed defect, it is
  isolated with `@Disabled("BUG-NN: …")` + `@Tag("bug")` so the **main build stays green**, and a
  GREEN *characterization* test alongside it locks in the current (defective) behavior as an
  executable reproduction.
- All new tests live in one class to amortize Spring/Testcontainers startup:
  `src/test/java/com/flux/service/workflow/WorkflowEngineBugHuntTest.java`.
- `./mvnw clean test` was green before and after this work (the new failing assertions are
  `@Disabled`).

## How to reproduce

```bash
# Normal suite (green): disabled bug-assertions are skipped
./mvnw test -Dtest=WorkflowEngineBugHuntTest

# Prove the defects: enable the disabled correct-behavior assertions and watch them fail
./mvnw test -Dtest=WorkflowEngineBugHuntTest \
  -Djunit.jupiter.conditions.deactivate='org.junit.jupiter.engine.extension.DisabledCondition'
```

The second command currently fails 2 assertions (BUG-01, BUG-02) — that is the proof.

---

## Findings summary

| ID | Title | Severity | Status vs known-issues | Reproducing test |
|----|-------|----------|------------------------|------------------|
| BUG-01 | Conditional branch + join deadlocks the run forever (`PENDING`) | High | **Confirms known issue #13** (was Open, untested) | `conditionalBranchJoinShouldReachTerminalState` (disabled) + `conditionalBranchJoinCurrentlyDeadlocksPending` (green) |
| BUG-02 | Invalid `branch_on_field` expression silently routes to `defaultResultCode` | High | **Confirms known issue #10** (was Open, untested) | `invalidBranchExpressionShouldFailLoudly` (disabled) + `invalidBranchExpressionCurrentlyRoutesToDefault` (green) |
| BUG-03 | A terminal in one parallel branch silently SKIPS slower sibling branches | High | **Novel** (distinct from #13) | `parallelTerminalSilentlySkipsSlowerSibling` (green) |
| BUG-04 | Two eventId-less triggers for the same person collapse to one run | Medium | **Novel** (inverse hazard of #24) | `noEventIdTriggersCollapseToSingleRun` (green) |
| — | Positive control: unconditional fan-out + join *does* complete | — | Isolates BUG-01 to the conditional case | `unconditionalFanOutWithJoinCompletes` (green) |

**Honesty note.** BUG-01 and BUG-02 are pre-existing entries in
`Docs/engineering-reference/known-issues.md` (#13 and #10). Both were marked *Open* and had **no
executable reproduction** in the test suite. The contribution here is to turn that prose into
deterministic, regression-grade tests, and to sharpen the mechanism. BUG-03 and BUG-04 are not
present in the 28-row known-issues table.

---

## BUG-01 — Conditional branch + join deadlocks the run (High) — confirms #13

**Mechanism.**
`WorkflowExecutionManager.buildPredecessorMap` ([WorkflowExecutionManager.java:174](../../src/main/java/com/flux/service/workflow/WorkflowExecutionManager.java#L174))
statically counts **every** incoming edge across **all** result codes of a node. A join node that
is the target of two branch arms is therefore materialized with `pendingDependencyCount = 2`
([WorkflowExecutionManager.java:164](../../src/main/java/com/flux/service/workflow/WorkflowExecutionManager.java#L164)).

At runtime a `branch_on_field` entry takes exactly **one** arm, so
`WorkflowStepExecutionService.activateNextNodes`
([WorkflowStepExecutionService.java:354](../../src/main/java/com/flux/service/workflow/WorkflowStepExecutionService.java#L354))
decrements the join exactly **once** (2 → 1). The join never reaches 0, stays
`WAITING_DEPENDENCY`, and the unselected branch also stays `WAITING_DEPENDENCY` forever.
`checkRunCompletion` ([:394](../../src/main/java/com/flux/service/workflow/WorkflowStepExecutionService.java#L394))
sees non-terminal steps and never finalizes; the worker has no claimable `PENDING` steps. The run
hangs in `PENDING` indefinitely.

**Graph used (entry routes LEFT *or* RIGHT; both converge on `join`):**
`branch ──LEFT──▶ leftAction ─▶ join ─▶ terminal`, `branch ──RIGHT──▶ rightAction ─▶ join`.

**Expected:** run reaches a terminal state (COMPLETED) via the taken path.
**Actual:** run stuck `PENDING`; `join` stranded at `pendingDependencyCount=1`; `rightAction`
stranded `WAITING_DEPENDENCY`. (Proven green by the characterization test; the disabled correct-
behavior test fails with `expected: not equal but was: <PENDING>`.)

**Positive control:** `unconditionalFanOutWithJoinCompletes` runs the same diamond with an
*unconditional* fan-out (`a ─▶ [b, c]`, both ─▶ `join`). Both predecessors fire, the join reaches
0, the run COMPLETES — confirming the engine's AND-join is correct and the defect is specific to
*conditional* (OR) fan-in.

**Fix direction (not applied):** the join's required-predecessor count must reflect the arms that
can actually fire (e.g., OR-join semantics / activation-based counting), or a "no remaining
reachable work" sweep must finalize runs that can no longer progress.

## BUG-02 — Invalid `branch_on_field` expression silently routes to default (High) — confirms #10

**Mechanism.**
`JsonataExpressionEvaluator.evaluateExpression`
([JsonataExpressionEvaluator.java:53](../../src/main/java/com/flux/service/workflow/expression/JsonataExpressionEvaluator.java#L53))
catches **all** exceptions and returns `null`. In a branch step,
`BranchOnFieldWorkflowStep.executeExpressionMode`
([BranchOnFieldWorkflowStep.java:129](../../src/main/java/com/flux/service/workflow/steps/BranchOnFieldWorkflowStep.java#L129))
stringifies that `null` to `"null"`, which is absent from `resultMapping`, so `routeResult`
([:170](../../src/main/java/com/flux/service/workflow/steps/BranchOnFieldWorkflowStep.java#L170))
falls through to `defaultResultCode` and reports **SUCCESS**.

**Sharper observation (adds to #10):** because the evaluator never throws, the
`catch (RuntimeException) → EXPRESSION_EVAL_ERROR` branch at
[BranchOnFieldWorkflowStep.java:134-138](../../src/main/java/com/flux/service/workflow/steps/BranchOnFieldWorkflowStep.java#L134)
is **dead code** for malformed JSONata. A typo in a routing predicate can never surface as a
failure — it always degrades to the default route.

**Expected:** a malformed expression FAILs the step (`EXPRESSION_EVAL_ERROR`).
**Actual:** step COMPLETED on `FALLBACK`. (Disabled correct-behavior test fails with
`expected: <FAILED> but was: <COMPLETED>`.)

**Fix direction (not applied):** as #10 already suggests — fail fast on invalid expressions
(typed exception / explicit error result) and let the execution layer mark the step failed.

## BUG-03 — Terminal in one parallel branch silently skips slower siblings (High) — novel

**Mechanism.**
`applyTerminalTransition`
([WorkflowStepExecutionService.java:332](../../src/main/java/com/flux/service/workflow/WorkflowStepExecutionService.java#L332))
finalizes the **whole run** and marks **every** remaining `WAITING_DEPENDENCY`/`PENDING` step
`SKIPPED`. In a parallel fan-out where the branches have different delays, the branch that becomes
due first hits its terminal and cancels the slower sibling **before it ever executes**.

**Graph used:** `a ─▶ [fast, slow]`; `fast` (delay 0) ─▶ terminal; `slow` (delay 30m) ─▶ terminal.
The worker only claims **due** steps, so `slow` is still `PENDING`-in-the-future when `fast`
terminates and is swept to `SKIPPED`.

**Expected:** `slow` is an independent parallel branch and should run (the engine's join model via
`pendingDependencyCount` implies parallel branches are meant to run concurrently).
**Actual:** run COMPLETED, `fast` COMPLETED, `slow` `SKIPPED` — work silently dropped. (Proven
green.)

**Why it matters:** any workflow that fans out to two independent actions (e.g., "create note"
**and** "notify"), where each path ends in its own terminal, will silently execute only the
faster one. This is distinct from #13 (which is the join *under*-firing / deadlock); here the
terminal *over*-cancels.

**Fix direction (not applied):** terminal semantics should be scoped to the branch/run model
deliberately (e.g., a terminal completes only its own path, or fan-out requires an explicit join
before any terminal), and the "skip everything on terminal" sweep should not cancel
not-yet-started independent branches.

## BUG-04 — eventId-less triggers collapse to a single run (Medium) — novel (inverse of #24)

**Mechanism.**
`buildIdempotencyKey`
([WorkflowExecutionManager.java:228](../../src/main/java/com/flux/service/workflow/WorkflowExecutionManager.java#L228))
emits a literal `FALLBACK|NO_EVENT` segment when `eventId` is blank. Two genuinely distinct
triggers for the same `(workflowKey, source, sourcePersonId)` with no eventId hash to the **same**
idempotency key; the second returns `DUPLICATE_IGNORED` and never runs. `triggerPayload`
distinctness does not participate in the key.

**Expected (needs product confirmation):** distinct trigger occurrences that lack an eventId
should still be able to start distinct runs (or there should be an explicit, documented
time/window dedup), rather than *all* eventId-less triggers for a person collapsing to one run for
the lifetime of that key.
**Actual:** second distinct trigger → `DUPLICATE_IGNORED`, one run total. (Proven green.)

**Relationship to known issues:** this is the *inverse* of #24 ("no suppression of duplicate
workflow runs for the same `(workflow_key, source_person_id)`"). With an eventId present the engine
under-suppresses (#24, over-fire); with eventId absent it **over**-suppresses. Whether the fallback
collapse is intentional dedup or a hazard depends on whether production triggers are guaranteed to
carry an eventId — **flagged for intent confirmation**, not asserted as an outright defect.

---

## Areas examined and judged NOT defective (to bound the audit)

- **Unconditional fan-out + AND-join:** correct (positive control passes).
- **Linear multi-node, single-node, terminal-from-entry, delay due-at:** correct (existing
  `WorkflowEngineSmokeTest` + re-verified).
- **Idempotency with a real eventId:** correct dedup (existing smoke test).
- **Retry off-by-one (`shouldRetry: retryCount < maxAttempts-1`):** correct — `DEFAULT_FUB`
  (maxAttempts 3) yields initial + 2 retries; `NO_RETRY` (maxAttempts 1) never retries.
- **Graph validator:** correctly rejects cycles/unreachable nodes; it *allows* the BUG-01 and
  BUG-03 graph shapes, which is why those defects are reachable in production.

## Not done / boundaries

- No production code changed; no known-issues table edited.
- Concurrency-race variants (e.g., the `applyTerminalTransition` PENDING guard under simultaneous
  workers) were reasoned about but not turned into deterministic tests — they need multi-worker
  orchestration and are better covered by the existing race harness under `src/test/.../race/`.
- BUG-04's correct-behavior twin is left as documentation (observing "correct" needs trigger-time
  semantics that are a product decision, not a clear engine contract).
