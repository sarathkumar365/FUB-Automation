# Policy Engine Rebuild — Analysis & Greenfield Plan

## Context

The automation engine today has a **policy management** subsystem that evaluates incoming FUB webhooks and runs SLA-style automations against leads. It is intentionally narrow: a single hardcoded template (`ASSIGNMENT_FOLLOWUP_SLA_V1`) describing a fixed 3-step linear pipeline (`WAIT_AND_CHECK_CLAIM` → `WAIT_AND_CHECK_COMMUNICATION` → `ON_FAILURE_EXECUTE_ACTION`) with two possible actions (`REASSIGN`, `MOVE_TO_POND`).

The user wants to **rebuild this into a real, scalable policy engine** that an internal ops team can author against via a **visual builder**, with two new capabilities the current system cannot express:

1. **Extensible step / action types** (new action types beyond reassign/move-to-pond — e.g. send SMS, add tag, create task, post to Slack — without writing Java + redeploying for each one).
2. **Branching / multi-path workflows** (beyond a fixed linear 3-step pipeline — parallel branches, conditional next-steps, more than three steps).

The user's chosen scope is **greenfield engine alongside the old one**: build the new engine in parallel (new tables, new module, new API surface), migrate use-cases over, then retire the old. This keeps `ASSIGNMENT_FOLLOWUP_SLA_V1` running untouched while we iterate.

Out of scope per the user's answers: new trigger event types and rich conditional/filter logic on triggers (those can come later — branch-edge conditions are still in scope as a side-effect of branching).

---

## Current System — What Exists Today (Summary)

### Conceptual model
- **Policy** = `(domain, policyKey)` scope (e.g. `ASSIGNMENT/FOLLOW_UP_SLA`) + a JSON `blueprint` + `status ACTIVE|INACTIVE` (only one ACTIVE per scope, enforced by partial unique index) + a `version` for optimistic locking.
- **Blueprint** = JSON document with hardcoded shape: `templateKey`, `steps[3]` in fixed order with hardcoded dependsOn, plus an `actionConfig` block.
- **Run** = one materialized execution of a policy for one lead from one webhook event, deduplicated by an `idempotency_key` (SHA-256 of domain|policyKey|source|action|leadId|eventId-or-payloadHash).
- **Step** = one row per pipeline step on the run, with `due_at`, `status`, `result_code`, `depends_on_step_order`.

### Key files (verified)
| Role | File |
|---|---|
| Blueprint validator (single-template, hardcoded) | `src/main/java/com/fuba/automation_engine/service/policy/PolicyBlueprintValidator.java` |
| Planning / idempotency / run+step materialization | `src/main/java/com/fuba/automation_engine/service/policy/PolicyExecutionManager.java` |
| Step dispatch + transition application | `src/main/java/com/fuba/automation_engine/service/policy/PolicyStepExecutionService.java` |
| Polling worker (every 2s, FOR UPDATE SKIP LOCKED) | `src/main/java/com/fuba/automation_engine/service/policy/PolicyExecutionDueWorker.java` |
| Step executors (one per Java class) | `WaitAndCheckClaimStepExecutor`, `WaitAndCheckCommunicationStepExecutor`, `OnCommunicationMissActionStepExecutor` (same package) |
| Hardcoded step state machine | `PolicyStepTransitionContract` |
| Webhook → policy fan-out | `service/webhook/WebhookEventProcessorService.java` (`processAssignmentDomainEvent`) |
| Admin API | `controller/AdminPolicyController.java`, `controller/AdminPolicyExecutionController.java` |
| DB schema | `src/main/resources/db/migration/V5,V6,V7,V9` |
| UI module (form-based, no JSON editor) | `ui/src/modules/policies/**` (notably `ui/PolicyFormModal.tsx`) |
| Deep-dive docs | `Docs/deep-dive/07-flow-assignment-policy.md`, `08-flow-policy-execution.md` |

### What is genuinely good and worth keeping
These are the parts of the current system that should be **lifted into the new engine, not redesigned**:

