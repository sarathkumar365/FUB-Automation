# Domain Events — Phases

Each phase is independently shippable and reviewable. Complete in order. The phase order has been chosen so that **Phase 3 (local-state-first writes) lands before Phase 4 (trigger schema migration)** — otherwise the deployment window between the two would leave engine echoes producing phantom events under the new trigger shape, briefly making the system *worse* than today.

The "app is in dev phase" framing applies — drain protocols, in-flight migration shims, and reconciliation are deferred (see plan.md "Out of scope"). Each phase is verifiable in isolation but the bad-run-rate win arrives at Phase 4.

---

## Phase 0 — Replay harness
Status: `DONE` — framework + one synthesized fixture + four real DB-extracted fixtures (lead 20123, 20207, 20231, 20235) all passing in 132s. See [phase-0-implementation.md](./phase-0-implementation.md) for details.

**Goal:** A tool that takes a recorded sequence of webhook events (live or synthesized) and plays it through a test instance of the engine, asserting on the resulting workflow runs, domain events emitted, and FUB writes attempted.

Without this, Phases 2–5 are nearly impossible to validate. The field-observations.md learnings on 05-08, 05-11, 05-12 are exactly the kind of multi-event-in-time interactions unit tests miss.

### Deliverables
- A CLI / test harness that ingests a JSON / JSONL file of recorded webhook payloads with relative timing
- The harness drives `WebhookIngressService` directly (or a test seam just above it), respecting the inter-event gaps
- Mock FUB client that records outbound calls and returns canned responses
- Assertions: events emitted (kinds, payloads), workflow runs created/suppressed, FUB calls made
- Recorded fixtures for the three high-signal field-obs incidents:
  - Lead 20123 cascade (05-08)
  - Lead 20207 triple-run (05-11)
  - Lead 20235 FUB-burst (05-12)
  - Lead 20231 4-webhook burst (05-12)

### Verification
- Each recorded fixture plays back deterministically against the *current* engine and reproduces the documented bad behaviour (proves the harness has fidelity)
- Failing assertions are reported with enough context to debug (event timeline + expected-vs-actual)

### Exit criteria
- Harness can be invoked via `./mvnw` or a dedicated script
- README documents the fixture format and how to add new ones
- Three reproductions of historical incidents pass deterministically

### Why this is Phase 0
- Phases 2–5 each need it for credible verification
- Currently the project's only safety net against this class of bug is live production observation (field-observations learning #9); the harness moves that safety net into CI

---

