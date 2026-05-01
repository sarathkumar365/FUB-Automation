# Workflow Engine — Implementation Plan

## Overview

The rebuild splits cleanly into **three sections**, each producing one bucket of the system. End state:

```
   developer writes step types  →  user composes workflows  →  engine executes them
   ─────────── §2 ───────────       ────────── §3 ──────────       ──────── §1 ────────
```

| Section | Bucket | Built once or grows? |
|---|---|---|
| **§1 Engine core** | The runtime: registry, persistence, scheduler, executor, validator, templating | Built once, stabilizes |
| **§2 Step type library** | Concrete step type implementations (FUB ops + generic utilities) | **Grows continuously** |
| **§3 Workflow builder** | Visual canvas UI for authoring + run inspector | Built once, polished forever |

The two contracts that hold the three sections together — and that decouple them so each can change independently:

- **`WorkflowStepType` Java interface** — between §1 and §2. Adding a step type is implementing one class. Engine doesn't know what FUB or Slack is.
- **Graph JSON schema + `GET /admin/workflows/step-types` endpoint** — between §1 and §3. Builder can render any step type's config form without being recompiled. Builder doesn't know what FUB or Slack is either.

This document is the implementation plan. Engine weakness analysis lives in `policy-engine-rebuild-analysis-and-plan.md`.

---

## Section 1 — Engine Core

The runtime that consumes a workflow JSON definition and executes it. Built once. After it stabilizes, you barely touch it.

### What to build

1. **Step-type registry interface (`WorkflowStepType`)**
   First-class abstraction for what a step type *is*: id, JSON Schema for config, set of declared result codes, retry policy defaults, `execute(ctx)` method. The engine knows about the registry, never about specific step types.
   *Replaces:* hardcoded `PolicyStepType` enum + `EnumMap<PolicyStepType, ...>` dispatch in `PolicyStepExecutionService`.

2. **Graph-shaped blueprint model + new persistence**
   New tables: `automation_workflows`, `workflow_runs`, `workflow_run_steps`. Replace single `depends_on_step_order` (int) with `depends_on_node_ids` (array). Replace `step_order` with stable `node_id` strings. Add `outputs JSONB` and `resolved_config JSONB` columns to step rows.
   *Unblocks:* branching, parallel fan-out, merge points, run reproducibility, debuggable run inspector.

3. **Generic graph validator**
   Validates *structure*, not template content: well-formed JSON, references resolve, no cycles (topological sort), every transition's result code is declared by the step type, every node's config matches the step type's JSON Schema, expression syntax is valid.
   *Replaces:* `PolicyBlueprintValidator`, which is hardcoded to `ASSIGNMENT_FOLLOWUP_SLA_V1`.

4. **Transitions in data, not code**
   The workflow JSON declares `node.transitions[resultCode] → next-node-list | terminal-outcome`. Engine just walks the declaration. No more hardcoded state machine.
   *Replaces:* `PolicyStepTransitionContract`.

5. **Run context object**
   At execution time, every step receives a context containing: trigger event payload, lead identity, prior step outputs by node id, engine metadata. Templates evaluate against this. Step `execute()` returns a result code **and** an `outputs` map that gets merged into the context for downstream steps.
   *New primitive — nothing in the current engine corresponds to this.*

6. **Trigger payload snapshot on the run**
   `workflow_runs.trigger_payload JSONB`. Frozen at plan time. Makes runs reproducible, makes templating against `event.*` possible, makes dry-run feasible.
   *Currently:* the engine does not snapshot the inbound webhook payload — only the policy blueprint.

7. **Expression / template primitive**
   One sandboxed evaluator used by (a) trigger filters, (b) per-edge guards, (c) step config templating, (d) dry-run mock context. Pick one syntax (JSONata, Mustache-with-comparisons, SpEL, or hand-rolled) and make it the only place expressions are evaluated.
   **Highest-leverage addition** — unblocks templated config, per-edge guards, trigger filters, and dry-run all at once.

8. **Templated config + resolved-config snapshot**
   Step config can reference `{{ event.payload.lead.source }}` or `{{ steps.wait_claim.outputs.assigned_user_id }}`. At step start, the engine resolves the templated config against the run context and writes the resolved version to `workflow_run_steps.resolved_config`. This is what makes runs debuggable.
   *Currently:* config is static literals only.