1. **Atomic step claiming** — `claimRepository.claimDuePendingSteps()` uses `FOR UPDATE SKIP LOCKED` over `(status='PENDING' AND due_at <= now)`. This is the right pattern for a multi-worker due-step queue and we should reuse it verbatim.
2. **Idempotency key model** — SHA-256 of `(domain|policyKey|source|action|leadId|eventId|payloadHash)` with two-phase duplicate detection (pre-insert lookup + post-insert constraint-violation recovery). This is correct and we should keep its semantics.
3. **Blueprint snapshot on the run** — `policy_execution_runs.policy_blueprint_snapshot` freezes the blueprint at plan time so a mid-run policy edit can't corrupt in-flight executions. Keep this.
4. **Stale-processing recovery** — `recoverStaleProcessingSteps()` + `stale_recovery_count` give us crash recovery for orphaned `PROCESSING` rows. Keep.
5. **Compensation in `REQUIRES_NEW`** — failures from `executeClaimedStep` are marked failed in a fresh transaction with retry/backoff. Keep.

### What blocks the user's goals (extension barriers)
1. **Step types are Java classes wired by Spring**, indexed in an `EnumMap<PolicyStepType, ...>`. Adding a new step requires writing Java, registering a bean, recompiling, redeploying. Users cannot define new steps. (`PolicyStepExecutionService` constructor)
2. **`PolicyBlueprintValidator` is hardcoded to one template** and explicitly rejects anything that isn't `ASSIGNMENT_FOLLOWUP_SLA_V1` (`PolicyBlueprintValidator.java:54`). The class itself has a TODO comment (lines 10–18) describing the target shape: a template-registry + per-template validator strategy.
3. **The state machine is a literal hardcoded contract** (`PolicyStepTransitionContract`) — `(stepType, resultCode) → next-step OR terminal-outcome`. No DAG, no branching, no per-edge conditions.
4. **Step ordering is rigid**: validator enforces exactly 3 steps in exactly the canonical order with exactly the canonical `dependsOn`. There is no support for parallel branches, alternative next-steps, or pipelines of any other length.
5. **Action targets are static integers** (`targetUserId` / `targetPondId` numbers). No parameterization, no expressions, no template variables.
6. **Validator bypass on read** (known-issues #9): `getActivePolicy()` skips blueprint validation, so invalid blueprints can leak into execution.
7. **Tight coupling to FUB models** in step executors (`PersonDetails`, `followUpBossClient.reassignPerson`). Action mutations are also **log-only in dev** today — not production wired.
8. **UI is form-only and template-aware** (`PolicyFormModal.tsx` knows about claim delay, comm delay, action type fields). No generic step-graph builder.

---

## Goals for the rebuild

1. **Plugin step/action registry** — adding a new step type is a small server-side change (one class implementing a stable interface, plus declaring its input/output schema), with zero changes to the engine, validator, scheduler, UI builder, or persistence.
2. **DAG-shaped workflows** — a policy is a directed acyclic graph of steps, with named outcomes per step that select the next edge(s). Supports linear, branching, parallel-fan-out, and merge-points. Cycles forbidden by validator.
3. **Author-facing visual builder** — node-graph editor for ops users; the underlying serialized form is JSON, but no human edits JSON in the normal path. Built on top of the new schema.
4. **Greenfield, alongside the old engine** — new tables, new module path, new admin endpoints, new UI tab. The existing `automation_policies` / `policy_execution_runs` / `policy_execution_steps` and `ASSIGNMENT/FOLLOW_UP_SLA` keep running unchanged until use-cases are migrated.
5. **Preserve the production-grade primitives** from today's system: atomic claim with `SKIP LOCKED`, idempotency-key dedupe, blueprint snapshotting on the run, stale recovery, compensation transactions.

---

## Proposed architecture

### Naming
To keep the two engines unambiguous in code and DB while we run them side by side, the new engine uses the prefix **`workflow`** instead of `policy`. (Open to a different name; the important thing is no collision with the existing `policy_*` symbols.)

- Old: `automation_policies`, `policy_execution_runs`, `policy_execution_steps`, `service.policy.*`
- New: `automation_workflows`, `workflow_runs`, `workflow_run_steps`, `service.workflow.*`

### Data model (new tables)

**`automation_workflows`** — workflow definitions
- `id` BIGSERIAL PK
- `key` VARCHAR(128) — stable identifier, unique per active version
- `name`, `description` — human-facing
- `trigger` JSONB — `{eventDomain, eventAction, leadIdSelector}`. **For v1, this matches today's hardcoded routing**: `{ASSIGNMENT, *, resourceIds[*]}`. The trigger model is intentionally minimal — we are not adding rich filter logic in v1.
- `graph` JSONB — the DAG (see schema below)
- `status` VARCHAR(16) — `DRAFT | ACTIVE | INACTIVE`
- `version` BIGINT — `@Version` optimistic lock
- Partial unique index on `(key) WHERE status='ACTIVE'` — same one-active-per-key pattern as today

**`workflow_runs`** — one row per `(workflow, lead, triggering event)`
- Mirrors today's `policy_execution_runs` 1:1 in column shape, plus:
  - `workflow_id`, `workflow_version`, `workflow_graph_snapshot` JSONB
  - Same `idempotency_key` construction and uniqueness constraint
  - Same `status` enum: `PENDING / BLOCKED / DUPLICATE_IGNORED / COMPLETED / FAILED`

**`workflow_run_steps`** — one row per node instance in a run
- `id`, `run_id`, `node_id` (string from graph), `step_type` (string, looked up in registry), `status`, `due_at`, `result_code`, `outputs` JSONB, `error_message`, `stale_recovery_count`
- **No `step_order` column** — order is implicit from the graph. Replaced by `node_id` (the graph node's stable ID).
- Indexes: `(status, due_at)` for the claim query, `(run_id, node_id)` unique
- Reuses today's `WAITING_DEPENDENCY → PENDING → PROCESSING → COMPLETED|FAILED|SKIPPED` lifecycle

### Workflow graph schema (the JSON the visual builder produces)

```json
{
  "schemaVersion": 1,
  "trigger": { "eventDomain": "ASSIGNMENT", "eventAction": "*" },
  "nodes": [
    {
      "id": "wait_claim",
      "type": "wait_and_check_claim",
      "config": { "delayMinutes": 5 },
      "transitions": {
        "CLAIMED": ["wait_comm"],
        "NOT_CLAIMED": { "terminal": "NON_ESCALATED_CLOSED" }
      }
    },
    {
      "id": "wait_comm",
      "type": "wait_and_check_communication",
      "config": { "delayMinutes": 10 },
      "transitions": {
        "COMM_FOUND":     { "terminal": "COMPLIANT_CLOSED" },
        "COMM_NOT_FOUND": ["reassign_lead", "notify_slack"]
      }
    },
    {
      "id": "reassign_lead",
      "type": "fub_reassign",
      "config": { "targetUserId": 77 },
      "transitions": {
        "SUCCESS": { "terminal": "ACTION_COMPLETED" },
        "FAILED":  { "terminal": "ACTION_FAILED" }
      }
    },
    {
      "id": "notify_slack",
      "type": "slack_notify",
      "config": { "channel": "#ops-leads", "template": "Lead {leadId} reassigned" },
      "transitions": { "SUCCESS": { "terminal": "ACTION_COMPLETED" } }
    }
  ],
  "entryNode": "wait_claim"
}
```

Key properties:
- **`type`** is a string looked up in the StepType registry — not an enum, not a Java class reference.
- **`transitions`** maps result codes (declared by the step type's contract) to either a list of next node IDs (fan-out → multiple `WAITING_DEPENDENCY` steps activated together) or a `{terminal: REASON}` outcome.
- **Branching** falls out of having multiple result codes per step.
- **Parallel fan-out** falls out of `transitions[code]` being a list.
- **Merge points** — if a node is named as a `next` in two branches, both must complete before it activates (`depends_on_node_ids` array on the run-step row, replaces today's single `depends_on_step_order`).
- **Cycles forbidden** — graph validator does a topological sort.

### Step type plugin model

A step type is a class implementing a new interface:

```java
public interface WorkflowStepType {
    String id();                                  // e.g. "fub_reassign"
    StepConfigSchema configSchema();              // JSON schema for `config` block — drives validator AND visual builder form
    Set<String> declaredResultCodes();            // e.g. {"SUCCESS","FAILED"} — used to validate transitions
    StepExecutionResult execute(StepExecutionContext ctx); // same shape as today's PolicyStepExecutionResult
}
```

A central `WorkflowStepRegistry` is a Spring bean that auto-discovers all `WorkflowStepType` implementations. Every other layer (validator, scheduler, builder UI metadata endpoint) goes through the registry.

**Concrete step types delivered in v1** — wrap the existing executors so we get parity with today's pipeline plus new value:
- `wait_and_check_claim` — wraps existing `WaitAndCheckClaimStepExecutor` logic
- `wait_and_check_communication` — wraps existing `WaitAndCheckCommunicationStepExecutor` logic
- `fub_reassign` — extracts the REASSIGN branch of `OnCommunicationMissActionStepExecutor`
- `fub_move_to_pond` — extracts the MOVE_TO_POND branch
- One brand-new step type to **prove the plugin model works end-to-end** before the user has to write one — recommendation: `slack_notify` (low blast radius, shows off branching and parallel fan-out in a demo workflow)

The existing `service.policy.*` step executor classes are **not deleted** — they keep running the old engine. The new step types are **new classes** that delegate into the same FUB client and reuse helper methods (e.g. retry/transient classification) extracted into a small shared package.

### Engine internals (how a webhook becomes execution)

1. **Trigger router** — `WorkflowTriggerRouter` listens on the same event boundary that `WebhookEventProcessorService.processAssignmentDomainEvent` does. For each lead derived from `resourceIds`, it queries `automation_workflows` for any ACTIVE workflows whose `trigger` matches the event's `(eventDomain, eventAction)`. Multiple workflows may match → fan-out per workflow.
2. **Planner** — `WorkflowExecutionManager.plan(request)` is the analog of `PolicyExecutionManager.plan`. Same idempotency-key construction (with `workflowKey` instead of `policyKey`), same two-phase dedupe, same blueprint snapshot (now `workflow_graph_snapshot`), same materialization.
3. **Materialization** — instead of "Step 1 PENDING, Step 2/3 WAITING_DEPENDENCY", we walk the graph: the entry node becomes `PENDING` with `due_at = now + entryNode.config.delayMinutes` (or `now` if no delay), and every other node becomes `WAITING_DEPENDENCY` with `depends_on_node_ids` populated from inbound edges.
4. **Worker** — `WorkflowExecutionDueWorker` is structurally identical to `PolicyExecutionDueWorker` (same poll interval, same `claimDuePendingSteps()` query against the new table, same stale-recovery, same compensation transaction). Effectively a copy with the table name and step-execution service swapped.
5. **Step execution + transition** — `WorkflowStepExecutionService` resolves the step type from the registry (not an `EnumMap`), runs `execute()`, and applies the transition by:
   - Looking up `node.transitions[resultCode]`
   - If terminal → mark all non-terminal steps `SKIPPED`, mark run `COMPLETED` with the terminal reason
   - If list of next nodes → for each, find the run-step row, decrement its outstanding-dependencies count; when it hits zero, flip `WAITING_DEPENDENCY → PENDING` with `due_at = now + nextNode.config.delayMinutes` (or `now`)
   - Merge-point dependency tracking is the only structurally new piece relative to today's contract

### Visual builder (UI)

New module: `ui/src/modules/workflows/`. Sibling to `ui/src/modules/policies/` — does **not** modify it.

- **Library:** [React Flow](https://reactflow.dev/) for the node-graph canvas. Node palette is populated dynamically from a new endpoint `GET /admin/workflows/step-types` that returns the registry contents (id, label, description, configSchema, declaredResultCodes).
- **Per-node config form** is rendered from each step type's JSON schema. This is what makes adding a new step type require **zero UI changes**.
- **Edges** are drawn from a node's result-code outputs to other nodes. The builder enforces one outgoing edge per result code (or one fan-out group), and warns on unreachable nodes / cycles / undeclared result codes.
- **Save** serializes the canvas to the JSON schema above and POSTs to `POST /admin/workflows`. Activation flow mirrors today's `POST /admin/policies/{id}/activate`.
- **Runs tab** — reuses the existing run-inspector pattern from `ui/src/modules/policies/ui/RunInspector.tsx` and `StepTimeline.tsx`. Wire it against the new endpoints. The timeline becomes a graph view rather than a strictly linear one (or starts as a list of node-instances and evolves to a graph view in a later phase — see Phase 4).

### Admin API (new surface)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/admin/workflows` | list, filter by status |
| `POST` | `/admin/workflows` | create DRAFT |
| `PUT` | `/admin/workflows/{id}` | update graph; bumps version |
| `POST` | `/admin/workflows/{id}/activate` | deactivates siblings on same `key`, marks ACTIVE |
| `GET` | `/admin/workflows/step-types` | step registry contents (powers builder palette + per-node form) |
| `GET` | `/admin/workflow-runs` | list runs with filters/pagination |
| `GET` | `/admin/workflow-runs/{id}` | run detail with all node-instance steps |

These are net-new — the existing `/admin/policies/**` and `/admin/policy-executions/**` endpoints stay live and untouched.

---

## Phased delivery

**Phase 0 — Decision lock-in (this document).** Already done by virtue of the user's answers above.

**Phase 1 — Engine skeleton, no UI, one step type.**
- New Flyway migration: `automation_workflows`, `workflow_runs`, `workflow_run_steps` with the indexes called out above.
- New package `service/workflow/` with: `WorkflowStepType` interface, `WorkflowStepRegistry`, `WorkflowGraphValidator` (shape + topological sort + transition reachability), `WorkflowExecutionManager` (plan + idempotency, lifted from `PolicyExecutionManager`), `WorkflowExecutionDueWorker` (lifted from `PolicyExecutionDueWorker`), `WorkflowStepExecutionService`.
- One step type implementation: `wait_and_check_claim`, wrapping `WaitAndCheckClaimStepExecutor`'s logic (extract the FUB call + retry helper into a shared util — do not duplicate).
- Integration test: hand-write a single-node workflow JSON, call `WorkflowExecutionManager.plan` directly, assert run + step rows materialize and the worker advances them to terminal.

**Phase 2 — Step library + branching execution.**
- Implement remaining v1 step types: `wait_and_check_communication`, `fub_reassign`, `fub_move_to_pond`, plus the proof-of-plugin `slack_notify`.
- Implement merge-point dependency tracking on `workflow_run_steps` (`depends_on_node_ids` + decrement-on-completion).
- Implement transition application for fan-out (multiple next nodes) and terminal outcomes.
- Contract tests for transitions (analog of `PolicyStepTransitionContractTest`), DAG validator tests (cycles, unreachable nodes, undeclared result codes), end-to-end test that runs the canonical 3-step ASSIGNMENT pipeline as a workflow and asserts behavior matches the old engine.

**Phase 3 — Trigger routing + admin API.**
- `WorkflowTriggerRouter` wired into `WebhookEventProcessorService` (additive — runs after, not instead of, the existing assignment dispatch). Same fan-out per lead. Same idempotency guarantees.
- `AdminWorkflowController` + `AdminWorkflowExecutionController` with the endpoints listed above. Optimistic locking via `expectedVersion` like today's policy controller.
- `GET /admin/workflows/step-types` powered by the registry.

**Phase 4 — Visual builder UI.**
- New `ui/src/modules/workflows/` module. React Flow canvas, palette from `step-types`, JSON-schema-driven node config forms.
- Runs tab + run inspector adapted from `ui/src/modules/policies/ui/RunInspector.tsx` and `StepTimeline.tsx`. v1 can render the run as a list of node-instances; graph visualization for a run is a follow-up.
- New top-level nav entry "Workflows" sibling to "Policies".

**Phase 5 — Migration + decommission of old engine.**
- Author the canonical `ASSIGNMENT_FOLLOWUP_SLA_V1` as a workflow in the new engine. Activate it. **At this point both engines fire on the same event** — duplicate side effects are a real risk, so this phase requires either (a) disabling the old policy's `enabled` flag, or (b) using the `slack_notify` step in the new workflow as a no-op shadow first to verify execution parity before flipping the action steps live.
- Once parity is confirmed, mark the old policy INACTIVE, leave the old code in place for one release as a rollback path, then delete `service/policy/`, `controller/AdminPolicy*`, and `ui/src/modules/policies/` in a follow-up cleanup.

The user's answer was **greenfield engine alongside old**, so phases 1–4 are pure additive work with no risk to the running system. Phase 5 is the only one with crossover risk and gets explicit shadow-mode handling.

---

## Critical files to be created (Phase 1–3)

**Backend (new):**
- `src/main/resources/db/migration/V{next}__create_workflow_engine.sql`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepType.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepRegistry.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowGraphValidator.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionManager.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionDueWorker.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/WaitAndCheckClaimWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/WaitAndCheckCommunicationWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubReassignWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubMoveToPondWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/SlackNotifyWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/persistence/entity/WorkflowEntity.java`, `WorkflowRunEntity.java`, `WorkflowRunStepEntity.java`
- `src/main/java/com/fuba/automation_engine/persistence/repository/WorkflowRepository.java`, `WorkflowRunRepository.java`, `WorkflowRunStepRepository.java` (the step repo holds the `claimDuePendingSteps()` analog)
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowController.java`
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowExecutionController.java`

**Backend (modified):**
- `src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java` — additive: invoke `WorkflowTriggerRouter` after the existing policy fan-out. Existing `processAssignmentDomainEvent` untouched.

**Frontend (new):**
- `ui/src/modules/workflows/**` — full module
- `ui/src/platform/ports/workflowPort.ts`, `workflowExecutionPort.ts`

### Existing code to extract/reuse (do not duplicate)
- The retry/transient-classification helper inside `WaitAndCheckClaimStepExecutor` (`executeWithRetry`) and the FUB person-fetch glue should move to a small shared util in `service/fub/` and be called by both the old executors and the new workflow steps. This is the only refactor inside the old engine.
- `claimDuePendingSteps()` SQL pattern from `PolicyExecutionStepRepository` should be copied verbatim (different table) into `WorkflowRunStepRepository`.
- Idempotency-key construction logic from `PolicyExecutionManager.buildIdempotencyKey` (lines ~216–235) should be lifted into a shared helper and called from both managers.

---

## Verification

**Phase 1 — engine skeleton:**
- Unit test `WorkflowGraphValidatorTest`: rejects cycles, undeclared result codes, unreachable nodes, missing entry node.
- Integration test `WorkflowEngineSmokeTest` (analogous to `AdminPolicyExecutionServiceTest`): plant a 1-node `wait_and_check_claim` workflow, call `WorkflowExecutionManager.plan`, run the worker once, assert the run reaches `COMPLETED`.
- `mvn test -Dtest='Workflow*Test'`

**Phase 2 — branching:**
- `WorkflowParityTest`: build a workflow that mirrors `ASSIGNMENT_FOLLOWUP_SLA_V1` exactly, run the same scenarios as `OnCommunicationMissActionStepExecutorTest` / `WaitAndCheckClaimStepExecutorTest` / `WaitAndCheckCommunicationStepExecutorTest`, assert run outcomes and step result codes match the old engine row-for-row.
- `WorkflowFanOutTest`: workflow with a node whose `transitions[X]` lists two next nodes; assert both activate, both run, run completes only after both reach terminal.
- `WorkflowMergeTest`: diamond DAG; assert merge node activates only after both predecessors complete.

**Phase 3 — API + routing:**
- `AdminWorkflowControllerTest`: CRUD + activate happy path, optimistic-lock conflict, validator rejection.
- `WorkflowTriggerRouterTest`: feed an `ASSIGNMENT/peopleCreated` webhook, assert one `workflow_runs` row per matching workflow per lead, assert idempotency on replay.

**Phase 4 — UI:**
- `npm run lint && npm run build` in `ui/`
- Manual: load the new Workflows tab, drag two nodes onto the canvas, connect them via a result-code edge, configure each from the schema-driven form, save, activate, fire a test webhook, observe the run advance in the run inspector.

**Phase 5 — migration:**
- Shadow mode: run the new workflow with `slack_notify` instead of `fub_reassign` for one production-like cycle. Compare counts of `workflow_runs.status` against `policy_execution_runs.status` for the same time window. Variance = bug.
- Cutover: disable old policy via its `enabled=false` flag (does **not** delete the row; instant rollback by re-enabling). Re-point new workflow at `fub_reassign`.

---

## Open questions parked for later
These were intentionally **deferred** based on the user's scope answers, but should be noted so we don't paint ourselves into a corner:

- **Conditional / filter logic on triggers** ("only run if lead source = Zillow"). Adding this later means: extending the `trigger` JSON with a small expression (start with literal field-equality predicates, no DSL), and evaluating it in `WorkflowTriggerRouter`. The graph schema doesn't change.
- **Per-edge guards** ("take this branch only if X"). Adding this later means making `transitions[code]` optionally an object `{when: <expr>, next: [...]}`. Backwards compatible.
- **New trigger event types** beyond `ASSIGNMENT`. Mostly a routing concern — `WorkflowTriggerRouter` needs to learn about more `(eventDomain, eventAction)` pairs and the `WebhookEventProcessorService` needs to call it from more event handlers. No engine change.
- **Multi-tenant isolation.** The current scope is single-tenant (internal ops team). If end-customer authoring ever lands, we will need: `workflow.tenant_id`, sandboxed step execution (especially `slack_notify` and any future HTTP-call step), per-tenant rate limits, and a much stricter validator on `config` JSON.
