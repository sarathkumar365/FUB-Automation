# How the Workflow Engine Works — A Walkthrough

**Audience:** You, a stakeholder, or any engineer new to this codebase who needs the whole picture in one sitting before touching the engine.
**Approach:** One realistic example traced end-to-end, from the moment a webhook hits the server to the moment the last step completes. Every mechanism (claiming, delays, branching, retries) is explained against the same example, with file paths and line numbers so you can jump to the code.

---

## 0. The Example We'll Trace

A four-node workflow: wait 5 minutes, check the lead's priority, then create a FUB task with a different assignee depending on whether the lead is high-priority or not.

```json
{
  "schemaVersion": 1,
  "entryNode": "wait_5m",
  "nodes": [
    { "id": "wait_5m",
      "type": "delay",
      "config": { "delayMinutes": 5 },
      "transitions": { "DONE": ["branch_check"] } },

    { "id": "branch_check",
      "type": "branch_on_field",
      "config": {
        "expression": "trigger.priority",
        "resultMapping": { "high": "HIGH", "low": "LOW" },
        "defaultResultCode": "MEDIUM"
      },
      "transitions": {
        "HIGH":   ["task_urgent"],
        "LOW":    ["task_standard"],
        "MEDIUM": ["task_standard"]
      } },

    { "id": "task_urgent",
      "type": "fub_create_task",
      "config": { "assignedUserId": 42 },
      "transitions": { "SUCCESS": { "terminal": "COMPLETED" } } },

    { "id": "task_standard",
      "type": "fub_create_task",
      "config": { "assignedUserId": 99 },
      "transitions": { "SUCCESS": { "terminal": "COMPLETED" } } }
  ]
}
```

This workflow is stored in `automation_workflows.graph` as JSONB. Its `status` is `ACTIVE` and it has a `trigger` column describing which webhook events should fire it.

---

## 1. The Two Levels to Keep in Your Head

The engine has **two separate pieces of state** for any workflow, and most confusion comes from mixing them up:

1. **The definition** — the workflow the author drew. Stored in `automation_workflows`. Immutable from the engine's perspective (authors edit it, the engine only reads it).
2. **The run** — one specific execution of that definition, triggered by one specific webhook event. Stored in `workflow_runs` (one row) and `workflow_run_steps` (one row per node). These rows are the *live state machine* — the engine reads and writes them constantly.

Critically: when a run is planned, the engine **freezes a snapshot** of the definition into `workflow_runs.workflow_graph_snapshot`. From that point on, the run refers only to the snapshot. If someone edits the definition five minutes later, in-flight runs are unaffected. This is how we get safe deploys and predictable behavior.

---

## 2. Webhook Arrives → Run Gets Planned

### 2.1 Webhook comes in

File: `src/main/java/com/fuba/automation_engine/controller/WebhookIngressController.java`

The controller exposes `POST /webhooks/{source}` (e.g. `/webhooks/fub`). It reads the raw body, grabs the headers, and hands off to `WebhookIngressService.ingest()`.

### 2.2 Ingest persists and dispatches

File: `src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java`, method `ingest()` (lines 59–157).

The ingest service does four things:
1. Verifies the webhook signature.
2. Parses the raw body into a `NormalizedWebhookEvent`.
3. **Persists** a row in `webhook_events` — this is the audit trail and the deduplication anchor.
4. If the event type is supported, hands the event to `AsyncWebhookDispatcher`, which calls `WebhookEventProcessorService.process()` asynchronously so the HTTP response returns fast.

### 2.3 Trigger router picks which workflows to run

File: `src/main/java/com/fuba/automation_engine/service/workflow/trigger/WorkflowTriggerRouter.java`, method `route()` (lines 46–193).

Important mental model: **triggers are not steps.** Triggers are evaluated *before* any run exists. A trigger's only job is to answer "does this webhook event cause this workflow to start, and if so, for which entity (lead)?"

