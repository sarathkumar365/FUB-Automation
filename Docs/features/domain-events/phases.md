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

> **Note:** Phase 1 was implemented under the older `Lead` naming. The Pre-Phase-2 rename pass (next entry) sweeps everything to `Person`. Phase 1's implementation log preserves the original `Lead` wording as a historical record.

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

## Pre-Phase-2 — Rename `Lead` → `Person` + drop ingest filter
Status: `NOT STARTED`

**Goal:** Clean substrate for Phase 2's vocabulary. Strictly mechanical rename, plus one bundled behaviour change (drop the `isFubLeadPerson` ingestion filter).

Why this slot in the phase order: Phase 2 is about to introduce the `events` table, the `event_kind` enum (`person.state_changed`, etc.), the validator's `change.*` vocabulary, and emission code. Locking these around the existing `Lead` name would require a rename migration of the events table later. Doing the rename first means every downstream phase uses the right vocabulary from day one.

### Why `Person` (not `Contact`, `Party`, or staying with `Lead`)

`Lead` conflates the CRM-contact entity with the FUB `stage` value `"Lead"`. They are different concepts: the entity is a human in the CRM; `stage` (Lead / Customer / Past Client / Trash / custom) is one of many attributes. FUB's own API calls them `/v1/people`.

- **`Person`** — matches FUB's API. No collision with any major CRM's named entity. Salesforce/HubSpot/Zoho/Dynamics adapters each translate their own term (`Lead`, `Contact`) into our `Person`. Workflows filter relationship type via `person.kind` (our normalized enum — see below) or `person.stage` (FUB's raw stage string).
- **`Contact`** rejected: collides with Salesforce's specific `Contact` entity (which is distinct from SF's `Lead`). Would be actively misleading in a multi-CRM context.
- **`Party`** rejected: most abstract, no CRM uses it natively, solves problems we don't have (organizations).
- Staying with `Lead` rejected: bakes the conceptual mismatch deeper with every phase.

### Deliverables

