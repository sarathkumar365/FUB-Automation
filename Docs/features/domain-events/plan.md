# Domain Events

> **Changelog (2026-05-28):** Architecture review before starting Phase 2. Added two correctness pieces to §"The `events` table" — per-person upsert serialization (the diff-collapse invariant is not safe under the existing 2–4 thread async webhook pool without a pessimistic row lock) and after-commit dispatch (emission stays decoupled from consumption). The complementary durable-outbox poller is explicitly deferred to a Phase 4 decision and added to the Out-of-scope table. The Pre-Phase-2 rename's missing admin read-feed surface is tracked in [`phases.md`](./phases.md).

A platform-level reframe: webhooks become **state-sync signals**; workflows subscribe to **domain events** the engine emits when state actually transitions or when something happens (a call, a note). The engine — not the webhook stream — is the source of truth for "what changed."

This replaces the earlier draft at [`Docs/features/state-change-events/design.md`](../state-change-events/design.md), which framed the same problem as a layered set of patches over the existing webhook-as-trigger model. After deliberation we concluded the deeper move is a model change, not a patch stack.

> **Naming note (2026-05-XX):** the canonical CRM-contact entity is `Person` (table `persons`), not `Lead`. `Lead` is just one possible value of `person.stage`. Phase 1 was implemented under the older `Lead` naming; the Pre-Phase-2 rename pass (see [`phases.md`](./phases.md)) sweeps the code and docs over. This document is written in the post-rename vocabulary. Where the historical record matters (Phase 1's implementation log, field-observations, known-issues) the original `Lead` wording is preserved.

## Why this feature exists

The driving evidence is in [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md). Across three operational days the `agent_followup_enforcement` workflow ran at a **46–64% bad-run rate** because:

- FUB's `peopleUpdated` is a catch-all for any person mutation. The trigger has no view of what changed, so it fires on every edit. (Known issue #20.)
- Engine writes to FUB produce echo webhooks within 300–800 ms. Every meaningful reassign produced an echo on 2026-05-12 (7/7). The workflow self-triggers. (Known issue #23.)
- FUB occasionally fires 3–4 `peopleUpdated` webhooks within 8–16 seconds for one logical edit. Multiple parallel runs result. (New observation on 2026-05-12.)
- Two webhooks for the same person within minutes produce two independent runs that proceed in parallel, unaware of each other. (Known issue #24.)
- `workflow_runs.webhook_event_id` is never populated — every investigation requires fuzzy correlation. (Known issue #25.)

The current workflow stays "safe" only because of engineering safety nets — particularly the 5-min lookback buffer absorbing agent-induced over-fires by accident. It is **not production-credible** today and adding a second workflow inherits the same bugs.

## Conceptual framing

Two ways to read a webhook:

| Framing | Meaning |
|---|---|
| **Event-sourced** (today) | A webhook is a discrete event the engine reacts to. Three webhooks = three reactions. |
| **State-observation** (this plan) | A webhook is a signal that some entity's state *may* have changed. The engine reads the new state, compares against what it knows, and emits a **domain event** only if something meaningful actually changed (or, for append entities, every time an instance is created). |

Under state-observation:

- Three `peopleUpdated` webhooks for one logical edit collapse to **one** event (only the first sees a diff; the others see no change).
- An engine write that updates local state at write time produces **no event** when the echo arrives (no diff).
- Workflows subscribe to *what changed*, not to *what arrived*.

State-observation does not cover every case — appendable events like calls and notes have no "previous state" to diff against. Those flow through the same domain-event pipeline as **append events**: every webhook arrival produces one event, no diff machinery involved.

The unified abstraction is `events` (or `domain_events`) — a single table with `event_kind` discriminating the flavour.

## The five invariants this plan establishes

| # | Invariant | Mechanism |
|---|---|---|
| **I1** | Local `persons` state authoritatively mirrors FUB for fields any workflow references | Webhook-driven upsert (existing — without the historical `isFubLeadPerson` filter; **all persons** are captured, workflows filter lead-only behaviour by `person.kind = "LEAD"`); workflow-creation-time validation that every field referenced in trigger filter / step expressions is captured |
| **I2** | A domain event is emitted iff something meaningful happened (state diff, or append) | Diff at upsert for state entities (`person.created` on first insert, `person.state_changed` on subsequent diff); pass-through for append entities (`call.created`, `note.created`, `note.updated`, `note.deleted`) |
| **I3** | Engine-originated writes do not produce phantom events | Local-state-first writes (update local before FUB call); `EngineWriteTracker` cache as race-window guard |
| **I4** | At most one active run per `(workflow_key, source_person_id)` | Partial unique index, hard suppression; supersede semantics deferred |
| **I5** | Every run knows the proximate webhook AND the logical domain event that caused it | Populate both `workflow_runs.webhook_event_id` and a new `workflow_runs.domain_event_id` |

## End-to-end lifecycle

```mermaid
flowchart TD
    A[FUB webhook arrives] --> B[WebhookIngressService\npersists to webhook_events]
    B --> C[WebhookEventProcessorService\nclaims and dispatches by event type]

    C -->|peopleCreated/Updated| D[PersonUpsertService\nfetch FUB, upsert persons,\ncapture previous_state]
    C -->|callsCreated/Updated| E[CallUpsertService.persistCallFacts\n@Transactional save + emit\n(extracted in Phase 2b)]
    C -->|notesCreated/Updated/Deleted| EN[processNoteDomainEvent\n@Transactional; no persistence;\nemit from payload]

    D --> G{First upsert or\ndiff vs previous_state?}
    G -->|first insert| GC[DomainEventEmitter\nINSERT events row\nevent_kind=person.created]
    G -->|no diff\necho or unchanged edit| H[STOP\nno event emitted]
    G -->|diff non-empty| I[DomainEventEmitter\nINSERT events row\nevent_kind=person.state_changed]

    E --> J[DomainEventEmitter\nINSERT events row\nevent_kind=call.created\nappend, no diff]
    EN --> JN[DomainEventEmitter\nINSERT events row\nevent_kind=note.created/updated/deleted\nappend, no diff]

    GC --> K[DomainEventDispatcher\nin-process fan-out]
    I --> K
    J --> K
    JN --> K

    K --> L[WorkflowTriggerRouter\nmatch event_kind + filter expression]
    L -->|match| M[WorkflowExecutionManager.plan]
    L -->|no match| N[STOP]

    M --> O{Active run for\nworkflow_key + source_person_id?}
    O -->|yes| P[Persist SUPPRESSED row\npointing to active run]
    O -->|no| Q[Create workflow_run with\nwebhook_event_id + domain_event_id]

    Q --> R[WorkflowExecutionDueWorker\nclaims and executes steps]
    R --> S[Step uses expression scope:\nevent.* / change.* / person.* / webhook.*]

    S -->|step calls fub_reassign etc.| T[EngineWriteTracker.record\nlocal_state.update\nthen FUB API call]
    T -->|FUB ok| U[Run continues / completes]
    T -->|FUB fails| V[Revert local_state\nmark tracker entry failed]

    %% Echo loop
    T -.echo webhook.-> A
    %% The echo arrives, runs through D, G — no diff because local already updated → loop terminates at H
```

The diagram is load-bearing. Anyone reading this doc later should be able to trace any workflow-run incident through it without re-reading the implementation.

## Architectural pieces

### 1. `persons.previous_state` column

A JSONB column on `persons` storing the entity's state from before the most recent upsert. Used at upsert time to compute the diff. Retention: keep last-known-previous only (one column, not a history table) — adequate for trigger evaluation; a future change-log table can be added if audit needs grow.

(Phase 1 added this column under the older `leads.previous_state` name; the Pre-Phase-2 rename pass moves it to `persons.previous_state`.)

### 2. The `events` table (domain events)

```sql
CREATE TABLE events (
    id                  BIGSERIAL PRIMARY KEY,
    event_kind          VARCHAR(64) NOT NULL,
        -- "person.created", "person.state_changed",
        -- "call.created",
        -- "note.created", "note.updated", "note.deleted",
        -- (future) "task.completed", ...
    source_system       VARCHAR(32) NOT NULL DEFAULT 'FUB',
    source_event_id     BIGINT,
        -- FK to webhook_events.id; nullable for engine-synthesized events
    entity_type         VARCHAR(32),
        -- "person" | "call" | "note" | ...
    entity_id           VARCHAR(255),
        -- source-system entity id (e.g. person 20235)
    payload             JSONB NOT NULL,
        -- event-specific shape; see below
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_events_webhook
        FOREIGN KEY (source_event_id) REFERENCES webhook_events(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_events_kind_created_at ON events (event_kind, created_at DESC);
CREATE INDEX idx_events_entity ON events (entity_type, entity_id, created_at DESC);
```

`payload` shape per `event_kind`:

| `event_kind` | Flavour | `payload` shape |
|---|---|---|
| `person.created` | State-change (first insert) | `{ current: { <full Person snapshot> } }` — no `previous`, no `changed_fields` (everything is new) |
| `person.state_changed` | State-change (subsequent diff) | `{ changed_fields: ["assignedUserId","stage"], previous: { assignedUserId: 10, stage: "Cold" }, current: { assignedUserId: 11, stage: "Hot" } }` — **only changed fields** in `previous`/`current` |
| `call.created` | Append | The full FUB call payload |
| `note.created`, `note.updated`, `note.deleted` | Append | The webhook payload (note id + person id; full note content is NOT fetched — workflows that need body content fetch on demand) |

**Why two event_kinds for state entities** (`person.created` and `person.state_changed`): they answer different workflow questions cleanly. "Fire when a new lead arrives" subscribes to `person.created` + filter `current.stage = 'Lead'`. "Fire on assignment changes" subscribes to `person.state_changed` + filter `change.assignedUserId.changed`. Conflating them into a single event with `previous: null` forces every workflow to discriminate, which is the wrong default.

**Diff strategy** (per-field, see `PersonDiffComputer`):
- **Scalars** (`name`, `firstName`, `lastName`, `stage`, `stageId`, `type`, `source`, `assignedUserId`, `assignedTo`, `assignedPondId`, `assignedLenderId`, `claimed`, `contacted`) → `JsonNode.equals()`
- **Arrays of strings** (`tags`) → sort both arrays then compare (order-independent)
- **Arrays of objects** (`phones`, `emails`) → `Set<JsonNode>` comparison (order-independent at the element level; deep-equal within each element)

The set/sort treatment for arrays is deliberate: FUB clients sometimes reorder tags or contact methods without semantic intent. Naive `JsonNode.equals()` on the whole array would emit phantom events on re-ordering. Per-field strategy avoids that without losing legitimate changes.

`source_system` is on the row from day one even though only `FUB` feeds it today — future CRM adapters slot in cleanly (closes the spirit of known issue #18).

**Per-person serialization of the upsert** (load-bearing for collapse). Webhooks process on a 2–4 thread async pool ([`WebhookAsyncConfig.java`](../../../src/main/java/com/fuba/automation_engine/config/WebhookAsyncConfig.java)), so a FUB burst of N webhooks for the same person can run truly in parallel. If `PersonUpsertService.upsertFubPerson` reads with a plain finder, every parallel worker reads the same pre-burst state, every worker sees a non-empty diff, every worker emits — and the collapse invariant silently fails. The fix is a pessimistic row lock on the `persons` row (`@Lock(PESSIMISTIC_WRITE)` on a `findBySourceSystemAndSourcePersonIdForUpdate` finder). Different persons still process fully in parallel; only same-person upserts serialize. Phase 2 deliverable 5a builds this; it is a correctness prerequisite for the headline "3 webhooks → 1 event" claim, not an optimization.

**Emit in-transaction, dispatch after commit.** `DomainEventEmitter` inserts the events row inside the caller's write transaction (atomic with the state change and `previous_state` update that produced it), and registers an after-commit hook (`TransactionSynchronizationManager.registerSynchronization(...)`) that invokes `DomainEventDispatcher.dispatch(...)` only once the transaction commits. Dispatching inline inside the write transaction would extend the `persons` row-lock hold across listener work and, once Phase 4 wires `WorkflowTriggerRouter` as a listener, drag workflow planning and run-row INSERTs into the upsert transaction — the wrong default. After-commit dispatch keeps emission and consumption decoupled while preserving durability: the event row is committed before any listener runs, so a listener failure cannot lose the event (it has already been recorded). The complementary durable-poller piece (crash recovery for the commit-vs-dispatch window) is deferred to Phase 4 — see the Out-of-scope table.

### 3. Trigger schema (new)

Replaces the existing `peopleUpdated`-typed trigger. Hard cut — one workflow exists today (`agent_followup_enforcement`); it gets re-authored as part of Phase 4.

```json
{
  "trigger": {
    "on": "person.state_changed",
    "filter": "person.stage = 'Lead' AND change.assignedUserId.changed AND change.source != 'ENGINE'"
  }
}
```

The `person.stage = 'Lead'` predicate is mandatory under the new architecture because the Pre-Phase-2 rename pass drops the `isFubLeadPerson` ingestion filter — workflows now own the stage filtering instead of the ingestion layer.

For brand-new lead arrivals:

```json
{
  "trigger": {
    "on": "person.created",
    "filter": "current.stage = 'Lead'"
  }
}
```

Or for append:

```json
{
  "trigger": {
    "on": "call.created",
    "filter": "event.payload.durationSec > 60"
  }
}
```

### 4. Expression scope (new shape)

| Scope key | Source | Notes |
|---|---|---|
| `event.id`, `event.kind`, `event.entityType`, `event.entityId` | Domain event metadata | Stable across all event_kinds |
| `event.payload.*` | Domain event payload — shape varies by kind | `person.state_changed`: `{changed_fields, previous, current}`. `person.created`: `{ current }`. Append: the entity record. |
| `change.<field>.changed` / `.old` / `.new` | Sugar over `event.payload` for `person.state_changed` events | Cleaner than indexing into `changed_fields` arrays |
| `current.<field>` | Sugar over `event.payload.current` (available for both `person.created` and `person.state_changed`) | Use in filters like `current.stage = 'Lead'` |
| `change.source` | Annotated by `EngineWriteTracker` if the diff matches a recent engine write | `"ENGINE"` or absent |
| `person.*` | Current Person snapshot (resolved at step time, as today) | Available in trigger filter scope too — closes #17 |
| `webhook.*` | Raw underlying webhook payload | Available for steps that need source-format fields |
| `runMetadata.*` | Run timing (unchanged from today) | |
| `steps.<stepId>.*` | Prior step outputs (Wave 2, unchanged) | |

This is a workflow-author-facing breaking change. Steps that today use `event.payload.resourceIds[0]` must migrate to `webhook.payload.resourceIds[0]`. There is one production workflow that needs the migration.

### 5. Local-state-first engine writes

Steps that write to FUB (`fub_reassign`, `fub_move_to_pond`, `fub_create_note`, ...) follow:

```text
begin transaction
  capture old local state for affected fields
  update local state to intended new value
  insert engine_write_tracker entry (kind, entity, fields, ttl ~30s)
commit
call FUB
on FUB failure:
  begin transaction
    revert local state to old value
    mark tracker entry FAILED
  commit
  return error to workflow runtime (step fails as today)
on FUB success:
  return success
```

When the echo webhook arrives ~500ms later, `PersonUpsertService` fetches FUB, computes the diff against the already-updated local state, finds no diff → no event. The tracker is a secondary guard for the narrow race where the echo arrives before the local commit lands.

**Failure semantic chosen — and why:** if FUB write fails, the engine compensates by reverting local. Worst case: revert fails too; the next legitimate webhook re-syncs anyway. This is preferred over the alternative (write FUB first, then local) because (a) its failure mode is **operation did not execute** — loud, loggable, no FUB-visible side effect; (b) the alternative's failure mode is a **phantom event** — silent, indistinguishable from real events, can cascade.

**Revert means *restore the captured prior snapshot* of the affected fields — not "undo the delta we applied."** For scalar fields (`assignedUserId`, `assignedPondId`) the two are equivalent. For accumulating fields (`tags`, `phones`, `emails`) they diverge: if another webhook landed an unrelated change between our local write and our revert, "undo the delta" would either destroy that change or preserve a destination the user no longer wants. Restoring the prior snapshot is wrong in the opposite direction (it could destroy a legitimate concurrent change), but the next webhook re-syncs the truth — so the worst-case window is one webhook cycle, and the semantics are simple and the same across all field types.

Accepted residual race: an external write (human, another integration) landing in FUB between the local commit and our PUT can produce a brief inconsistency that self-heals on the next webhook. Window ~100–500 ms. Acceptable for dev phase.

### 6. `EngineWriteTracker` (interface-first)

```text
interface EngineWriteTracker {
  void record(entityType, entityId, changedFields, runId);
  Optional<EngineWriteRecord> findMatching(entityType, entityId, changedFields, withinMs);
  void markFailed(recordId);
}
```

Shipped initial impl: `InMemoryEngineWriteTracker` (ConcurrentHashMap + scheduled eviction). Future impl: `RedisEngineWriteTracker`, swapped in when Redis enters the stack (no Redis dependency in the project today). The interface boundary is the commitment; the impl is replaceable.

### 7. Run-level uniqueness

A **partial unique index** in addition to the existing `uk_workflow_runs_idempotency_key`:

```sql
CREATE UNIQUE INDEX uk_workflow_runs_active_per_person
  ON workflow_runs (workflow_key, source_system, source_person_id)
  WHERE status IN ('PENDING','RUNNING','BLOCKED');
```

`source_system` is included because the `events` table already carries it from day 1 to allow future CRM adapters (`source_system = 'FUB'` today; e.g. `'SALESFORCE'` later). Without it, person id `100` from FUB and person id `100` from a future second CRM would collide on the same workflow.

The existing constraint catches "same webhook event replayed forever." The new partial index catches "same workflow, same person from the same source, while a run is still active." Both stay — they protect different things and the cost is one extra B-tree index.

On conflict, the planner persists a `SUPPRESSED` row pointing at the active run via `workflow_runs.suppressed_by_run_id` (new column) for audit. Status is hard-suppress only; **supersede semantics are deferred** (see Out of Scope).

## Engine-echo exclusion default

`change.source` is annotated when the diff matches a recent engine-write tracker record. The `change.source != 'ENGINE'` predicate is **opt-in per workflow**: workflow authors must include it (or set a workflow-level `excludeEngineEchoes` toggle once the config page exists). Default is opt-in.

This is a deliberate platform choice for flexibility. It carries a real risk: a workflow author who forgets the predicate reintroduces #23 silently. Mitigation at the platform level:

- Document the predicate as the standard pattern for any workflow that does FUB writes
- Surface the toggle prominently in the future workflow-creation UI
- Revisit the default if/when a second workflow shows the opt-in pattern is error-prone

## Workflow-creation-time validation

The workflow validator (called at `POST /admin/workflows` and `PUT`) gains field-reference validation:

- Every `change.<field>` reference must resolve against a field captured in `persons.previous_state` / `persons.person_details` (added in Phase 4 when `change.*` enters the scope vocabulary)
- Every `person.<field>` reference must resolve against a field captured in the Person snapshot (live since Phase 1; renamed from `lead.*` in the Pre-Phase-2 pass)
- Every `event.*` reference must be valid for the declared `event_kind`

This is the defence against the silent-failure mode where a workflow references a field that isn't captured → diff never sees a transition → trigger never fires → workflow looks healthy but doesn't run.

## Out of scope (tracked as follow-ups)

These are deliberately deferred, not forgotten. Each will land as its own change when the priority surfaces.

| Deferred item | Why deferred |
|---|---|
| **Supersede semantics for same-workflow multi-transition** (Option B from deliberation) — when a second `person.state_changed` event arrives for a person with an active run, terminate the active run and start a fresh one | Hard-suppress is the placeholder; supersede needs run-termination machinery that's a separate effort. No observed incident requires it today. |
| **Action-step freshness gate** (re-read assignedUserId immediately before reassigning) | Only justified by the supersede case, which is deferred. We trust local state for the single-transition case. |
| **5-min lookback buffer narrowing** | Once Phase 4 lands and over-fires stop reaching `wait_and_check_communication`, the buffer's "absorb agent over-fire" job disappears. Should be narrowed back to its true purpose (calls-before-claim race). Trivial to do; deferred to a follow-up that touches the step's config. |
| **FUB-to-local reconciliation / catch-up** | If FUB stops sending webhooks the engine has no recovery path. Whole system already relies on FUB to keep sending; not regressing. Out of scope; address if/when observed. |
| **Stale-assignment guard** (person 19255 case — prior real conversation outside buffer window) | Product concern, not engine bug. Workflow author should add a `person.lastCallAt` predicate; engine should expose the data. |
| **30-min reassign threshold tuning** | Product, not platform. |
| **`change.source` exclusion default reconsidered** | If a second workflow exposes the opt-in pattern as error-prone, revisit. |
| **In-flight run drain protocol** at Phase 4 deploy | App is in dev phase; not worth building a drain protocol yet. |
| **`previous_state` retention policy** | Currently last-known-only; if storage growth or audit needs change, formalise. |
| **Person merges** in FUB orphaning events | One-line note; out of scope. |
| **FUB Users (`/v1/users`) ingestion** as Persons | Conceptually fits a unified Person abstraction (closes #19) but is a separate feature. Today our `Person` is FUB `/v1/people` only. |
| **`notes` table persistence** + on-demand FUB note fetch (`/v1/notes/{id}`) | Phase 2 emits `note.created/updated/deleted` events from the webhook payload only. If a workflow needs note body content, add a `NoteRepository` + fetch path then. |
| **Replay harness extension to all event kinds** | Phase 0 ships harness for person events; extend incrementally as new event kinds are added. |
| **Redis-backed `EngineWriteTracker`** | Interface ready; swap when Redis lands. |
| **Durable outbox poller for `events`** (a `dispatched` flag + scheduled job that picks up un-dispatched rows after a crash) | Phase 2 ships the decoupling seam (in-tx insert + after-commit dispatch) but not the durability piece. No event consumers exist until Phase 4, and the app is not deployed anywhere, so the crash-between-commit-and-dispatch window carries no operational risk today. Cleanly additive — no rewrites of emission code needed to add it later. **Revisit at Phase 4** once `WorkflowTriggerRouter` actually consumes events; decide then whether the in-memory dispatcher's loss-on-crash semantics are tolerable. |
| **Time-sensitive step expiry (known issue #11)** | Backlog-driven; not observed under current volume. |

## Risks and mid-flight detection

| Risk | Detection signal during build |
|---|---|
| Field-coverage gap silently breaks a workflow | Validation at workflow creation should refuse to save; replay harness (Phase 0) catches missed events in test data |
| Engine local-state-first compensation logic has a bug → local drifts from FUB | Periodic diff between `persons.person_details` and a live FUB fetch on a small sample; surface drift as a metric |
| `EngineWriteTracker` TTL too short → echo arrives after eviction → phantom event | Tracker metrics: tracker hits vs misses on echo windows; tune TTL if miss rate rises |
| Partial unique index race on planner | Smoke test under simulated FUB-burst load (Phase 0 harness) |
| New trigger schema breaks existing workflow | Hard-cut migration of `agent_followup_enforcement` and explicit validation that no other workflow uses old shape |
| Expression-scope breaking change misses a call site | Validator runs at workflow save; runtime resolution errors logged loudly (per #10 — JSONata error surfacing remains a separate concern) |

## Validation criteria

Per phase (see [phases.md](phases.md)) but at the feature level:

- Replay harness covers the 05-08, 05-11, 05-12 incidents recorded in field observations. Each replays cleanly and produces the *expected* sequence under the new architecture (engine echoes collapse, FUB bursts collapse, agent over-fires never produce events).
- Person 20235 scenario (3 webhooks in 8s, all hitting peopleUpdated): under new architecture, exactly one `person.state_changed` event is emitted; one run is created; one reassign is performed.
- Person 20123 scenario (echo cascade after reassign): under new architecture, the engine's reassign updates local state first, the echo webhook diff is empty, no second event emitted.
- The `agent_followup_enforcement` workflow re-authored against the new shape runs the same 26+ days of recorded events with bad-run rate dropping from ~50% to <5% (the residual being genuine multi-transition cases the deferred supersede semantics would close).

## Linked decisions

- This plan does not require a new entry in `Docs/repo-decisions/`. The state-observation framing is a feature-level architectural choice limited to the workflow-trigger pipeline; it does not cross-cut other subsystems. If the model proves out and we want to canonicalise "state-observation as engine policy," promote to `repo-decisions/` then.
- The `excludeEngineEchoes` opt-in default is a platform behaviour choice worth promoting to `repo-decisions/` once the config page exists, so future workflow authors have a referenceable rationale.

## Cross-references

- Driving evidence: [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md)
- Bug records: [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) #17, #18, #20, #23, #24, #25
- Earlier draft (superseded by this doc): [`Docs/features/state-change-events/design.md`](../state-change-events/design.md)
- Product backlog: [`Docs/product-discovery/ideas.md`](../../product-discovery/ideas.md)
- Existing engine entry points referenced in the lifecycle diagram:
  - [WebhookIngressService.java](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java)
  - [WebhookEventProcessorService.java](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java)
  - [PersonUpsertService.java](../../../src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java)
  - [FubWebhookTriggerType.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java)
  - [WorkflowExecutionManager.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionManager.java)
  - [WorkflowExecutionDueWorker.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowExecutionDueWorker.java)