The router:
1. Loads all ACTIVE workflows.
2. For each workflow, looks up its trigger type in `WorkflowTriggerRegistry` (today there's just `FubWebhookTriggerType`).
3. Calls `triggerType.matches(...)` with the event — returns true/false.
4. If matched, calls `triggerType.extractEntities(...)` — returns the lead ID(s) this event pertains to.
5. For each `(workflow, lead)` pair, calls `WorkflowExecutionManager.plan(...)` to actually create a run.

For our example, suppose a FUB webhook arrives for a newly-created lead. The trigger matches, the lead ID is extracted, and planning begins.

### 2.4 Planning creates the run and materializes every step

File: `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionManager.java`, method `plan()` (lines 68–130).

This is the most important method in the whole engine. In one transaction it:

1. **Checks idempotency.** Builds an `idempotencyKey` from `workflow_key + source + sourceLeadId + eventId` (SHA-256). If a run with this key already exists, returns `DUPLICATE_IGNORED`. This is how duplicate webhook deliveries don't cause duplicate runs.
2. **Loads and validates** the workflow graph.
3. **Creates one `workflow_runs` row** with:
   - `status = PENDING`
   - `workflow_graph_snapshot` = a deep copy of the graph (the freeze mentioned earlier)
   - `trigger_payload` = the frozen event payload (step configs will template against this later)
   - `idempotency_key`, `source_lead_id`, `webhook_event_id` — audit / dedupe fields
4. **Materializes every node** as a `workflow_run_steps` row (method `materializeSteps()`, lines 132–170).

After step 4, our example run has **four** `workflow_run_steps` rows sitting in the database:

| node_id         | status              | due_at       | pending_dependency_count |
|-----------------|---------------------|--------------|--------------------------|
| `wait_5m`       | PENDING             | now + 5 min  | 0                        |
| `branch_check`  | WAITING_DEPENDENCY  | null         | 1                        |
| `task_urgent`   | WAITING_DEPENDENCY  | null         | 1                        |
| `task_standard` | WAITING_DEPENDENCY  | null         | 1                        |

Two things to notice:
- **The entry node is the only one that's PENDING.** Everything else is WAITING_DEPENDENCY because it has upstream predecessors.
- **`delayMinutes` from the entry node's config is already baked into `due_at`.** The delay step itself will be a no-op at execution time; the wait is enforced by `due_at` + the claim query's filter. This is the key trick of the engine.

At this point, nothing is running. The transaction commits. The HTTP request that delivered the webhook has long since returned. The run just sits in the database, waiting.

---

## 3. The Worker Claim Loop — How Steps Actually Run

Nothing in the engine is event-driven in-process. Everything is database-polled. This is intentional: it survives restarts, scales horizontally, and every piece of state is inspectable via SQL.

### 3.1 The polling worker

File: `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionDueWorker.java`, method `pollAndProcessDueSteps()` (lines 39–77).

A `@Scheduled` method wakes up every ~2 seconds (configurable via `workflow.worker.poll-interval-ms`). Each tick it:
1. Runs stale-recovery (rescues steps stuck in PROCESSING from a crashed prior tick).
2. Asks the claim repo for a batch of due, PENDING steps.
3. For each claimed step, calls `WorkflowStepExecutionService.executeClaimedStep(stepId)`.

### 3.2 The claim query — the heart of concurrency safety

File: `src/main/java/com/fuba/automation_engine/persistence/repository/JdbcWorkflowRunStepClaimRepository.java`, method `claimDuePendingSteps()` (lines 96–108).

```sql
WITH due AS (
  SELECT steps.id
    FROM workflow_run_steps steps
    JOIN workflow_runs runs ON runs.id = steps.run_id
   WHERE steps.status  = 'PENDING'
     AND steps.due_at <= :now
     AND runs.status   = 'PENDING'
   ORDER BY steps.due_at, steps.id
   LIMIT :limit
   FOR UPDATE OF steps SKIP LOCKED
)
UPDATE workflow_run_steps steps
   SET status = 'PROCESSING', updated_at = :now
  FROM due
 WHERE steps.id = due.id
RETURNING ...
```

Two Postgres features do all the heavy lifting:

- **`FOR UPDATE ... SKIP LOCKED`** — if two workers poll at the same time, each grabs a disjoint set of rows. No double-execution, no distributed lock service.
- **`due_at <= :now`** — this is why `delay` works. A step with `due_at = now + 5min` is simply invisible to the claim query until its time arrives.

A successfully claimed step transitions **PENDING → PROCESSING** atomically. If the worker crashes while the step is in PROCESSING, the stale-recovery path (section 6) will eventually rescue it.

---

## 4. Executing a Single Step

File: `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java`, method `executeClaimedStep()` (lines 61–135).

Let's walk the claim of `wait_5m` — the very first step to execute in our example — and then `branch_check`, to see how data flows between them.

### 4.1 Load and sanity-check

The method loads the step row and its parent run. If the run isn't PENDING (already COMPLETED, FAILED, CANCELED), it skips the step. This prevents orphan steps from running after a run has been terminated by another branch.

### 4.2 Look up the step type from the registry

```java
WorkflowStepType stepType = stepRegistry.get(step.getStepType());
```

The registry (`WorkflowStepRegistry`) was populated at Spring startup by scanning every `@Component` that implements `WorkflowStepType` and indexing them by their `id()`. So `"delay"` → `DelayWorkflowStep`, `"branch_on_field"` → `BranchOnFieldWorkflowStep`, etc.

### 4.3 Build RunContext — the data the step sees

Method `buildRunContext()` (lines 197–215). This is how state flows through the run:

```
RunContext {
  metadata:        (runId, workflowKey, workflowVersion)
  triggerPayload:  the frozen webhook event payload
  sourceLeadId:    the entity this run is about
  stepOutputs:     map<nodeId, outputs> of every COMPLETED prior step
}
```

Notice `stepOutputs` is rebuilt from the database on every step execution. No in-memory run-level object. If `wait_5m` ran an hour ago and the worker crashed five times since, the context for `branch_check` is still perfectly reconstructable by querying `workflow_run_steps WHERE run_id = ? AND status = 'COMPLETED'`.

### 4.4 Resolve config templates (JSONata)

Method `resolveConfigTemplates()` (lines 218–227). The authored config may contain templates like `"${trigger.priority}"` or `"${steps.branch_check.expressionResult}"`. Each string value is run through `ExpressionEvaluator.resolveTemplate()`, which evaluates JSONata expressions against the `RunContext`.

Resolved config is persisted to `workflow_run_steps.resolved_config`, so debugging "what did this step actually see?" is trivial — you just read the column.

For `wait_5m` there's nothing to resolve. For a later step, `"${trigger.priority}"` would become `"high"` at this point.

### 4.5 Call execute()

```java
StepExecutionResult result = stepType.execute(ctx);
```

For `DelayWorkflowStep` (`src/main/java/.../steps/DelayWorkflowStep.java`), `execute()` is essentially a no-op — it returns `StepExecutionResult.success("DONE")` immediately. The delay has already been enforced by `due_at`; execute has nothing to do.

For `BranchOnFieldWorkflowStep` (`src/main/java/.../steps/BranchOnFieldWorkflowStep.java`, lines 69–111), `execute()`:
1. Evaluates the JSONata `expression` (`trigger.priority`) against the RunContext → gets `"high"`.
2. Looks up `"high"` in `resultMapping` → gets `"HIGH"`.
3. Writes `{ "expressionResult": "high" }` into the step's `outputs` (so downstream steps could reference `steps.branch_check.expressionResult` if they wanted).
4. Returns `StepExecutionResult.success("HIGH")`.

### 4.6 Persist outputs and hand off to transitions

On success the service marks the step COMPLETED, writes `result_code` and `outputs`, then calls `applyTransition()`.

---

## 5. Transitions — How the Next Step Is Decided

Method `applyTransition()` (lines 259–294). This is where the graph is walked.

The method reads the current node's `transitions` map from the **graph snapshot**, then looks up the result code we just produced. There are three possible shapes:

### Case A: A list of next nodes — fan-out or single-next

`"transitions": { "DONE": ["branch_check"] }` — the list has one entry, so "single next." `"transitions": { "DONE": ["notify_slack", "create_task"] }` would be two-way fan-out.

Method `activateNextNodes()` (lines 318–339) does the activation. For each target node:
1. Find its existing `workflow_run_steps` row (remember, it was materialized at plan time with status WAITING_DEPENDENCY).
2. Decrement `pending_dependency_count`.
3. **If the count reached 0**, flip status WAITING_DEPENDENCY → PENDING and set `due_at` from the target node's own `delayMinutes` config (or to now).
4. Save.

This is the **join mechanism**. A node with two predecessors is materialized with `pending_dependency_count = 2`, and becomes runnable only after *both* predecessors complete. No locks, no coordinator — just integer arithmetic on a row.

### Case B: A terminal marker — the run ends here

`"transitions": { "SUCCESS": { "terminal": "COMPLETED" } }` — the value is the special object `{"terminal": "COMPLETED"}`.

Method `applyTerminalTransition()` (lines 296–316):
1. Marks every non-terminal step (PENDING, WAITING_DEPENDENCY) as SKIPPED.
2. Sets `workflow_runs.status` to COMPLETED (or FAILED).

This is how our example ends: `task_urgent` completes with result `SUCCESS`, the transition is `{"terminal": "COMPLETED"}`, and the run is done.

### Case C: No transition for this result code

An author error or an unmapped `default` branch. The run is marked FAILED with an "unhandled result code" reason.

### Tracing our example

1. `wait_5m` completes → result `DONE` → transition `["branch_check"]` → `branch_check.pending_dependency_count` 1 → 0 → status becomes PENDING → `due_at = now`.
2. Worker claims `branch_check` on its next tick → evaluates `trigger.priority` → returns result `HIGH` → transition `["task_urgent"]` → `task_urgent` becomes PENDING.
3. Note: `task_standard` is **still sitting in WAITING_DEPENDENCY with count = 1**. Because `branch_check` routed only to `task_urgent`, nothing ever decrements `task_standard`. It will be SKIPPED when the terminal transition runs on `task_urgent`.
4. Worker claims `task_urgent` → `FubCreateTaskWorkflowStep.execute()` makes a real HTTP call to Follow Up Boss → returns `SUCCESS`.
5. Transition is `{"terminal": "COMPLETED"}` → `task_standard` gets marked SKIPPED → run.status = COMPLETED.

---

## 6. Failures, Retries, and Stale Recovery

Three failure modes; all land in `WorkflowStepExecutionService`.

### 6.1 Transient failure → retry with backoff

If `execute()` returns `StepExecutionResult.transientFailure(...)` — typical for an HTTP 503 or a network blip — the service consults the step's effective `RetryPolicy` (merging the step type's default with any `retryPolicy` override in the config). If `retry_count < maxAttempts - 1`:
- `retry_count` is incremented.
- Status stays PENDING.
- `due_at` is bumped to `now + exponentialBackoff(retry_count)`.
- The step will simply be re-claimed when its new `due_at` arrives.

