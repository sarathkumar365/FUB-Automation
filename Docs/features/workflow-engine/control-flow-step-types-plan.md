# Control-Flow Step Types — Brainstorm & Implementation Plan

**Status:** Draft / brainstorming
**Author:** Engineering
**Date:** 2026-04-17
**Scope:** Planning only — no code changes proposed in this document.

---

## 1. Problem Statement

The current workflow engine ships with a solid foundation — a validated DAG, per-node result-code transitions, dependency-tracked merge points, JSONata templating, a pluggable `WorkflowStepType` registry, and retry policy. What it does **not** yet have is a first-class notion of **control flow**:

- There is no ergonomic `if` / `else` step. `branch_on_field` technically performs conditional routing, but it reads like a data step and pushes routing logic into a result-code map rather than into authoring semantics.
- There are no loops — neither `for_each` over a collection, nor bounded `while`. The static-graph validator forbids cycles, which is correct but leaves iteration unsupported.
- There is no explicit `parallel` / join block with a declared concurrency policy. Fan-out works implicitly through DAG shape, but there is no declared join-semantics or per-branch error policy.
- There is no `try` / `catch`: a failing step fails the run; there is no scoped error recovery.
- There is no `terminate` / early-exit step: a branch that wants to end the run cleanly must route to a terminal transition on every downstream node.
- There is no `wait_until` (absolute-time wait) or `wait_for_event` (webhook rendezvous).
- There is no sub-workflow / `call` step: workflows cannot compose or reuse logic.

As a result, authors today hit a ceiling on what can be expressed, and non-trivial flows (per-lead iteration, "try X, on failure notify and archive", "wait for an inbound SMS, else escalate after 24h") must be hand-compiled into awkward DAG shapes or split across multiple workflow definitions stitched together by triggers.

---

## 2. Current Capabilities (grounding)

**Step registry** — `WorkflowStepRegistry` auto-wires all `WorkflowStepType` beans by `id()`. Adding a new step type is a single-file change plus `@Component`.

**Execution loop** — `WorkflowStepExecutionService.executeClaimedStep()` claims a step, builds `RunContext` from prior completed steps' outputs, resolves templated config, invokes `execute()`, persists outputs, and calls `applyTransition()` which:

- On `{"terminal": "COMPLETED"}` → finalizes the run.
- On `["nodeIdA", "nodeIdB", ...]` → materializes next nodes into `workflow_run_steps`, decrementing `pending_dependency_count` for merge-point nodes until they become PENDING.