9. **Generic retry primitive**
   Worker handles retry/backoff/transient-vs-permanent classification based on a `RetryPolicy` declared by the step type and overridable per node. Step types stop implementing their own retry loops.
   *Currently:* `WaitAndCheckClaimStepExecutor` has its own retry, others don't.

10. **Trigger declaration on the workflow + dynamic routing**
    `automation_workflows.trigger JSONB` declares `{eventType, filterExpression}`. New `WorkflowTriggerRouter` queries workflows whose trigger matches the incoming event. Routing becomes data, not Java code.
    *Replaces:* hardcoded `processAssignmentDomainEvent` mapping in `WebhookEventProcessorService`.

11. **Signal / wait primitive** *(can be deferred)*
    New step status `WAITING_SIGNAL`. New table keyed by `(run_id, correlation_key)`. Step executors can return "I'm waiting on signal X" instead of a result code. New API endpoint delivers signals and wakes the step. Defer until the first step type actually needs it.

12. **Re-establish validator invariant**
    Every blueprint reaching the engine must have passed the new validator. Fix any data drift, then remove the read-time validation bypass that exists today (known-issues #9).

### What we keep verbatim from the existing engine

These are battle-tested and worth lifting straight in with the table name swapped. Do **not** redesign:

- Atomic step claiming with `FOR UPDATE SKIP LOCKED` — `PolicyExecutionStepRepository.claimDuePendingSteps`
- Idempotency key construction + two-phase dedupe — `PolicyExecutionManager.buildIdempotencyKey` + the `DataIntegrityViolationException` recovery dance
- Blueprint snapshot on the run row — the *concept*; the column grows to also hold trigger payload, but the snapshot pattern stays
- Stale-processing recovery + `stale_recovery_count` — `PolicyExecutionDueWorker` recovery loop
- Compensation in `REQUIRES_NEW` after worker exception
- Polling worker shape and per-poll budget logic

### Smallest viable §1 deliverable

A workflow JSON with one node (e.g. a no-op `delay`) can be POSTed, planned, run end-to-end by the worker, and the run reaches `COMPLETED`. Proves: registry works, graph model persists, validator accepts a real graph, planner materializes correctly, worker advances and terminates the run, transitions execute. Everything else in §1 builds on this skeleton.

---

## Section 2 — Step Type Library

Concrete step type implementations. Each is a Java class implementing `WorkflowStepType` from §1. **This section grows forever** — every new integration is a new class here. The initial seeded library determines what users can do at v1.

### Shared infrastructure (build before any concrete step type)

1. **Extract FUB retry/transient classification** from `WaitAndCheckClaimStepExecutor.executeWithRetry` into `service/fub/FubCallHelper`. Used by every FUB-touching step type.
2. **Common config schema fragments** — reusable JSON Schema snippets for `delayMinutes`, `targetUserId`, `targetPondId`, `messageTemplate`, etc. Avoids each step type re-declaring identical fields.
3. **Standard result-code conventions** — every side-effect step declares at minimum `SUCCESS` and `FAILED`. Document the convention so the builder renders consistent edge labels.
4. **Base class for FUB-calling steps** — handles person-id parsing/validation, FUB error → result-code mapping, retry policy plumbing. Each new FUB step becomes ~20 lines.

### Initial step type catalog (v1)

**Parity steps (so the existing policy can migrate onto the new engine):**
- `wait_and_check_claim` — wraps existing `WaitAndCheckClaimStepExecutor` logic
- `wait_and_check_communication` — wraps existing `WaitAndCheckCommunicationStepExecutor` logic
- `fub_reassign` — extracts the REASSIGN branch of `OnCommunicationMissActionStepExecutor`
- `fub_move_to_pond` — extracts the MOVE_TO_POND branch

**Common FUB operations:**
- `fub_add_tag` / `fub_remove_tag`
- `fub_create_task`
- `fub_create_note`
- `fub_send_text` (via FUB API)
- `fub_send_email` (via FUB API)

**Generic / utility steps:**
- `delay` — generic timer decoupled from claim/comm semantics
- `branch_on_field` — generic conditional node, evaluates an expression and emits a result code
- `http_request` — generic outbound webhook (lets users hit anything)
- `slack_notify` — first non-FUB step type, proves the registry is genuinely external-system-agnostic
- `set_variable` — write a value into the run context for later steps to read

### Smallest viable §2 deliverable

Two real step types implemented through the registry: `wait_and_check_claim` (proves FUB integration through the new interface) and `delay` (proves a generic non-FUB step type). Both runnable inside the §1 skeleton.

---

## Section 3 — Workflow Builder UI

The frontend that produces workflow JSON. Built once, polished over time. Knows nothing about specific step types — everything is driven by data from the §1 step-type registry endpoint.

### What to build

1. **New module** `ui/src/modules/workflows/`. Sibling to `ui/src/modules/policies/` — does not modify it.
2. **Canvas** — `@xyflow/react` (React Flow). Drag nodes from a palette, connect with edges, zoom/pan/minimap.
3. **Palette** — populated from `GET /admin/workflows/step-types`. New step types appear automatically without UI code changes.
4. **Per-node config form** — rendered from each step type's JSON Schema via `@rjsf/core`. Zero per-step-type UI code.
5. **Auto-layout** — `elkjs` or `dagre`. One function call to lay the graph out cleanly.
6. **Edge drawing for transitions** — each step type's declared result codes become handles on the node. User connects each handle to the next node. Builder enforces one outgoing edge per result code (or one fan-out group).
7. **Inline graph validation** — surfaces engine validator errors as you author: cycles, unreachable nodes, undeclared result codes, expression references to nonexistent variables, missing required config.
8. **Templating expression input** — text fields for templated config show autocomplete against the available context (`event.*`, `lead.*`, `steps.<previous>.outputs.*`). Live-validates against the expression evaluator.
9. **Save / Activate flow** — POST `/admin/workflows`, PUT for updates, POST activate. Optimistic locking via `expectedVersion`, like today's policy controller.
10. **Run inspector** — adapts existing `RunInspector.tsx` and `StepTimeline.tsx` from `ui/src/modules/policies`. Shows every step's resolved config (post-templating), outputs, error messages, retry attempts, durations. v1 can render the run as a list of node-instances; graph visualization for a run can land later.
11. **Manual retry from run inspector** — single click to resume a failed run from the failed node.
12. **Run history search** — filter by workflow, status, lead id, date range, error code.
13. **Test / dry-run mode** — manual trigger from the builder against a mock event payload. Action executors swapped for log-only adapters.
14. **New top-level "Workflows" nav** — sibling to "Policies" (which keeps running on the old engine until §1 + §2 are far enough along to migrate).

### How the builder works (user experience)

1. User opens the Workflows page (new top-level nav, sibling to "Policies").
2. Clicks "New Workflow." A blank canvas opens with a **palette** on the left.
3. The palette is populated from `GET /admin/workflows/step-types` — shows every registered step type with its name and description. If a developer adds a new step type tomorrow, it appears here automatically without a UI deploy.
4. User **drags** a step type onto the canvas. A node appears with output handles labeled by the step type's declared result codes (e.g., `CLAIMED`, `NOT_CLAIMED`).
5. User **clicks** the node. A config panel opens on the right — rendered from the step type's JSON Schema by rjsf. Some fields accept template expressions — typing `{{` triggers autocomplete showing available context variables (`event.*`, `lead.*`, `steps.<previous>.outputs.*`).
6. User **draws edges** from result-code handles to other nodes. This is how transitions are defined — "on this result, go to that node."
7. **Inline validation** runs continuously — warnings for cycles, unreachable nodes, missing config, undeclared result codes, invalid expressions.
8. User clicks **Save** → builder serializes canvas to graph JSON → POST `/admin/workflows`. Then **Activate** → deactivates siblings, marks ACTIVE.
9. User switches to the **Runs tab**. Fires a test webhook (or uses dry-run mode with a mock payload). Clicks into the **run inspector** — sees every step's resolved config, outputs, errors, retry attempts, durations.
10. If a run failed, clicks **"Retry from failed step"** to resume without re-running completed steps.

### Libraries (instead of building from scratch)

| Concern | Library |
|---|---|
| Canvas / node graph | `@xyflow/react` |
| Per-node config forms | `@rjsf/core` (react-jsonschema-form) |
| Auto-layout | `elkjs` (or `dagre`) |
| Form state / validation | already covered by Zod + react-hook-form patterns in the codebase |

Backend stays library-light: hand-rolled JSON Schema validation is fine for v1, with `networknt/json-schema-validator` available if/when we want stricter validation.

### Smallest viable §3 deliverable

Drag two nodes onto the canvas, connect them via a result-code edge, configure each from the schema-driven form, save, activate, fire a test webhook, observe the run advance in the run inspector. Proves: registry endpoint works, builder renders any step type, JSON output round-trips through engine, run inspector reads new step shape.

---

## Cross-section dependencies & recommended execution order

The three sections cannot run strictly sequentially because each one needs the others to validate it works (the engine needs at least one step type to test against; the builder needs the registry endpoint populated). Realistic interleaving in five waves:

```
Wave 1               Wave 2               Wave 3               Wave 4           Wave 5        Wave 6
§1 foundations   →  §1 deepening     →  §1 finishing      →                →               →
     ↓                   ↓                   ↓                  ↓                            
§2 first steps   →  §2 parity steps  →                    →  §2 full lib  →               →
                                                                             §3 builder UI →  Migration
```

### Wave 1 — Make the engine run one step

**Scope:**
- §1 items **1, 2, 3, 4** (registry interface, graph model + persistence, generic validator, transitions in data)
- §2 shared infrastructure + first 2 step types (`wait_and_check_claim`, `delay`)

**What happens:**

You start with the skeleton. A `WorkflowStepType` interface — any step type must declare: "my id is X, my config looks like Y, I can return these result codes, and here's my execute method." A `WorkflowStepRegistry` that auto-discovers every class implementing that interface. New tables (`automation_workflows`, `workflow_runs`, `workflow_run_steps`) with `node_id` instead of `step_order`, `depends_on_node_ids` instead of single int, plus `outputs` and `resolved_config` columns — both nullable and unused for now, just present so you don't need a migration later. A generic graph validator that checks structure (no cycles, references resolve, result codes match step type declarations, config matches step type schema). Transitions declared in the workflow JSON, not in a hardcoded Java contract.

The planner, worker, and executor are lifted from the existing engine — same `FOR UPDATE SKIP LOCKED` claim pattern, same idempotency key construction, same stale recovery, same compensation transactions. Different tables, same mechanics.

Then you implement two step types to prove it works: `wait_and_check_claim` (real FUB integration through the new interface) and `delay` (trivial timer, proves non-FUB steps work too).

**Success criteria:** You can hand-write a workflow JSON with one or two nodes, POST it, the worker picks it up, executes each step, and the run reaches `COMPLETED`. No UI, no templating, no fancy routing — just the engine running.

**What this proves:** registry works, graph model persists correctly, validator accepts a real graph, planner materializes steps correctly, worker claims and advances steps, transitions execute from data, terminal outcomes fire.

---

### Wave 2 — Make the engine handle real workflows

**Scope:**
- §1 items **5, 6, 7, 8** (run context, trigger payload snapshot, expression primitive, templated config + resolved snapshot)
- §2 parity steps (`wait_and_check_communication`, `fub_reassign`, `fub_move_to_pond`)
- §2 generic steps (`branch_on_field`, `set_variable`)
- Extract the FUB retry helper from `WaitAndCheckClaimStepExecutor.executeWithRetry` into a shared `service/fub/FubCallHelper`, so both old and new executors use the same retry logic without duplication.

**What happens:**

Now you make the engine do what the old one does, plus more. When a worker claims a step, the engine rehydrates a run context from the database: the trigger event payload (now snapshotted on `workflow_runs.trigger_payload`), the lead identity, and all prior completed steps' `outputs`. This context gets passed to `execute()`. Step config can now contain template expressions like `{{ event.payload.lead.source }}` or `{{ steps.wait_claim.outputs.assigned_user_id }}` — the engine resolves them against the run context before execution and writes the resolved version to `workflow_run_steps.resolved_config`. One sandboxed expression evaluator handles templating, trigger filters, and per-edge guards — pick the syntax (JSONata or Mustache-with-comparisons) and commit, it's the same evaluator everywhere.

On the step type side, you implement the remaining parity steps (`wait_and_check_communication`, `fub_reassign`, `fub_move_to_pond`) so the old policy can be expressed as a workflow. Plus `branch_on_field` (evaluates an expression, emits a result code) and `set_variable` (writes a value into run context for later steps) — both need the expression primitive.

**Success criteria:** Author the existing `ASSIGNMENT_FOLLOWUP_SLA_V1` policy as a workflow in the new engine. Run the same test scenarios (lead claimed, lead not claimed, communication found, communication not found, reassign success, reassign failure). Assert identical outcomes, identical result codes, identical terminal states. This is a row-for-row parity test against the old engine.

**What this proves:** branching works, step-to-step data passing works, templating works, the new engine is a strict superset of the old one. This is the first wave where the new engine is *more capable* than the old one.

---

### Wave 3 — Make the engine self-routing with a complete step library

**Scope:**
- §1 items **9, 10** (retry primitive, trigger declaration on workflow + dynamic routing)
- §2 common FUB ops (`fub_add_tag`, `fub_create_task`, `fub_create_note`, `fub_send_text`, `fub_send_email`)
- §2 outbound steps (`http_request`, `slack_notify`)

**What happens:**

Two engine additions. First, a generic retry primitive: the worker handles retry/backoff based on a `RetryPolicy` declared by the step type and overridable per node in the workflow JSON. Step types stop implementing their own retry loops — they just classify errors as transient or permanent and the engine does the rest. Second, trigger declaration: each workflow row gets a `trigger JSONB` field (`{eventType: "peopleCreated", filter: "lead.source == 'Zillow'"}`). A new `WorkflowTriggerRouter` replaces the hardcoded Java routing — on webhook arrival, it queries active workflows whose trigger matches, evaluates the filter expression, fans out. No code changes needed to route new event types — just author a workflow with the right trigger.

On the step type side, you fill out the library. Common FUB operations (`fub_add_tag`, `fub_remove_tag`, `fub_create_task`, `fub_create_note`, `fub_send_text`, `fub_send_email`) — each is thin, ~20 lines on top of the shared `FubCallHelper` base class. Plus outbound integrations: `http_request` (generic — lets users hit any URL) and `slack_notify` (first non-FUB step, proves the registry is genuinely agnostic).

**Success criteria:** Create a new workflow with trigger `{eventType: "peopleStageUpdated", filter: "event.payload.lead.source == 'Zillow'"}` that uses `fub_add_tag` and `slack_notify` in a branching graph. Fire a matching webhook. The router picks it up, plans a run, and the workflow executes end-to-end — without any Java code changes for routing. Then fire a non-matching webhook (wrong source) and confirm the router skips it.

**What this proves:** workflows are fully self-contained — a workflow declares what triggers it, the engine routes events to it, no hardcoded wiring. The step library covers common FUB operations. The platform is feature-complete on the backend.

---

### Wave 4 — Step type library (FUB operations + utilities)

**Scope:** All remaining §2 step types not covered in Waves 1–3.

**What happens:**

Each step type is a thin Java class implementing `WorkflowStepType`. The shared `FubCallHelper` base class (extracted in Wave 2) handles retry, error mapping, and person-ID validation — so each FUB step is ~20 lines of unique logic. Detailed specs (FUB API endpoints, config schemas, result codes, error mapping) will be written per step type as they're implemented.

**Step types to implement:**

FUB operations:
- `fub_add_tag` / `fub_remove_tag` — POST/DELETE tag on a FUB person
- `fub_create_task` — POST a task linked to a person
- `fub_create_note` — POST a note on a person
- `fub_send_text` — send SMS via FUB API
- `fub_send_email` — send email via FUB API

Outbound integrations:
- `http_request` — generic outbound HTTP call (any URL, configurable method/headers/body)
- `slack_notify` — post a message to a Slack channel via webhook URL

Utility:
- `branch_on_field` — generic conditional node (evaluates expression, emits result code)
- `set_variable` — write a value into run context for later steps

**Success criteria:** Each step type has: a working `execute()` method, a JSON Schema for its config, declared result codes, and a unit test. The full catalog shows up in `GET /admin/workflows/step-types` and can be used in workflow graphs.

**What this proves:** the plugin model scales — adding the 10th step type is as easy as adding the 2nd.

---

### Wave 5 — Builder UI (deferred)

**Scope:** All of §3. **Deferred — will be planned in detail when Waves 1–4 are complete.**

**What happens:**

New frontend module `ui/src/modules/workflows/`. Three libraries do the heavy lifting:
- `@xyflow/react` (React Flow) — the canvas. Nodes, edges, zoom, pan, drag, selection, keyboard shortcuts. Industry standard.
- `@rjsf/core` (react-jsonschema-form) — per-node config form. Each step type ships a JSON Schema for its config. rjsf renders the form. Zero per-step-type UI code.
- `elkjs` — auto-layout. One function call to position nodes nicely.

**The user experience, end to end:**

1. User opens the Workflows page (new top-level nav, sibling to "Policies").
2. Clicks "New Workflow." A blank canvas opens with a palette on the left.
3. The palette is populated from `GET /admin/workflows/step-types` — shows every registered step type with its name and description. New step types appear automatically without a UI deploy.
4. User drags a step type onto the canvas. A node appears with output handles labeled by the step type's declared result codes (e.g., `CLAIMED`, `NOT_CLAIMED`).
5. User clicks the node. A config panel opens on the right — rendered from the step type's JSON Schema by rjsf.
6. User draws edges from result-code handles to other nodes — this defines transitions.
7. Inline validation runs continuously.
8. User clicks Save → Activate. Runs tab shows execution. Run inspector shows step-by-step data.

**Success criteria:** An ops team member can author, activate, and debug a workflow entirely from the browser.

**Detailed technical spec for this wave will be written separately before implementation begins.**

---

### Wave 6 — Migrate and clean up

**Scope:**
- §1 items **11, 12** (signal/wait primitive only if needed; re-establish validator invariant)
- Migration of existing policy onto the new engine
- Decommission old policy module

**What happens:**

1. Author the existing `ASSIGNMENT_FOLLOWUP_SLA_V1` as a workflow in the new engine using the builder from Wave 4.
2. Run it in **shadow mode** alongside the old policy — both engines fire on the same webhooks, but the new workflow uses `slack_notify` instead of `fub_reassign` so there are no real side effects. Compare run outcomes for the same events over one full cycle. Any variance = bug to fix.
3. Once parity is confirmed, **cut over**: disable the old policy (`enabled = false` — instant rollback by re-enabling). Point the new workflow at the real `fub_reassign` step.
4. Leave the old `service/policy/*` code in place for one release as a safety net.
5. Delete `service/policy/`, `controller/AdminPolicy*`, and `ui/src/modules/policies/` in a follow-up cleanup after a stable production run.
6. Optionally: build the signal/wait primitive (§1 item 11) if any step type needs it by now. Re-establish the validator invariant (§1 item 12) — every workflow reaching the engine must have passed the generic validator, no bypasses.

**Success criteria:** The old policy module is deleted. All automation runs on the new engine. The cutover was reversible at every step. No runs were lost or duplicated during migration.

**What this proves:** the new engine is a full replacement, not just a parallel experiment.

---

### What stays untouched during Waves 1–5

The existing `ASSIGNMENT_FOLLOWUP_SLA_V1` policy keeps running on the old engine (`service/policy/*`, `automation_policies` table, `policy_execution_runs/steps` tables) throughout Waves 1–5. The new module (`service/workflow/*`, `automation_workflows` table, `workflow_runs/steps` tables) is a sibling, not a replacement. The two engines share no tables, no code paths, and no state. The only shared pieces are the `FollowUpBossClient` (which both call) and the `FubCallHelper` (extracted in Wave 2 for reuse). Zero risk to production during the build.

The old policy module is only touched in Wave 6 (migration), and even then only by flipping `enabled = false` — not by editing its code.

The waves are not calendar time — each wave is "the work that must land before the next wave can start." Pace is up to you.

---

## Decisions (locked in)

1. **Module naming** — `workflow/Workflow*` for the new module. Old `policy/Policy*` untouched until decommission.
2. **Database strategy** — new tables alongside old. Old tables stay until no longer needed, then removed.
3. **Expression language — JSONata.** Not needed in Wave 1 (all config values are literals), used from Wave 2 onward. Java library: `com.dashjoin:jsonata`. JSON-native, safe by default (no I/O, no Java class access), powerful enough for string ops / math / array queries / conditionals. Used in four places: step config templating, trigger filters, per-edge guards, and the `branch_on_field` step type.
4. **Signal/wait scope** — deferred until a step type needs it.