The claim query doesn't care that this is a "retry" — it's just a PENDING row with a future `due_at`, like any other.

### 6.2 Permanent failure or retries exhausted → run fails

`markStepAndRunFailed()` (lines 407–420): step.status = FAILED, run.status = FAILED (if still PENDING). No cascading SKIP of other steps — any in-flight sibling branches will finish their claim and discover the run is no longer PENDING when they try to execute, then no-op out.

### 6.3 Worker crash → stale recovery

If a worker claims a step (PENDING → PROCESSING) and then the JVM dies, the row is stuck in PROCESSING forever. The stale-recovery sweep (method `runStaleProcessingRecovery()` in the worker, lines 95–118) finds rows where `status = PROCESSING AND updated_at <= now - staleTimeout` and either:
- Requeues them (PROCESSING → PENDING, reset `due_at`, increment `stale_recovery_count`) if under the requeue limit, or
- Marks them FAILED if they've been stale-recovered too many times.

This is why `FOR UPDATE SKIP LOCKED` in the claim query is safe: a crashed worker's lock is released by Postgres when the connection dies, so a replacement worker can pick the row up on the next tick.

---

## 7. The Database, in One Table

Three tables do essentially all the work.

### `automation_workflows` — the authored definition

| Column   | Meaning                                             |
|----------|-----------------------------------------------------|
| `key`    | Stable identifier, normalized                       |
| `graph`  | JSONB — `{entryNode, nodes, schemaVersion}`         |
| `trigger`| JSONB — trigger type + config                       |
| `status` | DRAFT / ACTIVE / INACTIVE                           |
| `version`| Optimistic-lock version                             |