**Already expressible in the DAG**
- Fan-out (a step's transition lists multiple next nodes).
- Merge / join (a node with multiple predecessors; `depends_on_node_ids` + `pending_dependency_count`).
- Dynamic branching on data (`branch_on_field` → result code → transition).
- Deferred execution (`delay` writes `due_at`; worker polls for due steps).
- Bounded polling (`wait_and_check_claim`, `wait_and_check_communication`).

**Triggers (a useful precedent for "special" step kinds)** — `WorkflowTriggerType` is a peer interface to `WorkflowStepType`, but it is evaluated at webhook ingestion time by `WorkflowTriggerRouter`, not executed in the step loop. It gates *whether* a run is planned; it is not a node in the graph. This is a precedent for introducing a third citizen — **control step** — that executes in the step loop but carries runtime-shaping semantics beyond a plain action.

---

## 3. Design: the "special step type" abstraction

### 3.1 Two interfaces, one registry

Introduce a marker sub-interface of `WorkflowStepType`:

```java
public interface WorkflowControlStepType extends WorkflowStepType {
    /**
     * Control steps may return a graph delta instead of (or in addition to)
     * a plain result code. The execution service applies the delta by
     * materializing new workflow_run_steps and rewriting transitions for
     * the current run only — the stored workflow definition is never mutated.
     */
    StepExpansion planExpansion(StepExecutionContext ctx);
}
```

Control steps still have a config schema, declared result codes, and can still participate in `execute()` — so the registry and admin tooling do not need to fork. The difference is purely that the execution service, when dispatching a control step, consults `planExpansion()` after `execute()` and applies the returned delta.

### 3.2 The graph-delta contract (`StepExpansion`)

```java
public record StepExpansion(
    List<RuntimeNode> newNodes,        // nodes to materialize for this run
    Map<String, List<String>> extraTransitions, // edges from new nodes
    Optional<String> joinNodeId        // where iteration/parallel branches converge
) {}
```

- `newNodes` are materialized into `workflow_run_steps` with synthetic node IDs (`{parentNodeId}#iter-{n}` or `{parentNodeId}#branch-{k}`).
- `joinNodeId` lets the execution service wire `pending_dependency_count` correctly so the join only fires when all expansions complete.
- The **static workflow definition is never rewritten**. The delta lives only in `workflow_run_steps` for that run. This preserves auditability (the stored graph is still what the author drew) and keeps the validator strict (no cycles in the static graph).

### 3.3 Why runtime expansion, not an in-memory interpreter

Two models were considered:

| Aspect | **Runtime expansion** (proposed) | In-memory interpreter |
|---|---|---|
| Per-iteration visibility in `workflow_run_steps` | ✅ yes | ❌ hidden state |
| Reuses existing retry / claim / due_at machinery | ✅ yes | ❌ needs new paths |
| Handles long-delayed iterations across worker restarts | ✅ naturally | ❌ needs new persistence |
| Admin UI can show iteration N of M | ✅ free | ❌ needs new views |
| Elegant for tight loops | ❌ row-per-iteration | ✅ in-memory |

Given Waves 1 & 2 already invested in the materialized-step table, retry policy, and admin observability, the expansion model reuses that investment. In-memory interpretation would essentially build a second, parallel execution substrate.

### 3.4 Validator implications

- The static-graph validator continues to forbid cycles.
- Control nodes are allowed to declare an **expansion contract** in their config schema (e.g. `for_each` must declare `itemsExpression` and a `bodyTemplateNodeId` that points at a sub-graph in the same workflow that is marked `template: true` and therefore exempt from normal reachability rules).
- The runtime expansion is allowed to reference template sub-graphs by ID and clone them — the clone lives only in `workflow_run_steps`.

---

## 4. Proposed Step-Type Catalog

Each entry lists: **id**, purpose, config sketch, declared result codes, tier.

### Tier 1 — high value, low risk

#### `if`
Ergonomic wrapper over `branch_on_field`. A predicate step with a single boolean JSONata expression.
- Config: `{ "predicate": "<jsonata>" }`
- Result codes: `TRUE`, `FALSE`
- Transitions authored as `{ "TRUE": ["thenNode"], "FALSE": ["elseNode"] }`
- **Not** a control step in the expansion sense — it is a normal step that just returns a boolean code. Listed here because it closes an authoring gap and is trivial to implement.

#### `terminate`
Explicit early exit from any branch.
- Config: `{ "status": "COMPLETED" | "FAILED" | "CANCELLED", "reason": "<string|template>" }`
- Effect: `execute()` writes the terminal status directly onto the run and short-circuits transition application.
- Unblocks "on this branch we're done; don't make authors cascade terminal transitions through every downstream node."

#### `wait_until`
Generalization of `delay`. Waits until an absolute timestamp, or until a JSONata-computed time.
- Config: `{ "untilExpression": "<jsonata returning ISO-8601 timestamp>" }`
- Reuses the existing `due_at` materialization path — `execute()` is a no-op, scheduler handles the wait.
- Covers "wait until 9am tomorrow in the lead's timezone," which `delay` cannot.

#### `for_each`
Iterate over a collection from RunContext, run a body sub-graph per item, join results.
- Config: `{ "itemsExpression": "<jsonata returning array>", "itemVariable": "item", "bodyTemplateNodeId": "body_tpl", "joinNodeId": "after_loop", "concurrency": 1 }`
- Expansion: materializes N clones of the body sub-graph, each with its own scope binding `{itemVariable} = item[i]`; all converge on `joinNodeId` via `pending_dependency_count = N`.
- Result codes: `DONE` (emitted by the join synthetic transition), `EMPTY` (collection was empty — skip body, go directly to join).
- **This is the flagship control step.** It unlocks per-lead iteration, per-agent fan-out, per-item validation.

---

### Tier 2 — valuable, more design work

#### `parallel`
Explicit fan-out/join block with declared concurrency cap and per-branch error policy.
- Config: `{ "branches": ["branchA_entry", "branchB_entry", "branchC_entry"], "joinNodeId": "after_parallel", "concurrency": 0, "onBranchFailure": "FAIL_ALL" | "CONTINUE" | "RECORD_AND_CONTINUE" }`
- Improves over implicit DAG fan-out by making the join contract and error policy explicit.
- Result codes: `ALL_OK`, `PARTIAL`, `ALL_FAILED`.

#### `try_catch`
Scoped error handler for a sub-graph.
- Config: `{ "tryEntryNodeId": "try_body", "catchEntryNodeId": "handler", "finallyNodeId": "cleanup", "catchOn": ["TRANSIENT", "PERMANENT", "ANY"] }`
- Semantics: if any step inside the try sub-graph exhausts retries and would fail the run, the run is instead routed to the catch sub-graph with the failure reason in RunContext.
- Result codes: `OK`, `RECOVERED`, `UNCAUGHT`.

#### `call_workflow`
Invoke another workflow definition as a sub-workflow.
- Config: `{ "workflowId": "<uuid>|<slug>", "inputs": { ... }, "outputBinding": "subResult" }`
- Expansion: materializes the called workflow's graph (or spawns a child run — design choice, see §6).
- Unlocks reuse: "send welcome sequence," "run compliance check" become callable units.
- Result codes: `COMPLETED`, `FAILED`, `CANCELLED`.

---

### Tier 3 — powerful, heavy

#### `while`
Condition-based loop with mandatory bounded-iteration guard.
- Config: `{ "predicate": "<jsonata>", "bodyTemplateNodeId": "body_tpl", "joinNodeId": "after_loop", "maxIterations": 100 }`
- Expansion strategy: lazy — after each iteration's body completes, the execution service re-invokes `planExpansion()` on the `while` node; if the predicate still holds and iteration count is under `maxIterations`, another body clone is materialized; otherwise, route to `joinNodeId`.
- Requires `maxIterations` to be mandatory (validator-enforced) so runs remain finite.

#### `wait_for_event`
Pause until a matching webhook arrives, bound to a correlation key.
- Config: `{ "eventType": "fub.sms.inbound", "correlationExpression": "<jsonata>", "timeout": "PT24H", "onTimeout": "timeout_handler" }`
- Requires a new `workflow_event_rendezvous` table mapping correlation keys → waiting step IDs, consulted by the webhook ingestion path.
- Result codes: `MATCHED`, `TIMED_OUT`.
- This is the biggest new infrastructure of the set — it introduces an external-event join dimension to the engine.

#### `switch`
Multi-way branch over a scalar expression. Sugar over chained `if`s.
- Config: `{ "expression": "<jsonata>", "cases": { "a": "nodeA", "b": "nodeB" }, "default": "nodeDefault" }`
- Listed for completeness; low priority once `if` ships.

---

### Explicitly rejected

- **`goto` / arbitrary cycles in the static graph** — defeats validator guarantees, makes retry/replay ambiguous, destroys UI layout.
- **Unbounded `while`** — must always require `maxIterations`.
- **In-place graph mutation** — expansion only writes to `workflow_run_steps`, never to `automation_workflows.graph`.

---

## 5. Iteration Mechanics (for `for_each` and `while`)

The trickiest part of the design is iteration. Spelled out:

1. **Template sub-graphs.** A node inside the static graph may be marked `template: true`. Template sub-graphs are not reachable from `entryNode` by normal edges; the validator permits them solely because they are referenced by a control node's `bodyTemplateNodeId`.

2. **Cloning.** When `for_each` expands, each iteration clones the template sub-graph into `workflow_run_steps` with synthetic IDs: `{templateNodeId}#iter-{n}`. Transitions inside the clone are rewritten to point at the cloned IDs. The last nodes of each clone transition to `joinNodeId`.

3. **Scope isolation.** Each iteration binds `{itemVariable}` (and `{indexVariable}` if configured) into the `ExpressionScope` for that clone's descendants. Scope is scoped to the clone — sibling iterations do not see each other's per-item state.

4. **Join dependency count.** The `joinNodeId` is materialized with `pending_dependency_count = N` (one per iteration). As iterations complete, the count decrements; when it hits zero, the join fires with result code `DONE`.

5. **Empty collection.** If `itemsExpression` evaluates to `[]`, `for_each` emits result code `EMPTY` and transitions directly to `joinNodeId` with `pending_dependency_count = 0`.

6. **Concurrency cap.** If `concurrency > 0`, only that many iteration clones are materialized as PENDING at a time; the remainder are WAITING_DEPENDENCY on synthetic throttle edges.

7. **Iteration output aggregation.** Optional: `resultBinding` config names a RunContext variable that collects each iteration's terminal output into an array, visible post-join. Default off to avoid bloat.

---

## 6. Open Questions

1. **Sub-workflow model** — does `call_workflow` spawn a child `workflow_runs` row (cleaner isolation, harder output passing) or expand inline into the parent run (simpler data flow, muddier observability)? Recommend child-run with explicit output binding.
2. **`wait_for_event` correlation storage** — new table `workflow_event_rendezvous(correlation_key, waiting_step_id, expires_at)` indexed by `(event_type, correlation_key)`. Needs webhook ingestion path changes.
3. **`parallel` concurrency cap** — global to the engine, per-workflow, or per-parallel-block? Lean per-block, with a workflow-level ceiling.
4. **Template sub-graph authoring in the builder UI** — template sub-graphs need a distinct visual affordance; out of scope for this doc but must be coordinated with the builder storyboard.
5. **`try_catch` interaction with retry policy** — does `catchOn: TRANSIENT` catch after retries exhaust, or immediately? Proposed: only after retries exhaust, so retry policy always runs first.
6. **Observability / metrics** — per-iteration timing, per-branch success, event-wait duration. Should land alongside Tier 1 so we don't accumulate unmeasured control flow.

---

## 7. Phased Rollout

Each phase is its own wave, independently shippable.

### Phase A — Tier 1 (control-flow MVP)
- Introduce `WorkflowControlStepType` interface and `StepExpansion` record.
- Extend execution service to apply graph deltas.
- Extend validator to recognize `template: true` sub-graphs.
- Ship: `if`, `terminate`, `wait_until`, `for_each`.
- Admin UI: surface iteration clones in run-detail view.
- Exit criteria: author can express "for each lead in batch, create a task; if none, notify" without custom code.

### Phase B — Tier 2 (error handling and composition)
- Ship: `parallel`, `try_catch`, `call_workflow` (child-run model).
- Extend run-detail UI to show parent ↔ child run linkage.
- Exit criteria: "try HTTP call, on failure notify Slack and archive lead" expressible declaratively.

### Phase C — Tier 3 (advanced)
- Ship: `while` (with mandatory `maxIterations`), `switch`, `wait_for_event` (new rendezvous table + webhook ingestion path).
- Exit criteria: "wait for inbound SMS for up to 24h, else escalate" expressible declaratively.

---

## 8. Non-Goals

- Replacing the existing step types — action steps stay as they are.
- A visual programming DSL — this is a runtime/engine plan; the builder UI is coordinated separately.
- Distributed execution guarantees beyond what the current claim/retry machinery already provides.
- Cycle support in the static graph — iteration is exclusively via runtime expansion.

---

## 9. Summary

The workflow engine's DAG foundation is strong but author-facing primitives are thin. By introducing a **control-step** sub-interface that returns a **runtime graph delta**, we can add loops, parallel blocks, error boundaries, and sub-workflows without mutating stored definitions, without relaxing the cycle-free validator, and without building a second execution substrate. Tier 1 (`if`, `terminate`, `wait_until`, `for_each`) delivers most of the authoring power; Tier 2 adds composition and error recovery; Tier 3 handles external-event rendezvous and conditional iteration.