**Schema (V21 migration — single file, single transaction):**
- Rename `leads` → `persons` (table name; plural form chosen for SQL clarity)
- Rename `source_lead_id` → `source_person_id` on `persons`, `workflow_runs`, `webhook_events`, `processed_calls`
- Rename `lead_details` → `person_details` (the JSONB column on `persons`)
- `previous_state` keeps its name (already generic)
- Rename constraints: `uk_leads_source_system_source_lead_id` → `uk_persons_source_system_source_person_id`; `chk_leads_status` → `chk_persons_status`
- Rename indexes: `idx_leads_*` → `idx_persons_*`; `idx_processed_calls_lead_started` → `idx_processed_calls_person_started`
- Update V19 `chk_webhook_events_normalized_domain` CHECK constraint: allow `PERSON` (renamed from `LEAD`) and `NOTE` (added for Phase 2's note handling). Existing rows updated via `UPDATE webhook_events SET normalized_domain='PERSON' WHERE normalized_domain='LEAD'` before constraint re-add.
- **Add `kind` column on `persons`** — `VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'` with CHECK constraint allowing `LEAD`/`AGENT`/`REALTOR`/`UNKNOWN`. Indexed (`idx_persons_kind`) for admin-query performance.
- Backfill `persons.kind` from existing `person_details.stage` via a case-insensitive mapping (`lead`→LEAD, `agent`→AGENT, `realtor`→REALTOR, else UNKNOWN).

**Java renames + new types:**
- `LeadEntity` → `PersonEntity` (also gains a `kind` field — `@Enumerated(EnumType.STRING) PersonKind kind`)
- `LeadRepository` → `PersonRepository` (method `findBySourceSystemAndSourceLeadId` → `findBySourceSystemAndSourcePersonId`)
- `LeadStatus` → `PersonStatus`
- `LeadUpsertService` → `PersonUpsertService` (package `service/lead/` → `service/person/`)
- `LeadSnapshotResolver` → `PersonSnapshotResolver` (also exposes `kind` in the JSONata scope map)
- `LeadAdminQueryService` → `PersonAdminQueryService`
- `LeadFeedCursorCodec` → `PersonFeedCursorCodec`
- `WebhookEventProcessorService.processLeadDomainEvent` → `processPersonDomainEvent`
- `upsertLeadFromAssignmentEvent` → `upsertPersonFromEvent` ("assignment" wording was always misleading)
- `NormalizedDomain.LEAD` → `PERSON`
- **New enum `PersonKind`** — `LEAD`, `AGENT`, `REALTOR`, `UNKNOWN`. Lives in `persistence/entity/`. Pattern matches existing `PersonStatus`.
- **New `PersonUpsertService.mapStageToKind(String stage) → PersonKind`** — case-insensitive mapping helper. Called during upsert; logs WARN when an unmapped stage hits UNKNOWN so unrecognised stages surface fast.
- **`PersonUpsertService.capturedFieldNames()`** updated — returns `SNAPSHOT_FIELDS + {"kind"}` so the workflow validator accepts `person.kind` as a known field.

**Runtime vocabulary:**
- `RunContext.lead` → `RunContext.person`
- `ExpressionScope` key `lead` → `person`
- `WorkflowGraphValidator.LEAD_EXPRESSION_PATTERN` / `LEAD_TEMPLATE_PATTERN` → `PERSON_*`
- `validateLeadFieldReferences` → `validatePersonFieldReferences` (error message updated)
- `LeadUpsertService.SNAPSHOT_FIELDS` → `PersonUpsertService.SNAPSHOT_FIELDS` (membership unchanged)

**Workflow JSON sweep** (the one production workflow):
- `agent_followup_enforcement.workflow.json`:
  - `eventDomain: "LEAD"` → `eventDomain: "PERSON"`
  - `{{ lead.assignedUserId }}` → `{{ person.assignedUserId }}`
  - `{{ lead.assignedTo }}` → `{{ person.assignedTo }}`
  - `$boolean(lead.assignedUserId)` (in the `gate_assigned` node) → `$boolean(person.assignedUserId) and person.kind = "LEAD"`
  - **The `person.kind = "LEAD"` predicate is mandatory.** It compensates for the dropped `isFubLeadPerson` ingest filter. Uses our normalized `kind` enum, not FUB's raw `stage` string — stable across FUB stage customizations.

**Replay harness sweep:**
- `personSnapshots` field already correctly named — no change
- `ReplayFixture.Expected.minWorkflowRunsForLead` → `minWorkflowRunsForPerson`
- All 5 fixture JSONs swept

**Docs sweep:**
- `plan.md` and `phases.md` (this file) — `lead.*` → `person.*` throughout (already done in plan.md as part of this Pre-Phase-2 prep)
- `known-issues.md` — references to entity names updated; references to `lead.*` JSONata vocabulary updated
- `phase-1-implementation.md` — keep as-is (historical record); top-line note added
- `field-observations.md` — keep as-is (historical record; "lead" wording reflects what was observed)

**Bundled behaviour change — drop `isFubLeadPerson` filter:**
- Today's `LeadUpsertService` skips upsert if `personPayload.stage != "Lead"`.
- Post-rename `PersonUpsertService` removes this check; every person FUB sends a webhook for gets persisted, with the `kind` column populated via `mapStageToKind`.
- Trade-off: storage grows (agents, realtors, brokers all get rows); workflows must filter via `person.kind = "LEAD"` (or whatever kind they care about) in their trigger predicate. The single existing workflow gets this predicate added at the same time.
- Rationale: keeps the entity definition honest (`Person` is FUB's `/v1/people`, not a filtered subset) and unblocks future workflows that want to react to other kinds of persons.

### Verification
- All 525+ tests green after the rename + filter drop (525 baseline after Part A's 3 policy-test deletions).
- Replay harness's 5 fixtures still pass (with the renamed files: `person-*.json`).
- `agent_followup_enforcement` workflow still functional end-to-end (with the new `person.kind = "LEAD"` predicate in the `gate_assigned` node's expression).
- Manual behaviour test: POST a `peopleUpdated` webhook for a stage=Agent person → row persists in `persons` with `kind = AGENT`; the workflow does NOT fire.
- POST a stage=Lead person → row persists with `kind = LEAD`; workflow fires (current behaviour preserved).
- `grep -rE 'Lead[A-Z]|\bleads\b|source_lead_id|lead_details|NormalizedDomain\.LEAD' src/main src/test` returns zero matches in production code; in tests only intentional historical-record comments.

### Exit criteria
- V21 migration applies cleanly to dev DB.
- `persons` table exists with `kind` column populated for every row (backfilled from `person_details.stage`).
- No production code references `LeadEntity`, `LeadRepository`, `LeadUpsertService`, `LeadSnapshotResolver`, `LeadAdminQueryService`, `LeadFeedCursorCodec`, or `NormalizedDomain.LEAD`.
- The `peopleUpdated` webhook arrives → `PersonUpsertService` runs regardless of `stage` value; `kind` is set per the mapping.
- Workflow validator rejects `lead.<field>` references (cleanly migrated to `person.<field>`); accepts `person.kind` as a known field.

### Out of scope (deliberately deferred)
- FUB Users (`/v1/users`) ingestion as Persons — separate feature; closes #19 when done.
- Any actual diff / event-emission work — that's Phase 2.
- Any new behaviour beyond dropping the ingest filter.

### Repo decisions impact
Probably `No`. The rename is feature-internal vocabulary; no cross-cutting decision is created.

### Size estimate
~35 files touched (rename surface) + the new `PersonKind` enum + mapping helper + JSONata-scope wiring. Mostly mechanical; ~1.5 days with proper testing including the manual stage=Agent behaviour check.

---

## Phase 2 — Domain events table + diff machinery
Status: `NOT STARTED`

**Goal:** Webhooks produce typed `events` rows. Diff computed for state-change events. In-process dispatcher available; nothing subscribes yet. Lands on the renamed `Person` substrate (see Pre-Phase-2).

### Deliverables

**1. V22 migration — `events` table**
Per the schema in [`plan.md`](./plan.md) §"The `events` table". `source_system` from day one. Indices on `(event_kind, created_at DESC)` and `(entity_type, entity_id, created_at DESC)`. FK to `webhook_events.id` (nullable, `ON DELETE SET NULL`).

**2. `EventEntity` + `EventRepository`**
JPA mapping with `@JdbcTypeCode(SqlTypes.JSON)` for payload per project convention.

**3. `PersonDiffComputer`** — per-field strategy:
- **Scalars** (`name`, `firstName`, `lastName`, `stage`, `stageId`, `type`, `source`, `assignedUserId`, `assignedTo`, `assignedPondId`, `assignedLenderId`, `claimed`, `contacted`) → `JsonNode.equals()`
- **Arrays of strings** (`tags`) → sort both arrays, then `equals`
- **Arrays of objects** (`phones`, `emails`) → `Set<JsonNode>` comparison (order-independent at element level)
- Returns `DiffResult { changedFields, previous, current }` containing **only changed fields** in `previous`/`current`.

**4. `DomainEventEmitter`** service
`emit(eventKind, sourceSystem, sourceEventId, entityType, entityId, payload)` — INSERT row + call dispatcher inline within the caller's transaction.

**5. `DomainEventDispatcher`** — interface + `InMemoryDomainEventDispatcher`
Single method `dispatch(DomainEvent)`. Holds `List<DomainEventListener>`. No listeners registered in Phase 2. Modeled on the existing `WebhookDispatcher` pattern (no Spring `ApplicationEventPublisher` introduced).

**6. Emission in `PersonUpsertService`** (two event_kinds, decision Q1=B):
- Inside the existing `@Transactional`, between save and return:
  - If `existingOptional.isEmpty()` (this is a brand-new row): emit `person.created` with payload `{ current: <full snapshot> }`. Set `previousState = null`.
  - Else: compute diff via `PersonDiffComputer`. If `changedFields` non-empty: emit `person.state_changed` with payload `{ changed_fields, previous, current }` (only changed fields in `previous`/`current`). Set `previousState = oldDetails`.
  - If diff empty (echo / no-op upsert): no event emitted. `previousState` left alone.

**7. Inline `call.created` emission**
In `WebhookEventProcessorService.processCall`, after `processedCallRepository.save(entity)`. TODO comment about future extraction into a `CallUpsertService` (out of scope for Phase 2).

**8. Note webhook handling** (decision Q3=B):
- `FubWebhookParser` learns `notesCreated`, `notesUpdated`, `notesDeleted` → maps to `NormalizedDomain.NOTE` + `NormalizedAction.CREATED/UPDATED/DELETED` (the CHECK constraint loosening lands in Pre-Phase-2's V21 migration).
- `WebhookEventProcessorService.process()` gets a `case NOTE -> processNoteDomainEvent(event)` branch.
- `processNoteDomainEvent` emits `note.created` / `note.updated` / `note.deleted` with the webhook payload as the event payload.
- **No `notes` table** in Phase 2. Note body content is not fetched from `/v1/notes/{id}`. Workflows that need note content can add a fetch path later.

**9. Replay harness extensions:**
- New `ReplayFixture.Expected` fields:
  - `minStateChangeEventsForPerson` (Map<String, Integer>) — `sourcePersonId → min event count`
  - `minCreatedEventsForPerson` (Map<String, Integer>)
  - `minAppendEvents` (Map<String, Integer>) — `event_kind → min count`, e.g. `{"call.created": 1, "note.created": 2}`
- Update existing fixtures to assert collapse:
  - `lead-20235-fub-burst` (will be `person-20235-fub-burst` after the rename pass): 3 `peopleUpdated` webhooks → assert `minStateChangeEventsForPerson: {"20235": 1}` (collapse from 3 to 1).
  - `lead-20231-fub-burst`: 4 `peopleUpdated` → assert `minStateChangeEventsForPerson: {"20231": 1}`.
  - `lead-20123-echo-cascade`: still produces phantom events under Phase 2 (Phase 3 closes); assert the expected pre-fix shape.
- Add one synthesized fixture for note webhooks (no real-DB note webhook in our extracted set yet).

**10. Phase 2 invariants for the harness:**
- For every webhook arrival where local state ends up unchanged → 0 events emitted (collapse).
- For every `peopleCreated` → exactly 1 `person.created` event.
- For every `peopleUpdated` with meaningful diff → exactly 1 `person.state_changed`.
- For every `callsCreated` → 1 `call.created`.
- For every `notesCreated/Updated/Deleted` → 1 corresponding event.
- Engine echoes **still produce phantom `person.state_changed` events** in Phase 2 alone (Phase 3 closes this — the cascade fixtures will assert this is true today and switch the assertion in Phase 3).

### Verification
- All 528+ tests green.
- All replay fixtures pass (5 existing + 1 new note fixture).
- Person 20235 FUB-burst fixture: 3 webhooks → 1 `person.state_changed` event (collapse proven).
- Person 20123 echo-cascade fixture: still produces a phantom `person.state_changed` event from the echo (Phase 3 will flip this assertion).
- Diff-computer unit tests for each per-field strategy: scalar change, scalar no-op, tag reorder (no-op), tag add, phone reorder (no-op), phone add, phone removal.

### Exit criteria
- V22 migration applied.
- `events` table populated for every webhook the engine processes (with the right `event_kind` per the rules above).
- No subscribers consume events yet; the old `workflowTriggerRouter.route(event)` call at the end of `WebhookEventProcessorService.process()` still runs unchanged.
- Replay-harness fixtures show the FUB-burst collapse working.

### Repo decisions impact
TBD at `phase-2-implementation.md` time. Likely `No`.

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
  - Before FUB call: read current local Person state for affected fields; update local state to intended new value; record in tracker
  - Call FUB
  - On FUB failure: revert local state; mark tracker entry failed; propagate error to step machinery (step fails as today)
  - On FUB success: leave local + tracker in place; echo webhook will diff to empty
- **Diff annotation in Phase 2's emitter:** when `person.state_changed` would be emitted, consult the tracker — if a recent record matches the diff's fields and entity, annotate `payload.source = "ENGINE"`; emit the event anyway (filtering is the workflow's job per the opt-in default)

### Verification
- Replay 05-08 echo cascade: engine reassign → local state updated → echo webhook → diff = empty → no event emitted (verified by harness assertions)
- Replay 05-12 reassign-to-same-user case (person 20235): three back-to-back reassigns produce one tracker record sequence; second and third see local already at target and emit no event
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
  - `change.*` sugar over `event.payload` for `person.state_changed` events
  - `current.*` sugar over `event.payload.current` for both `person.created` and `person.state_changed`
  - `webhook.*` exposes the proximate raw webhook payload (for steps that need source-system fields)
  - `person.*` available in trigger filter scope too (closes #17)
  - `WorkflowStepExecutionService.buildRunContext` updated accordingly
- **Re-author `agent_followup_enforcement`** workflow JSON:
  - Trigger becomes `{ "on": "person.state_changed", "filter": "person.stage = 'Lead' AND change.assignedUserId.changed AND change.source != 'ENGINE'" }` (the `person.stage = 'Lead'` predicate was already added during the Pre-Phase-2 rename pass; Phase 4 keeps it and adds the change-based predicates)
  - Any step expressions using `event.payload.*` migrate to `webhook.payload.*` if needed
  - Verify in field-observations replay that bad-run rate drops

### Verification
- Replay the three high-signal incidents end-to-end:
  - **Person 20123 (05-08 echo cascade)** — under new architecture: one event for the real assignment, engine reassign produces no echo event (Phase 3), workflow does not self-trigger. Bad runs = 0.
  - **Person 20235 (05-12 FUB burst)** — one event for the burst (Phase 2 collapse), one run created, one reassign. Bad runs = 0.
  - **Person 20207 (05-11 triple-run)** — first event creates run; echo run suppressed by no-event-emission (Phase 3); unknown peopleUpdated either filtered semantically or absorbed by Phase 5's run dedup.
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

**Goal:** Hard-suppress duplicate runs of the same workflow on the same person while one is active. Catches residual cases not handled by Phase 2's event-collapse (e.g., genuine distinct state transitions in quick succession).

### Deliverables
- **Partial unique index** on `workflow_runs`:
  ```sql
  CREATE UNIQUE INDEX uk_workflow_runs_active_per_person
    ON workflow_runs (workflow_key, source_person_id)
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
- Synthesize two near-simultaneous `person.state_changed` events for the same person in the harness; assert one run runs, one row is `SUPPRESSED` with a back-reference
- Cross-workflow case: two different workflows triggering on the same person simultaneously both succeed (the partial unique is per `workflow_key`)
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
| 1 → Pre-Phase-2 | Phase 1 had to land before the rename to avoid co-mingling foundation work with a refactor. Now that the foundation is in, the rename happens with a stable target. |
| Pre-Phase-2 → 2 | Phase 2 hardens vocabulary (`events.event_kind = 'person.state_changed'`, validator `change.*`). Renaming after Phase 2 would require a data migration of the `events` table. |
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