## Phase 1 — Foundation
Status: `DONE` — webhook_event_id populated end-to-end (known-issue #25 resolved), `leads.previous_state` column added, validator refuses unknown `lead.*` references at save time. 528 tests pass; replay harness's Phase 1 invariant assertion holds across all 5 fixtures. See [phase-1-implementation.md](./phase-1-implementation.md) for details.

**Goal:** Cheap groundwork that has no behaviour change but unblocks everything downstream.

### Deliverables
- **Populate `workflow_runs.webhook_event_id` on every run** — the line at [WorkflowExecutionManager.java:~117](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionManager.java) already calls `run.setWebhookEventId(request.webhookEventId())`; trace the caller chain and ensure `request.webhookEventId()` is populated (resolves known-issue #25)
- **Thread `webhookEventId` through `RunContext`** — already present per Wave 2; verify and tighten if needed
- **Add `leads.previous_state` JSONB column** via Flyway migration; default `NULL`; populated by the diff layer in Phase 2
- **Workflow-creation-time field-reference validator** — runs at `POST /admin/workflows` and `PUT /admin/workflows/{id}`; refuses to save workflows that reference fields not captured in `leads.lead_details` or `leads.previous_state`; covers `change.*`, `lead.*` references
- **Audit `leads.lead_details` field coverage** — list every field referenced by today's workflows (just `agent_followup_enforcement` for now) plus product-discovery candidates; extend `LeadUpsertService.SNAPSHOT_FIELDS` if anything is missing

### Verification
- Existing test suite green after migration
- New unit test: workflow with `lead.fakeField` reference is refused at validation time with a clear error
- All current `agent_followup_enforcement` runs continue to be created with `webhook_event_id` populated (spot-check via `SELECT id, webhook_event_id FROM workflow_runs ORDER BY id DESC LIMIT 20`)

### Exit criteria
- Migration applied cleanly to dev DB
- Every new `workflow_runs` row has `webhook_event_id` set
- `leads.previous_state` column exists, all values `NULL`
- Validator refuses unknown field references at save time

### Repo decisions impact
TBD at phase-1-implementation.md time. Likely `No` — local feature concern.

---

## Phase 2 — Domain events table + diff machinery
Status: `NOT STARTED`

**Goal:** Webhooks produce typed `events` rows. Diff computed for state-change events. In-process dispatcher available; nothing subscribes yet.

### Deliverables
- **`events` table** Flyway migration per the schema in `plan.md`
- **`DomainEventEmitter`** service — `emit(eventKind, sourceSystem, sourceEventId, entityType, entityId, payload)` → inserts a row, optionally dispatches in-process
- **Diff at upsert** in `LeadUpsertService`:
  - Before upsert, read current `lead_details`
  - After upsert, compute diff against `previous_state` (which is set to the prior `lead_details`)
  - If diff non-empty: emit `lead.state_changed` event with `payload = { changed_fields, previous, current }`
  - Update `previous_state` to the pre-upsert `lead_details` for the next round
- **Append-event emission** in call/note ingestion paths:
  - `ProcessedCallUpsertService` emits `call.created` (or `call.updated`) after upsert
  - Note paths emit `note.created` (future-ready; only if a note ingestion path exists today)
- **`DomainEventDispatcher`** — in-process publish to a list of subscribers (Spring `ApplicationEventPublisher` or a simple `List<Listener>`); no consumers yet but the seam is there
- **Replay harness extended** to assert on emitted events

### Verification
- Replay the 05-08 echo cascade through the harness: expected to still produce a phantom `lead.state_changed` event because Phase 3 hasn't shipped yet (engine writes still cause diffs against pre-write local state)
- Replay the 05-12 FUB burst: expect **one** `lead.state_changed` event per logical change instead of N (the N-1 subsequent webhooks see no diff if FUB returns the same post-state, which the observations confirmed)
- Diff correctness unit tests on representative `lead_details` JSON pairs (added field, removed field, value change, no-op edit, nested change in `customFields`)

### Exit criteria
- `events` table populated for every webhook the engine processes
- No subscribers consume events yet; triggers still use the old path
- Replay harness fixtures show the FUB-burst collapse working

### Repo decisions impact
TBD.

---

## Phase 3 — Local-state-first engine writes
Status: `NOT STARTED`

**Goal:** Engine writes update local state before calling FUB. Echo webhooks see no diff. `EngineWriteTracker` is in place as race-window guard.

This phase must land **before** Phase 4 — otherwise re-authored workflows trip on their own echoes during the deployment window.

### Deliverables
- **`EngineWriteTracker` interface** + `InMemoryEngineWriteTracker` impl
  - `ConcurrentHashMap<TrackerKey, EngineWriteRecord>`
  - Scheduled eviction (30s TTL by default; configurable)
- **Wrap engine-write step types** (`fub_reassign`, `fub_move_to_pond`, `fub_create_note`, any other FUB-mutating step):
  - Before FUB call: read current local lead state for affected fields; update local state to intended new value; record in tracker
  - Call FUB
  - On FUB failure: revert local state; mark tracker entry failed; propagate error to step machinery (step fails as today)
  - On FUB success: leave local + tracker in place; echo webhook will diff to empty
- **Diff annotation in Phase 2's emitter:** when `lead.state_changed` would be emitted, consult the tracker — if a recent record matches the diff's fields and entity, annotate `payload.source = "ENGINE"`; emit the event anyway (filtering is the workflow's job per the opt-in default)

### Verification
- Replay 05-08 echo cascade: engine reassign → local state updated → echo webhook → diff = empty → no event emitted (verified by harness assertions)
- Replay 05-12 reassign-to-same-user case (lead 20235): three back-to-back reassigns produce one tracker record sequence; second and third see local already at target and emit no event
- Failure path: synthesize a FUB-PUT failure in the harness; verify local state reverts and step fails

### Exit criteria
- Echo webhooks (matched by tracker) produce `source = ENGINE` annotation OR no event (depending on diff)
- FUB write failures revert local state cleanly
- Tracker metrics logged for observability (hit rate, eviction rate, failed-write rate)

### Repo decisions impact
Probably `No`. The local-state-first write pattern is feature-internal; if it generalises to a project-wide rule for "engine writes always update local first," then promote.

---

## Phase 4 — Trigger schema migration + expression scope
Status: `NOT STARTED`

**Goal:** Workflows subscribe to domain events. New trigger schema in effect. `agent_followup_enforcement` re-authored against the new shape. Bad-run rate actually drops here.

This is the user-facing phase. Everything before it is plumbing.

### Deliverables
- **New trigger schema** in workflow JSON: `{ "on": "<event_kind>", "filter": "<JSONata expression>" }`
- **Workflow JSON validator** updated: accepts new shape; rejects the old `peopleUpdated`-typed trigger with a clear migration error message
- **`WorkflowTriggerRouter` refactored** to subscribe to `DomainEventDispatcher` instead of receiving raw webhook events:
  - Receives a `DomainEvent`
  - Looks up workflows registered for that `event_kind`
  - Evaluates each workflow's filter expression against the new scope
  - On match, calls `WorkflowExecutionManager.plan` with `domain_event_id` and the proximate `webhook_event_id`
- **`workflow_runs.domain_event_id`** column (new) + `workflow_runs.suppressed_by_run_id` column (new, used in Phase 5)
- **Expression scope refactor:**
  - `event.*` now refers to the domain event, not the webhook
  - `change.*` sugar over `event.payload` for state-change events
  - `webhook.*` exposes the proximate raw webhook payload (for steps that need source-system fields)
  - `lead.*` available in trigger filter scope too (closes #17)
  - `WorkflowStepExecutionService.buildRunContext` updated accordingly
- **Re-author `agent_followup_enforcement`** workflow JSON:
  - Trigger becomes `{ "on": "lead.state_changed", "filter": "change.assignedUserId.changed AND change.source != 'ENGINE'" }`
  - Any step expressions using `event.payload.*` migrate to `webhook.payload.*` if needed
  - Verify in field-observations replay that bad-run rate drops

### Verification
- Replay the three high-signal incidents end-to-end:
  - **Lead 20123 (05-08 echo cascade)** — under new architecture: one event for the real assignment, engine reassign produces no echo event (Phase 3), workflow does not self-trigger. Bad runs = 0.
  - **Lead 20235 (05-12 FUB burst)** — one event for the burst (Phase 2 collapse), one run created, one reassign. Bad runs = 0.
  - **Lead 20207 (05-11 triple-run)** — first event creates run; echo run suppressed by no-event-emission (Phase 3); unknown peopleUpdated either filtered semantically or absorbed by Phase 5's run dedup.
- Workflow JSON validation refuses old-shape triggers with a migration hint
- All step expressions in the re-authored workflow resolve without error

### Exit criteria
- `agent_followup_enforcement` running entirely on the new pipeline
- Replay harness shows bad-run rate <5% on recorded field-obs traffic (residual = genuine multi-transition cases that supersede semantics would close)
- Old trigger path code paths can be deleted (hard cut)

### Repo decisions impact
Probably `Yes` — the trigger schema is part of the workflow JSON contract; document the new shape in a repo-decision.

---

## Phase 5 — Run-level uniqueness
Status: `NOT STARTED`

**Goal:** Hard-suppress duplicate runs of the same workflow on the same lead while one is active. Catches residual cases not handled by Phase 2's event-collapse (e.g., genuine distinct state transitions in quick succession).

### Deliverables
- **Partial unique index** on `workflow_runs`:
  ```sql
  CREATE UNIQUE INDEX uk_workflow_runs_active_per_lead
    ON workflow_runs (workflow_key, source_lead_id)
    WHERE status IN ('PENDING','RUNNING','BLOCKED');
  ```
- **`WorkflowExecutionManager.plan` updated:**
  - Attempt to insert as today
  - On unique-constraint violation from the new index, look up the active run
  - Insert a `SUPPRESSED` row with `suppressed_by_run_id` pointing to the active run
  - Do not schedule any step execution for the suppressed run
- **`status = 'SUPPRESSED'`** added to the run status enum + UI / admin filters acknowledge it
- **Suppression metrics** — count per workflow_key, surface in `/admin/workflows/{key}/runs` for ops visibility

### Verification
- Synthesize two near-simultaneous `lead.state_changed` events for the same lead in the harness; assert one run runs, one row is `SUPPRESSED` with a back-reference
- Cross-workflow case: two different workflows triggering on the same lead simultaneously both succeed (the partial unique is per `workflow_key`)
- Existing idempotency-key constraint still catches webhook replay

### Exit criteria
- Partial unique index created
- Planner handles conflicts cleanly with audit rows
- Suppressed runs visible in admin UI

### Repo decisions impact
Probably `No` — run uniqueness semantics are feature-internal.

---

## Phase order rationale

| Order | Why |
|---|---|
| 0 → 1 | Harness before everything because every later phase needs it for honest verification |
| 1 → 2 | Diff machinery needs `previous_state` column and webhook_event_id linkage to exist |
| 2 → 3 | Local-state-first writes need the diff layer in place to annotate `source = ENGINE` |
| **3 → 4** | **Critical**: if 4 ships before 3, re-authored workflows trip on their own echoes during the deployment window |
| 4 → 5 | Run uniqueness only matters once workflows actually subscribe to events |

## Non-goals across all phases

- No supersede / cancel-on-new-event behaviour (deferred — see plan.md "Out of scope")
- No reconciliation / catch-up for missed webhooks
- No drain protocol at deploy time (dev phase)
- No retention policy enforcement on `events` or `previous_state`
- No Redis backend for `EngineWriteTracker` (interface ready; swap when Redis lands)
- No multi-CRM adapters (schema is ready via `source_system`; no second source today)