### `workflow_runs` — one row per execution

| Column                    | Meaning                                              |
|---------------------------|------------------------------------------------------|
| `workflow_graph_snapshot` | Frozen graph — run reads only this, never the live graph |
| `trigger_payload`         | Frozen event payload                                 |
| `status`                  | PENDING / COMPLETED / FAILED / CANCELED / DUPLICATE_IGNORED |
| `idempotency_key`         | UNIQUE — prevents duplicate runs from duplicate webhooks |
| `source_lead_id`          | The entity this run is about                         |

### `workflow_run_steps` — one row per node per run (the live state machine)

| Column                      | Meaning                                                |
|-----------------------------|--------------------------------------------------------|
| `node_id`                   | Stable ID from the graph                               |
| `step_type`                 | Registry key (`delay`, `branch_on_field`, ...)         |
| `status`                    | WAITING_DEPENDENCY / PENDING / PROCESSING / COMPLETED / FAILED / SKIPPED |
| `due_at`                    | When the step becomes claimable — this is how delays work |
| `pending_dependency_count`  | Decremented as predecessors complete; 0 means ready     |
| `depends_on_node_ids`       | Audit: which nodes we're waiting for                   |
| `config_snapshot`           | The authored config, frozen                            |
| `resolved_config`           | After JSONata template resolution (debugging gold)     |
| `result_code`               | The outcome the step returned                          |
| `outputs`                   | JSONB — downstream steps read these via `steps.<nodeId>` |
| `retry_count`               | For exponential backoff                                |
| `stale_recovery_count`      | For the stale-recovery sweep                           |

Key indexes: `(status, due_at)` on `workflow_run_steps` — this is what makes the claim query fast. `UNIQUE(run_id, node_id)` — one step row per node per run.

---

## 8. The Five Mechanisms, in One Sentence Each

If you want a cheat sheet:

1. **Delays** — a number in config becomes `due_at`; the claim query filters on it.
2. **Branching** — a step returns a string result code; the graph's `transitions` map picks the next node(s) by that string.
3. **Joins** — every node has a pre-computed `pending_dependency_count`; predecessors decrement it; the node runs when it hits 0.
4. **Retries** — same row, incremented `retry_count`, bumped `due_at`.
5. **Crash safety** — `FOR UPDATE SKIP LOCKED` + a stale-recovery sweep that rescues rows stuck in PROCESSING.

Everything else the engine does — triggers, templating, outputs-as-downstream-inputs, idempotency, per-run graph snapshots — is a thin layer over these five.

---

## 9. What's *Not* in the Engine (And Why It Matters for Extensions)

Now you can see clearly what the control-flow plan (`control-flow-step-types-plan.md`) is actually proposing to add:

- **Loops** don't fit anywhere in the picture above because the number of `workflow_run_steps` rows is decided at plan time and never grows. To support loops we'd need to add rows *after* plan time — "runtime expansion." Every other piece (claim query, retries, joins) works unchanged.
- **Sub-workflows** would mean one run spawning another run, with output passing across the boundary. The snapshot mechanism already isolates runs from each other, so this is a natural extension.
- **External-event waits** (wait-for-SMS) would need a new table mapping correlation keys to waiting step IDs, consulted by the webhook ingress path. It's the one proposed feature that *doesn't* fit cleanly into the existing claim-query pattern.

Everything proposed in the control-flow plan is additive — the mechanisms described in this document keep working exactly as they do today.

---

## Appendix — File Map

| Responsibility            | File                                                                                                    |
|---------------------------|---------------------------------------------------------------------------------------------------------|
| Webhook entry             | `controller/WebhookIngressController.java`                                                              |
| Ingest + persist + dedupe | `service/webhook/WebhookIngressService.java`                                                            |
| Async dispatch            | `service/webhook/dispatch/AsyncWebhookDispatcher.java`                                                  |
| Event processing          | `service/webhook/WebhookEventProcessorService.java`                                                     |
| Trigger matching          | `service/workflow/trigger/WorkflowTriggerRouter.java`                                                   |
| Run planning              | `service/workflow/WorkflowExecutionManager.java`                                                        |
| Poll loop                 | `service/workflow/WorkflowExecutionDueWorker.java`                                                      |
| Claim SQL                 | `persistence/repository/JdbcWorkflowRunStepClaimRepository.java`                                        |
| Step execution            | `service/workflow/WorkflowStepExecutionService.java`                                                    |
| Step registry             | `service/workflow/WorkflowStepRegistry.java`                                                            |
| Built-in step types       | `service/workflow/steps/*.java`                                                                         |
| Schema                    | `resources/db/migration/V10__create_workflow_engine_tables.sql`                                         |
