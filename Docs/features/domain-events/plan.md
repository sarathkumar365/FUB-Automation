# Domain Events

> **Changelog (2026-05-28):** Architecture review before starting Phase 2. Added two correctness pieces to §"The `events` table" — per-person upsert serialization (the diff-collapse invariant is not safe under the existing 2–4 thread async webhook pool without a pessimistic row lock) and after-commit dispatch (emission stays decoupled from consumption). The complementary durable-outbox poller is explicitly deferred to a Phase 4 decision and added to the Out-of-scope table. The Pre-Phase-2 rename's missing admin read-feed surface is tracked in [`README.md`](./README.md).

A platform-level reframe: webhooks become **state-sync signals**; workflows subscribe to **domain events** the engine emits when state actually transitions or when something happens (a call, a note). The engine — not the webhook stream — is the source of truth for "what changed."

This replaces the earlier draft at [`Docs/features/state-change-events/README.md`](../state-change-events/README.md), which framed the same problem as a layered set of patches over the existing webhook-as-trigger model. After deliberation we concluded the deeper move is a model change, not a patch stack.

> **Naming note (2026-05-XX):** the canonical CRM-contact entity is `Person` (table `persons`), not `Lead`. `Lead` is just one possible value of `person.stage`. Phase 1 was implemented under the older `Lead` naming; the Pre-Phase-2 rename pass (see [`README.md`](./README.md)) sweeps the code and docs over. This document is written in the post-rename vocabulary. Where the historical record matters (Phase 1's implementation log, field-observations, known-issues) the original `Lead` wording is preserved.

## Why this feature exists

The driving evidence is in [`Docs/features/agent-followup-enforcement/plan.md`](../agent-followup-enforcement/plan.md). Across three operational days the `agent_followup_enforcement` workflow ran at a **46–64% bad-run rate** because:

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
| **I4** | At most one active run per `(workflow_key, source_person_id)` | Field-aware supersede — a newer event whose `changed_fields` overlap the in-flight run's cancels it (`SUPERSEDED_BY_NEWER_EVENT`) and the newer run proceeds (Phase 5, **shipped**). Residual simultaneity race in known-issue #29. |
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

**Per-person serialization of the upsert** (load-bearing for collapse). Webhooks process on a 2–4 thread async pool ([`WebhookAsyncConfig.java`](../../../src/main/java/com/flux/config/WebhookAsyncConfig.java)), so a FUB burst of N webhooks for the same person can run truly in parallel. If `PersonUpsertService.upsertFubPerson` reads with a plain finder, every parallel worker reads the same pre-burst state, every worker sees a non-empty diff, every worker emits — and the collapse invariant silently fails. The fix is a pessimistic row lock on the `persons` row (`@Lock(PESSIMISTIC_WRITE)` on a `findBySourceSystemAndSourcePersonIdForUpdate` finder). Different persons still process fully in parallel; only same-person upserts serialize. Phase 2 deliverable 5a builds this; it is a correctness prerequisite for the headline "3 webhooks → 1 event" claim, not an optimization.

**Emit in-transaction, dispatch after commit.** `DomainEventEmitter` inserts the events row inside the caller's write transaction (atomic with the state change and `previous_state` update that produced it), and registers an after-commit hook (`TransactionSynchronizationManager.registerSynchronization(...)`) that invokes `DomainEventDispatcher.dispatch(...)` only once the transaction commits. Dispatching inline inside the write transaction would extend the `persons` row-lock hold across listener work and, once Phase 4 wires `WorkflowTriggerRouter` as a listener, drag workflow planning and run-row INSERTs into the upsert transaction — the wrong default. After-commit dispatch keeps emission and consumption decoupled while preserving durability: the event row is committed before any listener runs, so a listener failure cannot lose the event (it has already been recorded). The complementary durable-poller piece (crash recovery for the commit-vs-dispatch window) is deferred to Phase 4 — see the Out-of-scope table.

### 3. Trigger schema (new)

Replaces the existing `peopleUpdated`-typed trigger. Hard cut — one workflow exists today (`agent_followup_enforcement`); it gets re-authored as part of Phase 4.

```json
{
  "trigger": {
    "on": "person.state_changed",
    "filter": "person.kind = 'LEAD' AND change.assignedUserId.changed"
  }
}
```

The `person.kind = 'LEAD'` predicate is mandatory under the new architecture because the Pre-Phase-2 rename pass drops the `isFubLeadPerson` ingestion filter — workflows now own the lead filtering instead of the ingestion layer. It uses our normalized `kind` enum (set by `PersonUpsertService.mapStageToKind`) rather than FUB's raw `stage` string, matching the predicate already added to `agent_followup_enforcement` during the rename pass. (Updated 2026-06-02 — an earlier draft showed `person.stage = 'Lead'`, which predates the `kind` enum.)

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
| `event.origin` | Engine-write provenance, from the payload `source` annotation (`EngineWriteTracker`) | `"ENGINE"` (engine-caused) or `"EXTERNAL"` — **always present**. (Lives under `event`, not `change`, because `source` is also a diffable person field — so `change.source` is the lead-source field delta.) |
| `person.*` | Current Person snapshot (resolved at step time, as today) | Available in trigger filter scope too — closes #17 |
| `webhook.*` | Raw underlying webhook payload | Available for steps that need source-format fields |
| `runMetadata.*` | Run timing (unchanged from today) | |
| `steps.<stepId>.*` | Prior step outputs (Wave 2, unchanged) | |

This is a workflow-author-facing breaking change. Steps that today use `event.payload.resourceIds[0]` must migrate to `webhook.payload.resourceIds[0]`. There is one production workflow that needs the migration.

### 5. Local-state-first engine writes

> **Revision 2026-05-29 (race-matrix audit):** The earlier "capture old → write local → call FUB → revert on failure" pattern below is **superseded** for the three reasons captured in [`plan.md`](./plan.md) and locked in [`plan.md`](./plan.md):
>
> 1. **Revert is dropped.** `RetryPolicy.DEFAULT_FUB` handles transient FUB failures. Permanent failures leave local ahead of FUB; the next webhook for that person re-syncs. No `revertLocalUpdate` code path is built. Accepted cost: the post-failure webhook produces a `person.state_changed` event that *looks like* an external reversal (known issue #26).
> 2. **Tags do NOT use local-state-first.** The C2 phantom-removal class (concurrent external tag-add landing before our FUB PUT → diff fabricates a "removal" event) is structurally unfixable with optimistic local writes for accumulating fields. `fub_add_tag` calls FUB first, then records on the tracker; the echo flows through `PersonUpsertService` (truth-from-FUB) annotated `source=ENGINE`.
> 3. **Notes do NOT have local state to write first.** `fub_create_note` is tracker-only on two channels (`note.created` echo + person-side `peopleUpdated` echo for `lastNoteAt`).
>
> The text below is preserved as historical record of the original design intent. The actual Phase 3 pattern is in [`plan.md`](./plan.md) §"Defaults".

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

### 7. Run-collision handling

> **Revised 2026-06-03 (final, as shipped).** Two earlier designs are superseded: first the partial-unique-index + hard-`SUPPRESSED` row, then a brief **cancel-only** placeholder. The shipped Phase 5 design is **field-aware supersede-at-plan-time** — because under Rail 2 the newer event already spawns its own run, "supersede" (cancel the stale run, the newer one enforces) costs the same as cancel-only but enforces the **newest** state instead of dropping it. The original index/suppress text is preserved below the line for history; see [`implementation-log.md`](./implementation-log.md) and known-issue #29.

When the planner is about to create a run for a `(workflow_key, source_person_id)` that already has an **active** (`PENDING`) run, it cancels the stale run **only if** the two events' `changed_fields` overlap (the newer event re-touched what the in-flight run was premised on — so a phone change never cancels an assignment run): status `CANCELED`, `reason_code = SUPERSEDED_BY_NEWER_EVENT`, `domain_event_id` = the triggering event. The newer run **then proceeds normally** — enforcing the newest state. Owned by `RunSupersedePolicy`, called from `WorkflowExecutionManager.plan` after the idempotency check.

This reuses the existing `WorkflowRunControlService` cancel internals (factored into `finalizeCanceled`) — step-skipping plus the due-worker's "claim only due PENDING steps" make a cancelled run stop cleanly, so there is no new cancel machinery. Active runs are always `PENDING` (the `BLOCKED` enum is unused), so no guard relaxation was needed. No partial unique index is built.

**Why no freshness gate:** the stale run is cancelled *before* its waited actions fire, so it never acts on stale state — the action-step freshness re-check the "ideal" once needed is unnecessary. **Residual:** truly-simultaneous same-person events can both miss each other's not-yet-committed run (no lock) → two runs proceed. Rare; tracked in known-issue #29. Frequency is self-reported: `COUNT(*) … WHERE reason_code = 'SUPERSEDED_BY_NEWER_EVENT'`.

The existing `uk_workflow_runs_idempotency_key` still catches "same webhook event replayed forever" — unchanged.

<details><summary>Original hard-suppress design (superseded 2026-06-03)</summary>

A **partial unique index** in addition to the existing `uk_workflow_runs_idempotency_key`:

```sql
CREATE UNIQUE INDEX uk_workflow_runs_active_per_person
  ON workflow_runs (workflow_key, source_system, source_person_id)
  WHERE status IN ('PENDING','RUNNING','BLOCKED');
```

`source_system` is included because the `events` table already carries it from day 1 to allow future CRM adapters. On conflict, the planner persists a `SUPPRESSED` row pointing at the active run via `workflow_runs.suppressed_by_run_id` (new column) for audit. Status is hard-suppress only; supersede semantics deferred.

</details>

## Engine-echo exclusion — safe-by-default, two-level gated (revised 2026-06-03)

> Supersedes the earlier **opt-in** design (authors had to add `event.origin != 'ENGINE'` themselves; we'd warn if they forgot). That carried a real risk — a forgetful author silently reintroduces #23. The model below makes the safe behaviour the **default the platform enforces**, so it can't be forgotten.

Engine-caused events (`event.origin = 'ENGINE'`, from the Phase 3 annotation) are **excluded from every workflow by default.** A workflow reacts to them only when **both** gates are open:

1. **Global capability ON** — a platform-level switch (entitlement/"license" style: *is this system allowed to let workflows consume engine events at all?*). **Default OFF.** Today a config flag (single-tenant, dev); shaped to become a per-tenant entitlement later.
2. **Per-workflow opt-in** — `reactToEngineEvents: true` in the workflow's trigger config. **Default OFF.**

> `reacts-to-engine-event = globalCapability AND workflow.reactToEngineEvents`. External events are unaffected — they always evaluate normally; this gate only suppresses engine-caused events.

**Enforced by the platform at trigger evaluation** (Phase 4d) — it reads `event.origin` and skips engine-caused events for workflows that haven't opted in (with the capability on). Authors do **not** write an echo-exclusion predicate; the standard filter is just the change/state predicate (e.g. `person.kind = 'LEAD' AND change.assignedUserId.changed`). `event.origin` stays available in scope so an opt-in workflow can still discriminate (e.g. react *only* to engine events with `event.origin = 'ENGINE'`).

**Distinct from `engine.write.emit-events`:** that flag controls whether the engine *emits* its own events (so they're recorded/visible); this gate controls whether workflows *consume* them. The intended posture is emit **ON** (audit trail) + consumption-gate **OFF** (nobody reacts) — not redundant.

**Debuggability:** when the platform skips an engine-caused event for a non-opted-in workflow, log it (`skipped engine-caused event workflowKey=… reactToEngineEvents=false`) so "why didn't my workflow fire?" is traceable rather than silent.

This is a platform-behaviour decision, recorded as [`RD-006`](../../repo-decisions/RD-006-engine-echo-exclusion-safe-by-default.md) (engine-echo exclusion — safe-by-default, two-level gated).

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
| ~~Supersede semantics for same-workflow multi-transition~~ — **SHIPPED in Phase 5** | Field-aware supersede-at-plan-time: a newer overlapping-change event cancels the in-flight run and its own run enforces the newest state. Free under Rail 2 (the replacement run already exists). |
| ~~Action-step freshness gate~~ — **not needed** | The stale run is cancelled before its waited actions fire, so it never acts on stale state. Investigated and deliberately dropped (see `implementation-log.md`). |
| **Lock/partial-unique-index for the simultaneity race** | Supersede's collision lookup isn't lock-protected; two truly-simultaneous same-person events can both proceed. Rare; tracked in known-issue #29 with a revisit trigger. |
| **5-min lookback buffer narrowing** | Once Phase 4 lands and over-fires stop reaching `wait_and_check_communication`, the buffer's "absorb agent over-fire" job disappears. Should be narrowed back to its true purpose (calls-before-claim race). Trivial to do; deferred to a follow-up that touches the step's config. |
| **FUB-to-local reconciliation / catch-up** | If FUB stops sending webhooks the engine has no recovery path. Whole system already relies on FUB to keep sending; not regressing. Out of scope; address if/when observed. |
| **Stale-assignment guard** (person 19255 case — prior real conversation outside buffer window) | Product concern, not engine bug. Workflow author should add a `person.lastCallAt` predicate; engine should expose the data. |
| **30-min reassign threshold tuning** | Product, not platform. |
| **`event.origin` exclusion default reconsidered** | If a second workflow exposes the opt-in pattern as error-prone, revisit. |
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

Per phase (see [README.md](README.md)) but at the feature level:

- Replay harness covers the 05-08, 05-11, 05-12 incidents recorded in field observations. Each replays cleanly and produces the *expected* sequence under the new architecture (engine echoes collapse, FUB bursts collapse, agent over-fires never produce events).
- Person 20235 scenario (3 webhooks in 8s, all hitting peopleUpdated): under new architecture, exactly one `person.state_changed` event is emitted; one run is created; one reassign is performed.
- Person 20123 scenario (echo cascade after reassign): under new architecture, the engine's reassign updates local state first, the echo webhook diff is empty, no second event emitted.
- The `agent_followup_enforcement` workflow re-authored against the new shape runs the same 26+ days of recorded events with bad-run rate dropping from ~50% to <5% (the residual genuine multi-transition cases now closed by Phase 5 field-aware supersede; only the truly-simultaneous race remains, known-issue #29).

## Linked decisions

- This plan does not require a new entry in `Docs/repo-decisions/`. The state-observation framing is a feature-level architectural choice limited to the workflow-trigger pipeline; it does not cross-cut other subsystems. If the model proves out and we want to canonicalise "state-observation as engine policy," promote to `repo-decisions/` then.
- The engine-echo exclusion model (safe-by-default, two-level gated — global capability + per-workflow `reactToEngineEvents`) is recorded as [`RD-006`](../../repo-decisions/RD-006-engine-echo-exclusion-safe-by-default.md).

## Cross-references

- Driving evidence: [`Docs/features/agent-followup-enforcement/plan.md`](../agent-followup-enforcement/plan.md)
- Bug records: [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) #17, #18, #20, #23, #24, #25
- Earlier draft (superseded by this doc): [`Docs/features/state-change-events/README.md`](../state-change-events/README.md)
- Product backlog: [`Docs/product-discovery/ideas.md`](../../product-discovery/ideas.md)
- Existing engine entry points referenced in the lifecycle diagram:
  - [WebhookIngressService.java](../../../src/main/java/com/flux/service/webhook/WebhookIngressService.java)
  - [WebhookEventProcessorService.java](../../../src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java)
  - [PersonUpsertService.java](../../../src/main/java/com/flux/service/person/PersonUpsertService.java)
  - [FubWebhookTriggerType.java](../../../src/main/java/com/flux/service/workflow/trigger/FubWebhookTriggerType.java)
  - [WorkflowExecutionManager.java](../../../src/main/java/com/flux/service/workflow/WorkflowExecutionManager.java)
  - [WorkflowExecutionDueWorker.java](../../../src/main/java/com/flux/service/workflow/WorkflowExecutionDueWorker.java)

---

## Phase 2 — Implementation Plan (step-by-step)

Status: `DONE` — all 5 sub-phases shipped on `feature/domain-events` (commits `a9dc258`, `41044ba`, `26e8103`, `f3a8036`, `9b73759`). Decision narrative and deviations recorded in [`implementation-log.md`](./implementation-log.md).

Companion to [`README.md`](./README.md) §"Phase 2 — Domain events table + diff machinery". `README.md` is the canonical statement of **what** Phase 2 delivers and **why**; this file is the **commit-level order of operations** — concrete files, sequencing, test gates, defaults.

> **Plan-lock changelog (2026-05-28):** The original 4-sub-phase split layered emission onto unrefactored services and produced four real defects under fresh-eyes review (MANDATORY-propagation crash on the call/note path; lock-hole on the brand-new-person insert race; replay-harness `min` assertions that silently accept a broken collapse; implicit `oldDetails` capture that can be overwritten before read). Root cause: `PersonUpsertService` and `WebhookEventProcessorService` were sized for "webhook → fetch → save," not for "atomic state-change-with-event." This revision inserts a pure **refactor sub-phase 2b** between the scaffold and emission — extracts `CallUpsertService.persistCallFacts`, restructures `PersonUpsertService` to a "capture-old / apply-new" shape, and adds `findBy…ForUpdate` at **both** read sites — so 2c/2d/2e land on a structure where the four defects are impossible to write. **This is the final plan revision.** Anything that surfaces during build goes into `implementation-log.md`, not back into this plan.

### Scope reminder (one paragraph)

By the end of Phase 2, every webhook the engine processes either produces exactly one typed row in a new `events` table (`person.created`, `person.state_changed`, `call.created`, `note.created/updated/deleted`) or is silently collapsed (echoes, FUB-burst dupes, no-op edits). The collapse is **structurally guaranteed under concurrency** (per-person pessimistic write lock applied at both the primary find site and the unique-constraint-race recovery re-read), not just observed in lucky test runs. Events are inserted **inside the caller's transaction**, atomic with the state change, and the in-memory dispatcher fires **only after that transaction commits**. Every emission site is `@Transactional` by construction — `PersonUpsertService.upsertFubPerson`, a new `CallUpsertService.persistCallFacts`, and a newly-`@Transactional` `WebhookEventProcessorService.processNoteDomainEvent` — so the emitter's `MANDATORY` propagation guard fails loudly when violated, never silently. **No listeners are registered in Phase 2** — `WorkflowTriggerRouter.route(event)` continues to run on the existing path unchanged. The substrate Phase 4 needs is in place; Phase 4 wires consumers.

### Sub-phase split — 5 reviewable commits

| Sub-phase | Theme | Approx files / LOC | Risk |
|---|---|---|---|
| **2a** | Scaffold (events table + emitter + dispatcher; no callers) | ~10 / ~250 | Low — no behaviour change |
| **2b** | **Refactor only**: extract `CallUpsertService.persistCallFacts`; restructure `PersonUpsertService` to capture-old/apply-new shape; add `findBy…ForUpdate` and use at both read sites | ~6 / ~300 | Medium — load-bearing services touched, but **no behaviour change**; 528 tests stay green |
| **2c** | Person events emission (lands on the clean shape from 2b) | ~6 / ~400 | **High** — the load-bearing collapse claim is proven here |
| **2d** | Append events: `call.created` from `CallUpsertService`; `note.*` from `@Transactional processNoteDomainEvent` | ~5 / ~200 | Low |
| **2e** | Replay-harness assertions (**exact** counts for collapse fixtures, `min` for append) | ~6 / ~150 | Low |

Each commit ships with a green `./mvnw clean test` (528 tests pre-Phase-2 baseline; new tests added per sub-phase).

---

### Sub-phase 2a — Scaffold

**Goal:** the `events` table exists, the emitter inserts in-tx and dispatches after commit, the dispatcher interface + an empty in-memory impl are registered. Nothing calls any of it yet.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/resources/db/migration/V22__create_events_table.sql` | Create `events` table per [`plan.md`](./plan.md) §"The `events` table"; two indexes (`(event_kind, created_at DESC)`, `(entity_type, entity_id, created_at DESC)`); FK to `webhook_events(id)` `ON DELETE SET NULL` |
| 2 | `src/main/java/com/flux/persistence/entity/EventEntity.java` | JPA mapping; payload via `@JdbcTypeCode(SqlTypes.JSON)` |
| 3 | `src/main/java/com/flux/persistence/repository/EventRepository.java` | `extends JpaRepository<EventEntity, Long>` |
| 4 | `src/main/java/com/flux/service/event/DomainEvent.java` | Record: `eventKind`, `sourceSystem`, `sourceEventId`, `entityType`, `entityId`, `payload` (`JsonNode`) |
| 5 | `src/main/java/com/flux/service/event/DomainEventListener.java` | Interface — `void onEvent(DomainEvent)` |
| 6 | `src/main/java/com/flux/service/event/DomainEventDispatcher.java` | Interface — `void dispatch(DomainEvent)` |
| 7 | `src/main/java/com/flux/service/event/InMemoryDomainEventDispatcher.java` | Impl — **constructor-injected** `List<DomainEventListener>` (Spring auto-wires all `@Component` listeners), modeled on the existing `WebhookDispatcher` pattern (no Spring `ApplicationEventPublisher`). Phase 4 adds a listener by creating a `@Component` implementing the interface — no registration code to touch |
| 8 | `src/main/java/com/flux/service/event/DomainEventEmitter.java` | `@Transactional(propagation = MANDATORY)` `emit(...)`: INSERT row via `EventRepository`, then `TransactionSynchronizationManager.registerSynchronization(afterCommit → dispatcher.dispatch(event))` |
| 9 | `src/test/java/com/flux/integration/EventsTableMigrationPostgresRegressionTest.java` | Testcontainers: column shape matches V22 (names + types), both indexes present, FK present with `ON DELETE SET NULL` |
| 10 | `src/test/java/com/flux/service/event/DomainEventEmitterTest.java` | (a) commit → dispatch fires exactly once, **after** the row is visible to a fresh `Transactional` read; (b) rollback → dispatch never fires; (c) called outside any tx → throws `IllegalTransactionStateException` (this is the regression net that protects the `MANDATORY` invariant going forward) |

#### Order within the commit

1 → (2, 3 in parallel) → 4 → (5, 6 in parallel) → 7 → 8 → (9, 10 in parallel).

#### Test gate

`./mvnw clean test` green; V22 applies on a fresh Testcontainers Postgres; the new emitter test passes.

#### What 2a does NOT change

No caller of `DomainEventEmitter` exists yet. `PersonUpsertService`, `WebhookEventProcessorService`, and `WorkflowTriggerRouter` are untouched. Production behaviour identical.

---

### Sub-phase 2b — Refactor (no behaviour change)

**Goal:** restructure the two load-bearing services so 2c/2d can wire emission cleanly. **Zero behaviour change.** All 528 existing tests stay green; new refactor-confirming tests are added.

Why this sub-phase exists: the current shape of `PersonUpsertService.upsertFubPerson` ("find → mutate entity in place → save") and `WebhookEventProcessorService.processCall` (no enclosing `@Transactional`) makes it impossible to land 2c's emission code without either (a) the emitter's `MANDATORY` guard tripping at first webhook (calls/notes path) or (b) silently overwriting `oldDetails` before the diff reads it. Fixing those at emission time spreads concurrency/transactional concerns across the orchestration layer. Fixing them as a pure refactor first contains the change.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/persistence/repository/PersonRepository.java` | Add `findBySourceSystemAndSourcePersonIdForUpdate(String sourceSystem, String sourcePersonId)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` + explicit `@Query("select p from PersonEntity p where p.sourceSystem = :sourceSystem and p.sourcePersonId = :sourcePersonId")` |
| 2 | `src/main/java/com/flux/service/person/PersonUpsertService.java` | Restructure `upsertFubPerson` to a **capture-old / apply-new** shape: <br>(a) switch the primary finder call to `findBy…ForUpdate`; <br>(b) **also** switch the `DataIntegrityViolationException` recovery re-read to `findBy…ForUpdate` — this closes the brand-new-row insert-race window; <br>(c) for the existing-row branch, capture `JsonNode oldDetails = existing.getPersonDetails()` as a **named local before any mutation**, then build `newDetails` as a separate value (`buildSnapshot(personPayload)` already returns this), then apply the in-memory mutations in one block (`setPersonDetails(newDetails)`, `setKind`, `setUpdatedAt`, `setLastSyncedAt`) immediately before `save`; <br>(d) no event emission yet — `oldDetails` is captured but unused in 2b (2c reads it). The capture-before-mutate shape is what makes 2c's diff correct by construction |
| 3 | `src/main/java/com/flux/service/call/CallUpsertService.java` (new) | New service in a new package `service/call/`. Owns one method: `@Transactional public void persistCallFacts(NormalizedWebhookEvent event, ProcessedCallEntity entity, CallDetails callDetails)`. Body is **lifted verbatim** from `WebhookEventProcessorService.persistCallFacts` (lines ~268–290) — sets fields on `entity`, calls `processedCallRepository.save(entity)`, performs the orphan-person warn-log. **Surgical extraction**: retry/decision-engine logic stays in `WebhookEventProcessorService.processCall` |
| 4 | `src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java` | (a) inject `CallUpsertService`; (b) replace the inline `persistCallFacts(event, entity, callDetails)` call inside `processCall` with `callUpsertService.persistCallFacts(event, entity, callDetails)`; (c) **delete** the now-orphaned private `persistCallFacts` method and the `personRepository` field if it's only used there (verify grep first) |
| 5 | `src/test/java/com/flux/service/call/CallUpsertServiceTest.java` (new) | Verifies the lifted persistence behaviour: saves the entity with correct fields; emits the orphan-person warn log when the person row is missing; runs inside a transaction (assert via `TransactionSynchronizationManager.isActualTransactionActive()` inside the method under test, or by checking `MANDATORY`-propagation emit would succeed when called from inside it — the latter is wired in 2d) |
| 6 | `src/test/java/com/flux/service/person/PersonUpsertServiceTest.java` *(extend existing)* | New cases: brand-new + concurrent insert race → recovery path also takes the lock (assert by `TransactionSynchronizationManager` + integration test in 2c); capture-old / apply-new shape preserves all existing upsert behaviour byte-for-byte (the existing tests already enforce this — no new behaviour assertions needed beyond "the refactor didn't break anything"); existing test for `mapStageToKind` unaffected |

#### Order within the commit

1 (lock method on repo) → 2 (PersonUpsertService restructure, run existing tests to confirm zero diff) → 3 (extract CallUpsertService) → 4 (rewire WebhookEventProcessorService) → 5 + 6 (refactor-confirming tests).

#### Test gate

`./mvnw clean test` green. All 528 existing tests pass unchanged. New `CallUpsertServiceTest` passes.

#### What 2b does NOT change

No `events` row is ever written (no caller of `DomainEventEmitter` yet). No event_kind logic. `WorkflowTriggerRouter.route(event)` is untouched. Production-observable behaviour identical to before 2b.

---

### Sub-phase 2c — Person events emission

**Goal:** `PersonUpsertService` emits `person.created` / `person.state_changed` under the per-person row lock established in 2b. The collapse claim is proven by a deterministic concurrency stress test.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/person/PersonDiffComputer.java` (new) | Per-field strategy: scalars via `JsonNode.equals`; `tags` → sort both then equals; `phones`/`emails` → `Set<JsonNode>` equals (order-independent at element level). Returns `DiffResult(List<String> changedFields, ObjectNode previous, ObjectNode current)` containing **only changed fields** in `previous`/`current` |
| 2 | `src/main/java/com/flux/service/person/PersonUpsertService.java` | Inject `DomainEventEmitter` + `PersonDiffComputer` + (already-present) `ObjectMapper`. Wire emission inside the existing `@Transactional`, on the clean structure from 2b: <br>• brand-new row (either the no-existing branch OR the DIVE-recovery branch — both must emit consistently) → emit `person.created` with payload `{ current: <full snapshot> }`, set `previousState = null`; <br>• existing + non-empty diff → set `previousState = oldDetails` (the local captured in 2b), emit `person.state_changed` with `{ changed_fields, previous, current }`; <br>• empty diff → emit nothing, `previousState` untouched |
| 3 | `src/test/java/com/flux/service/person/PersonDiffComputerTest.java` (new) | Matrix: scalar change; scalar no-op; tag add; tag reorder (no-op); tag remove; phone add; phone reorder (no-op); phone remove; email change; mixed multi-field change |
| 4 | `src/test/java/com/flux/service/person/PersonUpsertServiceTest.java` *(extend)* | New cases: brand-new → 1 `person.created` row + correct payload + `previousState` null; update with diff → 1 `person.state_changed` row, payload contains only changed fields in `previous`/`current`, `previousState` = old details; echo upsert (identical JSON in) → 0 events, `previousState` untouched; `mapStageToKind` runs unaffected by emission |
| 5 | `src/test/java/com/flux/integration/PersonUpsertConcurrencyStressTest.java` (new) | Testcontainers Postgres + `CountDownLatch`-released N=10 parallel `upsertFubPerson` calls for the **same** `sourcePersonId`. Two scenarios: (a) row already exists → assert **exactly 1** `person.state_changed` row in `events` (the row lock from 2b serializes); (b) row does not exist → assert **exactly 1** `person.created` row (the DIVE recovery path now takes the lock too — closes the brand-new-row race). Run twice in the test (`@RepeatedTest(3)`) to catch nondeterminism |

#### Order within the commit

1 + 3 first (diff computer in isolation) → 2 (wiring on the 2b structure) → 4 (emission unit tests) → 5 (concurrency proof).

#### Test gate

`./mvnw clean test` green + concurrency stress test green on `@RepeatedTest(3)`. If 5 flakes on any run, **investigate and fix before merging** — flakiness here means the collapse claim is not actually held.

#### What 2c changes for users

The `events` table starts getting `person.created` / `person.state_changed` rows. Nothing reads them. `WorkflowTriggerRouter.route(event)` still runs unchanged.

---

### Sub-phase 2d — Append events

**Goal:** `call.created` and `note.created/updated/deleted` flow through the same emitter, each from inside its own `@Transactional` boundary.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/webhook/parse/FubWebhookParser.java` | Map `notesCreated` → `(NormalizedDomain.NOTE, NormalizedAction.CREATED)`; `notesUpdated` → `UPDATED`; `notesDeleted` → `DELETED` |
| 2 | `src/main/java/com/flux/service/webhook/support/StaticWebhookEventSupportResolver.java` | Register `notesCreated/Updated/Deleted` as supported, with consistent descriptions |
| 3 | `src/main/java/com/flux/service/call/CallUpsertService.java` | Inject `DomainEventEmitter`. After `processedCallRepository.save(entity)`, emit `call.created` with the full FUB call payload. `source_event_id` is the **webhook** id (`event.webhookEventId()`), not the FUB call id — that's already the entity_id |
| 4 | `src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java` | (a) add `case NOTE -> processNoteDomainEvent(event)` to the `process()` switch; (b) new **`@Transactional` private** method `processNoteDomainEvent(NormalizedWebhookEvent event)` — emits `note.created` / `note.updated` / `note.deleted` per `event.normalizedAction()`, payload = the webhook payload. The `@Transactional` annotation is what makes the emitter's `MANDATORY` guard pass. **No `notes` table** — body content not fetched; deferred to a future workflow that needs it. No `NoteUpsertService` — there's no state to own |
| 5 | `src/test/java/com/flux/service/FubWebhookParserNormalizedContractTest.java` *(extend)* | Note event types parse to `NormalizedDomain.NOTE` + the right `NormalizedAction` |
| 6 | `src/test/java/com/flux/service/call/CallUpsertServiceTest.java` *(extend)* | A `callsCreated` webhook → 1 `call.created` row with correct payload + `source_event_id` set |
| 7 | `src/test/java/com/flux/service/webhook/WebhookEventProcessorServiceTest.java` *(extend)* | `notesCreated/Updated/Deleted` → 1 matching `note.*` row each; emission happens inside the `processNoteDomainEvent` transaction (assert by rolling the tx back in a test and confirming no event row appears) |

#### Order within the commit

1 + 5 → 2 → 3 + 6 (call.created on the extracted service) → 4 + 7 (note branch on `WebhookEventProcessorService`).

#### Test gate

`./mvnw clean test` green.

#### What 2d changes for users

`events` table now also receives `call.created` and `note.*` rows. Still no consumers.

---

### Sub-phase 2e — Replay-harness assertions

**Goal:** turn the harness from "fires webhooks and asserts workflow_runs" into "fires webhooks and asserts the events table" so the collapse + append invariants are verified end-to-end on the real recorded incidents. **Exact counts** for collapse claims; `min` only where uniqueness isn't an invariant.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/test/java/com/flux/replay/ReplayFixture.java` | Add to `Expected`: <br>• `Map<String,Integer> expectedStateChangeEventsForPerson` (**exact** count per `sourcePersonId`) <br>• `Map<String,Integer> expectedCreatedEventsForPerson` (**exact**) <br>• `Map<String,Integer> minAppendEvents` (`event_kind → min count`, e.g. `{"call.created": 1, "note.created": 2}` — `min` is correct here because no uniqueness claim applies to append events) |
| 2 | `src/test/java/com/flux/replay/ReplayHarnessTest.java` | Inject `EventRepository`; assertion methods that count events by kind + entity; plug into `expectationsMet` poll and `assertExpectations`. The `expected*` assertions fail on **either** under-count (collapse missed) **or** over-count (collapse broke) — that's the whole point |
| 3 | `src/test/resources/replay-fixtures/person-20235-fub-burst-2026-05-12.json` | Add `expected.expectedStateChangeEventsForPerson: {"20235": 1}` — exact-1 proves the FUB-burst collapse |
| 4 | `src/test/resources/replay-fixtures/person-20231-fub-burst-2026-05-12.json` | Add `expected.expectedStateChangeEventsForPerson: {"20231": 1}` — exact-1 on the 4-webhook burst |
| 5 | `src/test/resources/replay-fixtures/person-20123-echo-cascade-2026-05-08.json` | Add `expected.expectedStateChangeEventsForPerson: {"20123": 2}` (1 real assignment + 1 phantom from the engine echo) with `notes` field saying *"Phase 3's local-state-first writes will flip this to 1"*. The exact-2 is honest about Phase 2's behaviour today — flipping to exact-1 is Phase 3's assertion change |
| 6 | `src/test/resources/replay-fixtures/synthesized-note-created.json` (new) | One `notesCreated` webhook → assert `expected.minAppendEvents: {"note.created": 1}` |

#### Order within the commit

1 + 2 → 3, 4, 5 → 6.

#### Test gate

`./mvnw clean test` green. Replay harness reports the collapse and append invariants holding on every recorded incident.

#### What 2e changes for users

Verification net is structural, not lucky. Phase 2 complete.

---

### Defaults (locked at plan-finalize time — override on PR review only with explicit justification)

| Decision | Default | Reason |
|---|---|---|
| `DomainEventEmitter.emit` tx propagation | `@Transactional(propagation = MANDATORY)` | A caller forgetting `@Transactional` should fail loudly (`IllegalTransactionStateException`) instead of silently producing un-dispatched / lost events. **Safe by construction**: every emission site post-2b is `@Transactional` — `PersonUpsertService.upsertFubPerson`, `CallUpsertService.persistCallFacts`, `WebhookEventProcessorService.processNoteDomainEvent`. The 2a unit test (case c) is the regression net |
| `@Lock` on `findBy…ForUpdate` | Explicit `@Query` (`select p from PersonEntity p where ...`) | Spring Data is finicky about applying `@Lock` to plain derived methods; explicit JPQL avoids surprises |
| **`findBy…ForUpdate` call sites** | **Both** the primary finder in `upsertFubPerson` **and** the `DataIntegrityViolationException` recovery re-read | The collapse claim ("N webhooks → 1 event") fails for brand-new persons if only the primary site uses the lock. The recovery path is the *only* path that runs when N parallel inserts race; it must take the lock too |
| Diff payload shape for `person.state_changed` | Nested `{ changed_fields: [...], previous: {...}, current: {...} }` | Matches `plan.md` §"The `events` table"; Phase 4 projects `change.<field>.old/.new` from this shape cleanly |
| `DomainEvent.payload` type | `JsonNode` (Jackson) | Same shape used in entity columns, FUB client returns, and the existing expression scope — avoids serialization round-trips in the hot path |
| Dispatcher impl name | `InMemoryDomainEventDispatcher` | Mirrors the deferred `RedisEngineWriteTracker` naming pattern; interface boundary stays the commitment, impl is swappable |
| Dispatcher listener registration | Constructor-injected `List<DomainEventListener>`, modeled on `WebhookDispatcher` | Phase 4 adds a listener by creating a `@Component` implementing `DomainEventListener`. No registration code to touch; Spring discovers and wires |
| `CallUpsertService` extraction scope | **Surgical**: only `persistCallFacts` moves; retry/decision-engine/task-creation stays in `WebhookEventProcessorService` | Smallest viable extraction that makes the call emission path `@Transactional` without bundling unrelated refactor |
| Note event handling | Inline `@Transactional processNoteDomainEvent` on `WebhookEventProcessorService` | No `notes` table = no state to own = no `NoteUpsertService` justified. The `@Transactional` annotation is the only thing the emitter cares about |
| Replay-harness collapse assertions | `expected*ForPerson` (**exact**) for person events; `minAppendEvents` (min) for append | `min` would silently accept a broken collapse — the bug Phase 2 prevents. Exact-count is the only assertion strong enough to catch the regression |

### Out of scope (deferred to other phases)

| Item | Why deferred | Phase that owns it |
|---|---|---|
| `dispatched` flag + durable outbox poller | No consumers in Phase 2; app not deployed; cleanly additive later | Phase 4 decision |
| `change.source = "ENGINE"` annotation on echoes | Needs `EngineWriteTracker` | Phase 3 |
| Any change to `WorkflowTriggerRouter.route()` | Old path runs unchanged until Phase 4's hard cut | Phase 4 |
| `notes` table + on-demand FUB note body fetch | Workflows can fetch on demand when a real consumer needs body content | Follow-up after Phase 4 |
| Full extraction of `processCall` orchestration into `CallUpsertService` (retry / decision engine / task creation) | Phase 2 only needs the persistence layer to be transactional; bundling the orchestration refactor would bloat the diff | Follow-up after Phase 4 |

### Cross-references

- High-level deliverables: [`README.md`](./README.md) §"Phase 2 — Domain events table + diff machinery"
- Architectural rationale + invariants: [`plan.md`](./plan.md) §"The `events` table", §"Per-person serialization of the upsert", §"Emit in-transaction, dispatch after commit"
- Driving evidence: [`Docs/features/agent-followup-enforcement/plan.md`](../agent-followup-enforcement/plan.md)
- Phase 0 harness this builds on: [`implementation-log.md`](./implementation-log.md)
- Implementation log to be written at end: `implementation-log.md` (does not exist yet; create when 2e ships)

---

## Phase 3 — Implementation Plan (step-by-step)

Status: `DONE` — 3a, 3b, 3c, 3d, 3e shipped; wrap-up in [`implementation-log.md`](./implementation-log.md).

> **Plan-lock changelog (2026-06-01): 3e reduced to a single channel.** The original §3e two-channel design rested on the assumption that creating a note makes FUB fire a `peopleUpdated` echo (carrying `lastNoteAt`-style metadata) that needs annotating. That assumption is **confirmed false** three independent ways: (1) **empirical** — created notes produced no `peopleUpdated` webhook; (2) **FUB API docs** — the `peopleUpdated` trigger field list (Name, Emails, Phones, Address, Price, Background, Assigned Agent, Assigned Lender, Contacted, Stage, Lead Source, Tags, Custom Fields, Relationships) does not include note activity, and note creation is documented to fire only `notesCreated`; (3) **code** — `lastNoteAt`/`lastActivity` are not in `PersonUpsertService.SNAPSHOT_FIELDS` nor `PersonDiffComputer`, so even a hypothetical person echo would diff to empty and emit no `person.state_changed` event. With **no person event ever produced to annotate**, the person-side channel is inert. 3e ships **single-channel** (`note.created` annotation only). The two-channel `SideEffectRecorder` in `applyEntityCreateTrackedOnly` is kept as-is (over-general but harmless; zero churn to tested infra). The original **D2** scenario (person-side echo annotated) is **dropped** — there is no such echo. No tripwire/disabled test stands in for it: a test cannot detect FUB changing its webhook semantics; that watch lives in known-issue #27 + a `SNAPSHOT_FIELDS` code comment + a Phase 4 exit criterion instead.

Companion to [`README.md`](./README.md) §"Phase 3 — Local-state-first engine writes". `README.md` is the canonical statement of **what** Phase 3 delivers and **why**; this file is the **commit-level order of operations** — concrete files, sequencing, test gates, defaults.

> **Plan-lock changelog (2026-05-29):** Phase 3 was originally framed as "wrap 4 step types with local-state-first writes." [`plan.md`](./plan.md) walked the matrix of (step type × scenario × timing × concurrency) and exposed that the 4 steps fall into **3 fundamentally different mechanisms**, not one. This plan restructures Phase 3 accordingly:
>
> - **Revert dropped entirely.** `RetryPolicy.DEFAULT_FUB` already handles transient FUB failures; permanent failures accept drift until the next webhook re-syncs. Plan.md §5's "restore prior snapshot" semantics are reversed (see §5 changelog).
> - **Three operation modes via `EngineWriteCoordinator`,** not one wrap pattern: `SCALAR_FIELD_UPDATE` (reassign, move_to_pond), `ENTITY_APPEND_TRACKED_ONLY` (add_tag — no local-state-first), `ENTITY_CREATE_TRACKED_ONLY` (create_note — no local state to write).
> - **`REQUIRES_NEW` is a pattern requirement,** not a footnote. The outer `@Transactional` on `WorkflowStepExecutionService.executeClaimedStep` (line 68) would otherwise pin the row lock across the FUB HTTP call. Every inner write goes through `TransactionTemplate(REQUIRES_NEW)`, mirroring `PersonUpsertService`'s DIVE-recovery discipline.
> - **Tags become tracker-only.** Phase 3 will not local-state-first for `tags`. C2 (phantom "tag removed" event from concurrent external add) is structurally unfixable with optimistic local writes; tracker-only annotates the real echo as `source=ENGINE`.
> - **Notes become tracker-only on two channels.** `note.created` echo and the person-side `peopleUpdated` echo (`lastNoteAt` etc.) both get tracker annotation. Documented as a known annotation-needs-verification entry until Phase 4 actually consumes these events.
> - **Race harness is a Phase 3 deliverable**, distributed across sub-phases (A-cells in 3b, C-cells in 3d, D-cells in 3e). Skeleton + fake FUB client in 3a.
> - **Redis-backed tracker promoted to Phase 4 prerequisite.** Phase 3 ships in-memory; the crash-window phantom-event class is dev-acceptable until consumers exist.

### Scope reminder (one paragraph)

By the end of Phase 3, every engine-originated write to FUB (`fub_reassign`, `fub_move_to_pond`, `fub_add_tag`, `fub_create_note`) flows through `EngineWriteCoordinator`, which records the intent in `InMemoryEngineWriteTracker` and (for scalar updates only) applies the change to local Person state before the FUB call. `DomainEventEmitter` consults the tracker when a `person.state_changed`, `note.created`, or note-side `person.state_changed` event is about to emit, and annotates `payload.source = "ENGINE"` when the diff matches a recent tracker record. No workflow filters on this annotation in Phase 3 — `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` still runs on the old webhook-shaped path unchanged. Phase 4 is when annotation starts being read. Phase 3 is pure substrate; the bad-run-rate win arrives at Phase 4.

### Sub-phase split — 5 reviewable commits

| Sub-phase | Theme | Approx files / LOC | Risk |
|---|---|---|---|
| **3a** | Scaffold: tracker interface + in-memory impl + coordinator skeleton (3 op modes wired but no callers) + emitter annotation hook + race harness skeleton with fake FUB client | ~12 / ~600 | Low — no behaviour change |
| **3b** | Wrap `fub_reassign` (scalar mode) + race harness scenarios A1–A7 | ~5 / ~350 | **High** — the load-bearing pattern (REQUIRES_NEW + lock + tracker + emitter annotation) lands here |
| **3c** ✅ | Wrap `fub_move_to_pond` (scalar mode, reuses 3b coordinator path) + harness A1/A3/A4/A5 mirror for `assignedPondId`. **DONE** — 672 tests green. | ~3 / ~150 | Low — identical pattern to 3b |
| **3d** ✅ | Wrap `fub_add_tag` (tracker-only append mode) + harness C1–C3. **DONE** — 676 tests green. | ~4 / ~250 | Medium — different mechanism; first exercise of tracker-only path |
| **3e** ✅ | Wrap `fub_create_note` (tracker-only, **single channel** — `note.created` only; person-side channel ruled out, see 2026-06-01 changelog) + harness **D1/D3/D4** + `NoteEmissionService` annotation hook. **DONE** — 682 tests green. | ~5 / ~250 | Medium — first entity-create wrap; early-echo race documented (D3) |

Each commit ships with a green `./mvnw clean test` (589 tests post-Phase-2 baseline; new tests added per sub-phase).

---

### Sub-phase 3a — Scaffold

**Goal:** the tracker exists, the coordinator exists with all 3 op modes wired, the emitter consults the tracker for annotation. Nothing calls the coordinator yet. Race harness skeleton + fake FUB client + assertion infrastructure are in place but no scenarios are codified.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/event/EngineWriteTracker.java` | Interface: `record(TrackerEntry)`, `findMatching(entityType, entityId, changedFields, withinMs) → Optional<EngineWriteRecord>`, `evictExpired(now)`. **No `markFailed` method** — revert is dropped (see plan-lock §"Revert dropped"). |
| 2 | `src/main/java/com/flux/service/event/EngineWriteRecord.java` | Record: `id`, `entityType`, `entityId`, `changedFields` (Set<String>), `runId`, `recordedAt`. Used as the tracker's value type and the `findMatching` return. |
| 3 | `src/main/java/com/flux/service/event/InMemoryEngineWriteTracker.java` | Impl: `ConcurrentHashMap<TrackerKey, List<EngineWriteRecord>>` keyed on `(entityType, entityId)`. **Scheduled eviction every 10s** via `@Scheduled`. **Default TTL 30s** configurable via `engine.write.tracker.ttl-seconds`. Hit/miss/eviction logged at INFO per [`README.md`](./README.md) §Phase 3 deliverable 5. |
| 4 | `src/main/java/com/flux/service/event/EngineWriteCoordinator.java` | Interface with three methods: <br>• `<T> StepExecutionResult applyScalarFieldUpdate(String sourcePersonId, Map<String, JsonNode> fieldUpdates, Long runId, Supplier<T> fubCall)` <br>• `<T> StepExecutionResult applyEntityAppendTrackedOnly(String sourcePersonId, String fieldName, JsonNode appendedValue, Long runId, Supplier<T> fubCall)` <br>• `<T extends CreatedEntityRef> StepExecutionResult applyEntityCreateTrackedOnly(String sourcePersonId, String entityType, Long runId, Supplier<T> fubCall, BiConsumer<EngineWriteTracker, T> recordSideEffects)` <br>Each method also returns the FUB call's result via the `StepExecutionResult` payload. |
| 5 | `src/main/java/com/flux/service/event/DefaultEngineWriteCoordinator.java` | Impl: <br>• **Scalar mode**: `REQUIRES_NEW` inner tx → `personRepository.findBy…ForUpdate` → apply field updates → `tracker.record(...)` → commit. Lock released. FUB call **outside any tx**. Return success or step-failure result. **No revert path.** <br>• **Append-tracked-only mode**: FUB call first (no tx). On success, `REQUIRES_NEW` inner tx → `tracker.record(...)` (entity + field + appended value as the "changedField" marker) → commit. <br>• **Entity-create-tracked-only mode**: FUB call first. On success, `REQUIRES_NEW` inner tx → invoke `recordSideEffects` callback (caller decides what tracker entries to write — typically one for the new entity id and one for the person-side echo field). |
| 6 | `src/main/java/com/flux/service/event/DomainEventEmitter.java` *(modify)* | Inject `EngineWriteTracker`. In `emit(...)`, before the in-tx insert, call `tracker.findMatching(entityType, entityId, payloadChangedFields, withinMs=tracker.ttl)`. If hit, mutate the payload to add `"source": "ENGINE"` at the top level. Emit row + after-commit dispatch as before. Tracker miss → no annotation; payload unchanged. |
| 7 | `src/main/java/com/flux/service/event/DomainEvent.java` *(possibly modify)* | If the `source` annotation is part of the in-memory `DomainEvent` shape (not just the persisted `payload` JSON), add an optional `source` field. **Decision: keep it in `payload` only.** Tracker hits mutate the payload JSON; downstream consumers (Phase 4) read `event.payload.source`. No new field on the record. Avoids churning every listener signature. |
| 8 | `src/main/resources/application.properties` *(extend)* | `engine.write.tracker.ttl-seconds=30`; `engine.write.tracker.eviction-interval-ms=10000` |
| 9 | `src/test/java/com/flux/race/EngineWriteRaceHarness.java` | Skeleton: Testcontainers Postgres + Spring context with `FakeFollowUpBossClient` wired. Provides scenario DSL: `engine().reassigns(personId).to(userId).at(tMs)`, `external().reassigns(personId).to(userId).at(tMs)`, `expect().exactStateChangedEvents(personId, n)`, `expect().eventPayloadAnnotated("ENGINE")`. CountDownLatch orchestration for releasing engine + webhook threads at configured offsets. |
| 10 | `src/test/java/com/flux/race/FakeFollowUpBossClient.java` | `@TestConfiguration` `@Primary` bean replacing `FollowUpBossClient`. Per-method configurable response delay + canned response (success / transient / permanent). Records every call for harness assertions. |
| 11 | `src/test/java/com/flux/service/event/InMemoryEngineWriteTrackerTest.java` | Record + findMatching with exact field-set match; with field-set subset (engine wrote A,B; diff includes A,B,C → hit because `engineFields ⊆ diffFields`); TTL eviction; concurrent record + read. |
| 12 | `src/test/java/com/flux/service/event/DefaultEngineWriteCoordinatorTest.java` | Scalar mode happy path; scalar mode FUB failure (no revert — local stays updated, step returns failure result); append mode happy path; entity-create mode happy path; **REQUIRES_NEW semantics**: if the outer caller is in a transaction, the inner write commits independently (verify by rolling back the outer and confirming the tracker record + local change persist). |
| 13 | `src/test/java/com/flux/service/event/DomainEventEmitterAnnotationTest.java` | Tracker hit → payload gains `source=ENGINE`; tracker miss → payload unchanged; emit-then-rollback still doesn't dispatch (Phase 2 invariant preserved). |

#### Order within the commit

1, 2 → 3 → 4 → 5 → 6 → 7 (decision-only) → 8 → 9, 10 (in parallel) → 11, 12, 13 (in parallel).

#### Test gate

`./mvnw clean test` green. 589 → 600+ tests (12 new). No existing test changes behaviour.

#### What 3a does NOT change

No production caller of `EngineWriteCoordinator` exists yet. `FubReassignWorkflowStep`, `FubMoveToPondWorkflowStep`, `FubAddTagWorkflowStep`, `FubCreateNoteWorkflowStep` are untouched. `DomainEventEmitter` is modified but tracker is always empty in production → annotation never triggers → existing behaviour preserved.

#### Tracker match semantics — locked here

- `findMatching(entityType, entityId, changedFields, withinMs)` returns a hit when **the tracker record's `changedFields` ⊆ the supplied `changedFields`**. Rationale: the engine wrote a known set of fields; the diff may include additional fields touched by concurrent activity; we annotate as ENGINE because at least some of the diff is ours.
- This is the loosest reasonable match and intentionally annotates the [A4](./plan.md#a-fub_reassign-scalar--assignedUserId) "real concurrent external change → phantom event" case as `ENGINE` even though the *trigger* of the event was external. The trade-off: Phase 4 workflows filtering `change.source != "ENGINE"` will silently suppress these. Acceptable because the alternative (strict equality match) misses A4 entirely.
- Documented in `known-issues.md` as a deliberate annotation-over-detection bias.

---

### Sub-phase 3b — Wrap `fub_reassign` (scalar mode) + race harness A-cells

**Goal:** `fub_reassign` routes through the coordinator's scalar mode. The race harness proves cells A1–A7 from [`plan.md`](./plan.md).

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/workflow/steps/FubReassignWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Replace the direct `fubCallHelper.executeWithRetry(() -> followUpBossClient.reassignPerson(...))` call with `coordinator.applyScalarFieldUpdate(sourcePersonId, Map.of("assignedUserId", numericNode(targetUserId)), runId, () -> fubCallHelper.executeWithRetry(...))`. The retry stays inside the FUB-call Supplier — the coordinator owns tx semantics, the helper owns retry semantics. |
| 2 | `src/test/java/com/flux/service/workflow/steps/FubReassignWorkflowStepTest.java` *(extend)* | Existing tests stay green (FUB call path unchanged from the workflow runtime's perspective). New tests: coordinator is invoked with the right field map; transient FUB failure still propagates as `transientFailure(FUB_REASSIGN_TRANSIENT)`; permanent FUB failure still propagates as `failure(FUB_REASSIGN_PERMANENT)`; local Person state is updated before the FUB call (verify via Testcontainers). |
| 3 | `src/test/java/com/flux/race/scenarios/ReassignScenariosTest.java` | All 7 A-cells codified: <br>**A1** happy path — single engine reassign, echo arrives later, expect 0 `person.state_changed` events. <br>**A2** echo-during-commit — fake FUB delays 10ms; webhook fires at t=5ms; expect lock blocks, 0 events. <br>**A3** FUB-burst echo — 3 echoes for one engine write; expect 0 events. <br>**A4** real concurrent external change — engine writes Alice at t=0; external webhook to Carol arrives t=30ms; engine FUB PUT lands t=200ms; echo arrives t=500ms. Expect: 1 real event (Alice→Carol from external), 1 phantom event (Carol→Alice) **annotated `source=ENGINE`**. Document the annotation-over-detection bias in test comments. <br>**A5** FUB permanent failure, no concurrent activity — engine writes Alice, FUB returns 400 permanent. Expect: local stays Alice (no revert), step returns FAILED, no event. Next synthetic webhook with FUB ground-truth Bob → diff Alice→Bob → real event emits (the "misleading echo after permanent failure" known issue). <br>**A6** *(was: FUB transient with concurrent — destroys Carol)* — re-cast as: FUB transient, retry succeeds. Engine writes Alice, FUB transient at t=10ms, external Carol at t=20ms, retry succeeds at t=60ms putting Alice. Expect: 1 real event from external (Alice→Carol), 1 phantom (Carol→Alice) annotated ENGINE. Same outcome as A4; retry doesn't change the race. <br>**A7** two engine writes back-to-back — workflow A writes Alice (t=0), workflow B writes Carol (t=5ms); both FUB PUTs race. Expect: final echo annotated ENGINE regardless of PUT order (both writes have tracker records). |

#### Order within the commit

1 → 2 → 3.

#### Test gate

`./mvnw clean test` green. Race harness scenarios `@RepeatedTest(3)` to catch nondeterminism — if any A-cell flakes, **investigate before merge**, do not rerun.

#### What 3b changes for users

`fub_reassign` engine writes now update local first and record on tracker. Echo webhooks for reassigns produce no event (happy path) or annotated events (concurrent-change cases). Nothing reads the annotation yet.

#### Smoking-gun verification

A1 + A3 with `fakeFub.delayMs(500)` confirm that the lock is NOT held across the FUB call: concurrent webhooks for the same person don't block on the engine's FUB round-trip. If the implementation accidentally holds the lock (the smoking-gun defect), these tests time out or report serialized webhook processing.

---

### Sub-phase 3c — Wrap `fub_move_to_pond`

**Goal:** identical wrap as `fub_reassign` for `assignedPondId`. Reuses the entire scalar mode pipeline. Race harness B coverage is identical to A; codify by parameterization, not duplication.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/workflow/steps/FubMoveToPondWorkflowStep.java` *(modify)* | Same shape as 3b#1: route through `coordinator.applyScalarFieldUpdate(sourcePersonId, Map.of("assignedPondId", numericNode(targetPondId)), runId, ...)`. |
| 2 | `src/test/java/com/flux/service/workflow/steps/FubMoveToPondWorkflowStepTest.java` *(extend)* | Mirror 3b#2's test additions for the pond field. |
| 3 | `src/test/java/com/flux/race/scenarios/MoveToPondScenariosTest.java` | A1, A3, A4, A5 mirrored for `assignedPondId`. A2/A6/A7 are coordinator-level concerns already proven in 3b — not re-tested per step. Keeps the suite focused. |

#### Order within the commit

1 → 2 → 3.

#### Test gate

`./mvnw clean test` green. Coverage delta: 4 new race scenarios.

#### What 3c changes for users

`fub_move_to_pond` engine writes go through the same pipeline as reassign. Same suppression / annotation behaviour.

---

### Sub-phase 3d — Wrap `fub_add_tag` (tracker-only mode)

**Goal:** `fub_add_tag` calls FUB first, then records the tag-append on the tracker. **No local-state-first write to `tags`.** Race harness scenarios C1–C3 prove the tracker-only path correctly annotates echoes for engine writes and lets external concurrent tag changes flow through unaffected.

#### Why tracker-only for tags

`tags` is an accumulating field. Optimistic local-state-first writes (write `[A,B,NEW]` locally before FUB confirms) cause [C2](./plan.md#c-fub_add_tag-accumulating--tags): any concurrent external webhook arriving before our FUB PUT lands sees local `[A,B,NEW]` and FUB payload `[A,B,X]` (no NEW yet) and emits a phantom `person.state_changed` event claiming NEW was removed. The "removal" is fabricated and indistinguishable from a real external removal.

Tracker-only inverts this: the FUB call happens first; the local update is applied by `PersonUpsertService` when the echo arrives (truth-from-FUB); the tracker annotates the echo's `person.state_changed` event as `source=ENGINE` so workflows can filter.

The cost is one extra event per engine tag-add (annotated). The benefit is eliminating the phantom-removal class entirely.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/workflow/steps/FubAddTagWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Route through `coordinator.applyEntityAppendTrackedOnly(sourcePersonId, "tags", textNode(tagName), runId, () -> followUpBossClient.addTag(personId, tagName))`. **No local Person write.** The coordinator records on the tracker with `changedFields = Set.of("tags")` only on FUB success. |
| 2 | `src/test/java/com/flux/service/workflow/steps/FubAddTagWorkflowStepTest.java` *(extend)* | Existing tests stay green. New: coordinator invoked with field=`tags` + tag value; FUB failure → no tracker record; FUB success → tracker record present. |
| 3 | `src/test/java/com/flux/race/scenarios/AddTagScenariosTest.java` | <br>**C1** happy path — engine adds NEW; FUB returns success; echo `[A,B,NEW]` arrives; expect 1 `person.state_changed` event **annotated `source=ENGINE`** (because local started at `[A,B]`, payload is `[A,B,NEW]`, diff is real but tracker hit). <br>**C2** external concurrent add — engine adds NEW (t=0), external adds X at FUB (t=10), external webhook `[A,B,X]` arrives t=30ms, engine FUB PUT lands t=200ms, engine echo `[A,B,X,NEW]` arrives t=500ms. Expect: 1 real event from external (no tracker hit because engine hasn't recorded yet — recording happens after FUB success), 1 event from engine echo annotated `ENGINE`. **No phantom "NEW removed" event** because local never had an optimistic NEW. <br>**C3** FUB failure — engine adds NEW, FUB returns 500. Expect: no tracker record; no local change; step returns FAILED. No phantom event possible because local was never modified. |
| 4 | `src/test/resources/known-issues-domain-events.md` *(or update existing)* | Entry: "When Phase 4 introduces `note.created` triggers, the `fub_create_note` tracker annotation needs verification against real consumer behaviour." See [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) Phase 3 entries (added separately). |

#### Order within the commit

1 → 2 → 3.

#### Test gate

`./mvnw clean test` green. C1, C2, C3 deterministic (real Postgres + controlled timing).

#### What 3d changes for users

Engine tag-adds no longer modify local state directly. The tag appears in local only when FUB's echo confirms. Net behaviour for end-users is unchanged; the event stream gains one annotated `person.state_changed` event per engine tag-add (versus zero in the optimistic-local model, but with no phantom-removal risk).

---

### Sub-phase 3e — Wrap `fub_create_note` (tracker-only, single channel)

**Goal:** `fub_create_note` calls FUB first, then records the note-creation on the tracker (single channel, keyed on the returned `noteId`). When FUB echoes the `notesCreated` webhook, `NoteEmissionService` annotates the `note.created` event `source=ENGINE`. Race harness D1/D3/D4 exercise the channel.

#### Single channel — the person-side channel was ruled out (2026-06-01)

The original plan had a second channel for a person-side `peopleUpdated` echo (FUB updating `lastNoteAt` on note creation). **That echo does not exist** — confirmed empirically, by FUB's API docs, and by the code (see the 2026-06-01 changelog at the top of this file). Creating a note fires only `notesCreated`; it does not touch any snapshotted person field, so no `person.state_changed` event is ever produced. There is therefore nothing to annotate on the person side, and the channel is dropped entirely.

- **Channel 1 (the only channel) — `note.created` echo:** the coordinator records on the tracker with `entityType="note"`, `entityId=<returned FUB note id>`, `changedFields=Set.of("created")`. When `NoteEmissionService` is about to emit `note.created`, it consults the tracker and annotates if hit. This annotation lives in `NoteEmissionService` (not the universal `DomainEventEmitter` hook) because that hook keys off `payload.changed_fields`, which note webhooks don't carry.

#### Watch condition (in lieu of Channel 2)

If a future change adds note-activity metadata to `SNAPSHOT_FIELDS` **and** FUB is found to echo it on note creation, the person-side channel must be wired then. This is recorded as: a code comment at `PersonUpsertService.SNAPSHOT_FIELDS`, known-issue #27, and a Phase 4 exit criterion. No disabled/failing test stands in for it — a unit test cannot detect FUB changing its webhook semantics.

#### The early-echo race (documented, not fixed)

If FUB's `notesCreated` echo arrives **before** `followUpBossClient.createNote` returns to our coordinator (i.e., the FUB POST response is slower than the FUB webhook fire), the tracker record doesn't exist when `NoteEmissionService` processes the echo. The annotation is missed; the event emits without `source=ENGINE`. Phase 4 workflows would treat this as a real `note.created`.

Empirically rare (POST responses are typically faster than webhook fires) but possible. Documented in `known-issues.md`. A content-hash key (record on tracker before the POST, keyed by hash of note body + personId; match on echo by computing same hash) would close this but adds complexity. Deferred until observation shows it firing.

#### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/flux/service/workflow/steps/FubCreateNoteWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Replace direct `fubCallHelper.executeWithRetry(() -> followUpBossClient.createNote(command))` with `coordinator.applyEntityCreateTrackedOnly("note", sourcePersonId, runId, () -> fubCallHelper.executeWithRetry(...), (tracker, note, ctx) -> tracker.record("note", String.valueOf(note.id()), Set.of("created"), ctx.runId()))`. **Single channel** — the recorder records only the note entry (guarded against a null note). |
| 2 | `src/main/java/com/flux/service/note/NoteEmissionService.java` *(modify)* | Inject `EngineWriteTracker` + `ObjectMapper`. Before emitting each `note.<action>` event, consult tracker with `entityType="note"`, `entityId=<noteId>`, `changedFields=Set.of("created"/"updated"/"deleted")` (match the action). On hit, deep-copy the payload and add `"source": "ENGINE"`; on miss, pass the original reference through unchanged. (Engine only ever records "created" today, so only `note.created` can hit — the updated/deleted handling is symmetrical and inert.) |
| 3 | `src/main/java/com/flux/service/event/DomainEventEmitter.java` *(no change)* | Untouched. There is no person-side channel; note annotation is done in `NoteEmissionService` (#2) because the emitter's universal hook keys off `payload.changed_fields`, which note webhooks don't carry. |
| 4 | `src/test/java/com/flux/service/note/NoteEmissionServiceTest.java` *(extend)* | Inject a tracker mock (defaults to miss). New: tracker hit → `note.created` payload annotated ENGINE; miss → unannotated + original reference preserved; multi-resourceId webhook → only the tracked note annotated (deep-copy must not leak `source` onto the others). |
| 5 | `src/test/java/com/flux/race/scenarios/CreateNoteScenariosTest.java` | <br>**D1** happy path — engine creates note; `notesCreated` echo arrives. Expect: 1 `note.created` event **annotated `source=ENGINE`**. <br>**D3** early-echo race — fake FUB delays `createNote` 300ms; echo fired at t=50ms (before the POST returns, so before the tracker record exists). Expect: 1 `note.created` event **NOT annotated**. Diagnostic for the early-echo race. <br>**D4** multiple notes — engine creates 3 notes; 3 echoes. Expect: 3 `note.created` events, all annotated ENGINE. <br>*(D2 dropped — no person-side echo exists; see 2026-06-01 changelog.)* |

#### Order within the commit

1 → 2 → 4 → 5.

#### Test gate

`./mvnw clean test` green (682 tests). D3 is the diagnostic test for the early-echo race; if it ever passes annotated (annotation present despite the race), the race-window assumption changed and the test needs updating.

#### What 3e changes for users

`note.created` events from engine writes carry `source=ENGINE` annotation (except in the rare early-echo race). Note creation produces **no** person-side event, so there is nothing to annotate there. Phase 4 workflow filters work uniformly across all engine-write step types.

#### Phase 3 complete

After 3e, every engine-originated FUB write either:
- Updates local first and emits no echo event (scalar mode happy path), **OR**
- Lets the echo flow through `PersonUpsertService`/`NoteEmissionService` annotated `source=ENGINE` (all other modes / concurrent cases)

Phase 4 can subscribe to events and filter on `change.source != "ENGINE"` to exclude engine echoes. The bad-run-rate win arrives in Phase 4.

---

### Defaults (locked at plan-finalize time — override on PR review only with explicit justification)

| Decision | Default | Reason |
|---|---|---|
| Revert semantics | **No revert.** Transient FUB failures retry via `RetryPolicy.DEFAULT_FUB`; permanent failures leave local ahead of FUB until the next webhook re-syncs. | Removes a whole code path. Plan §5's "restore prior snapshot" is reversed in the 2026-05-29 changelog. Acceptable cost: misleading echo on next webhook after permanent failure (known issue). |
| Inner-tx propagation in coordinator | `PROPAGATION_REQUIRES_NEW` via `TransactionTemplate` | The outer `@Transactional` on `WorkflowStepExecutionService.executeClaimedStep` would otherwise pin the row lock across the FUB HTTP call. Mirrors `PersonUpsertService` DIVE-recovery discipline. |
| `EngineWriteTracker` impl | `InMemoryEngineWriteTracker` (ConcurrentHashMap + scheduled eviction) | Phase 3 has no event consumers; crash-window phantom events are dev-acceptable. Redis-backed impl is a **Phase 4 prerequisite**. |
| Tracker TTL | 30s (configurable `engine.write.tracker.ttl-seconds`) | Matches FUB echo arrival window (300–800ms) with safe headroom. Tune based on hit/miss metrics in Phase 4. |
| Tracker eviction interval | 10s (configurable `engine.write.tracker.eviction-interval-ms`) | Bounded memory under burst; no observable lag for echo annotation. |
| Tracker match semantics | `engineRecord.changedFields ⊆ diffFields` → hit | Annotates A4-style concurrent-external scenarios as ENGINE. Trade-off documented (annotation-over-detection bias). Strict equality would miss these and let phantoms through. |
| `source` annotation location | `event.payload.source` (JSON field) — not a new field on the `DomainEvent` record | Avoids churning every listener signature; Phase 4 consumers read from payload anyway. |
| Tag operation mode | `ENTITY_APPEND_TRACKED_ONLY` — **no local-state-first** | Eliminates phantom-removal class structurally; cost is one annotated event per engine tag-add. |
| Note operation mode | `ENTITY_CREATE_TRACKED_ONLY` two-channel — `note.created` + person-side `peopleUpdated` | Both echoes are real and need annotation. Early-echo race documented. |
| Race harness location | `src/test/java/com/flux/race/` | Sibling to `replay/` and `integration/` packages. Testcontainers Postgres + `FakeFollowUpBossClient`. |
| Race harness scenario distribution | A in 3b, B subset in 3c, C in 3d, D in 3e | Each sub-phase ships its own proof. No "all scenarios at the end" deliverable. |

### Out of scope (deferred to other phases)

| Item | Why deferred | Phase that owns it |
|---|---|---|
| `RedisEngineWriteTracker` (or persisted equivalent) | No event consumers in Phase 3; crash-window cost is observable only when Phase 4 subscribers exist | Phase 4 prerequisite |
| Content-hash tracker keying for the early-echo race on note creation | Empirically rare; cost of fixing is high; revisit if observed | Post-Phase-4 follow-up |
| `FubMutatingStep` SPI for type-enforced pattern | With 4 steps using 3 mechanisms, SPI doesn't fit; reconsider when a 5th step joins | Speculative future |
| Tracker hit/miss metrics surfaced via admin UI | INFO-level structured logs are sufficient for Phase 3; admin UI surface is a separate feature | Admin UI roadmap |
| Smarter revert ("undo delta") for accumulating fields | Research project; not justified until production data shows pain from drift-on-permanent-failure | Speculative future |
| `change.source` annotation on `call.created` events | Engine doesn't write calls today; if a future `fub_create_call` step is added, this lands with it | Not currently planned |
| Workflow validator refuses `change.source` reference for non-`person.state_changed` triggers | Phase 4 territory; lives with the validator updates there | Phase 4 |

### Risks and mid-flight detection

| Risk | Detection signal during build |
|---|---|
| Smoking-gun lock-across-FUB-call defect | Race harness A1 + A3 with `fakeFub.delayMs(500)` and N=5 concurrent webhooks → test exceeds 2s timeout if lock is held |
| Tracker TTL too short → echo arrives after eviction → unannotated phantom | INFO log `engine.write.tracker.miss` ratio > expected baseline (≥10% miss on echo-window scenarios) |
| Match-semantics false positives (engine record matches an unrelated diff) | Race harness scenarios with non-overlapping engine + external field sets; assert no false annotation |
| Early-echo race on note creation | D3 scenario passes — annotation present despite race timing |
| `REQUIRES_NEW` not actually applied (caller's outer tx swallows the inner) | `DefaultEngineWriteCoordinatorTest` REQUIRES_NEW test: roll back outer, confirm tracker + local change persist |
| Memory leak in tracker under sustained load | Periodic log: tracker entry count + TTL distribution; alert if entry count grows unbounded between eviction runs |

### Cross-references

- High-level deliverables: [`README.md`](./README.md) §"Phase 3"
- Architectural rationale: [`plan.md`](./plan.md) §5 (note the 2026-05-29 revert reversal)
- Race matrix driving these decisions: [`plan.md`](./plan.md)
- Phase 2 lock-discipline prior art: [`PersonUpsertService.upsertFubPerson`](../../../src/main/java/com/flux/service/person/PersonUpsertService.java) + [`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/flux/integration/PersonUpsertConcurrencyStressTest.java) — the REQUIRES_NEW pattern this plan reuses
- Known issues tracker: [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) — Phase 3 entries added under #26, #27, #28
- Implementation log to be written at end: `implementation-log.md` (does not exist yet; create when 3e ships)

---

## Phase 4 — Commit-level plan

> **Status:** DONE — shipped as 4a–4d.2. Decomposes the Phase 4 section of [`README.md`](./README.md) into reviewable sub-phases; pairs with the decision narrative in [`implementation-log.md`](./implementation-log.md). Built on the fresh-eyes verification of 2026-06-02 (see the `README.md` changelog) and a local-DB workflow check the same day.
>
> **Intent (unchanged):** workflows stop subscribing to raw webhooks and subscribe to **domain events**; the one production workflow (`agent_followup_enforcement`) is re-authored against the new `{on, filter}` shape; the bad-run-rate win (~50% → <5%) lands here. Phases 0–3 are dormant plumbing until this phase consumes them.

---

### Ground truth at plan time (verified against code + local DB, 2026-06-02)

- **Latest migration is `V22`.** Phase 4's additive migration is **`V23`**.
- **Events are already flowing.** `DomainEventEmitter.emit()` is called ungated from `PersonUpsertService` / `CallUpsertService` / `NoteEmissionService`; rows land in `events` and after-commit dispatch fires on every webhook. `InMemoryDomainEventDispatcher`'s listener list is **empty** — so registering the first `DomainEventListener` is what starts consumption, **not** the `engine.write.emit-events` flag.
- **`engine.write.emit-events`** (`application.properties:79`, default `false`) gates only `DefaultEngineWriteCoordinator:180` — the engine's emission of *its own* `person.state_changed` event. Annotation (`source=ENGINE`) is always on irrespective of the flag. It is a **platform config toggle**, target ON.
- **Router:** `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` has a single caller — `WebhookEventProcessorService:114` — and only routes `findByStatus(ACTIVE)`. The new path is a second method registered as a listener.
- **Trigger types:** `FubWebhookTriggerType` (id `webhook_fub`, retiring). `DomainEventTriggerType` is new.
- **Scope today:** `FubWebhookTriggerType.matches()` puts only `{event: {payload}}` into scope. `person.*` is NOT in trigger scope (closes #17). Run-time scope is built in `WorkflowStepExecutionService.buildRunContext`.
- **Validator:** `WorkflowGraphValidator.validatePersonFieldReferences` checks `person.*` against `capturedFieldNames()` (`SNAPSHOT_FIELDS` + `kind`). Diffable-field source of truth: `PersonDiffComputer`.
- **Local-DB finding:** 18 workflow rows, **all `webhook_fub`**, **none `ACTIVE`** (DRAFT/INACTIVE/ARCHIVED; several duplicate dev keys). Runtime hard-cut risk is therefore zero today (router only touches ACTIVE). The stale rows need a stance at 4e (see its Risk note) — they are dev noise, not production workflows to migrate.

---

### Cutover sequencing (the spine of the phase)

Because events already flow, the listener and the flag must flip in order, never bundled:

1. **4d** — register listener + migrate `agent_followup_enforcement`, `emit-events` **OFF**. Engine writes stay invisible (pure echo-suppression, proven in Phase 3). Replay-verify old workflow behaves.
2. **4e** — flip `emit-events` **ON**. Engine's own `person.state_changed` events become visible (annotated `source=ENGINE` → `event.origin='ENGINE'`), but the **platform engine-echo gate excludes them from every workflow by default** (two-level gate — see `plan.md` "Engine-echo exclusion"), so no per-workflow predicate is needed. Replay-verify bad-run-rate.
3. **4e** — hard-cut the old webhook-shaped path.

---

### Sub-phases

#### 4a — Substrate (dormant, additive)

**Deliverables**
- `V23` migration: `workflow_runs.domain_event_id BIGINT NULL` (FK → `events.id`, `ON DELETE SET NULL`). *(The originally-planned `suppressed_by_run_id` was dropped 2026-06-03 — Phase 5 is cancel-only and attributes the cancel to an event via `reason_code` + `domain_event_id`, not a run-to-run link. See `README.md` changelog.)*
- Entity/field wiring on the run entity for `domain_event_id`. No population yet.
- Skeleton `DomainEventScopeBuilder` (or equivalent seam) — signatures only, no live caller.

**Verification:** migration applies cleanly to dev DB; full suite green; no behavior change.
**Exit:** columns exist, all `NULL`; nothing references them at runtime.
**Risk:** none — purely additive.

#### 4b — `DomainEventTriggerType` + trigger-time scope (additive, isolated, not wired)

> **Decisions locked 2026-06-03 (revised — "no Rail 1"):** app is dev-phase, so Rail 1 is **deleted, not coexisted** → no dual-mode anywhere. **4b is purely additive** — builds the new trigger front-end alongside the old, wired to nothing, deleting nothing. `buildRunContext`/`ExpressionScope` are **not touched in 4b**; the step-time scope is rewritten single-mode in **4d**, together with deleting Rail 1. Trigger type is clean/standalone (not implementing `WorkflowTriggerType`); `person.*` required → **closes #17**.

**Deliverables (all new — only `DomainEvent` is modified)**
- **`DomainEvent` += `Long id`** — `DomainEventEmitter` passes `saved.getId()` (already in hand). Gives `event.id` and lets 4d populate `workflow_runs.domain_event_id`. Sole construction site is the emitter.
- **`DomainEventScopeBuilder`** (`service/workflow/expression/`) — lean mapper, the single source of the domain-event scope shape. `build(DomainEvent, person) → Map`: `event{id,kind,entityType,entityId,payload}`; `change{<field>:{changed,old,new}, source}` (state_changed only, from `changed_fields/previous/current`; missing field → absent → falsy); `current` (= `payload.current`; created + state_changed); `person` (the snapshot). **No `webhook.*`** (step-time, 4d).
- **`DomainEventTriggerType`** (`service/workflow/trigger/`) — clean, **standalone** (does NOT implement `WorkflowTriggerType`). Deps: `PersonSnapshotResolver`, `DomainEventScopeBuilder`, `ExpressionEvaluator`. `matches(DomainEvent, config)`: `eventKind == config.on` → resolve person (via `entityId` when `entityType="person"`) → build scope → evaluate `config.filter` (no filter → match on kind). `extractEntity(DomainEvent)` → `EntityRef`.
- **Not wired** as a listener; nothing creates domain-event runs yet.

**Explicitly NOT in 4b → deferred to 4d:** `buildRunContext`/`ExpressionScope` step-time rewrite (single-mode), `webhook.*`, listener wiring / `route(DomainEvent)`, **Rail 1 deletion** (`FubWebhookTriggerType`, `TriggerMatchContext`, `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `route(NormalizedWebhookEvent)` + its `process()` call). Validator → 4c.

**Verification:** unit tests only — scope builder per-kind + `change`/`current`/`event.origin`; trigger filter matrix — engine echo filtered, real assignment passes, kind-mismatch rejected, watched-field-unchanged rejected. (The test filter includes `event.origin != 'ENGINE'` to exercise the `event.origin` scope key — as an *opt-in* workflow would; the **default** production filter omits it, since engine-echo exclusion is platform-enforced.) **`event.origin` is always present (`ENGINE`/`EXTERNAL`)** so the `!= 'ENGINE'` predicate is never undefined in JSONata. No runtime path.
**Exit:** new trigger + scope resolve correctly in isolation; full suite green (4b touched no live path; Rail 1 untouched). **✅ DONE — 12 tests, full suite 696 green.**

#### 4c — Validator generalization

**Deliverables**
- Per-event-kind **field-schema registry**: each event kind declares the fields a filter may reference. Person kinds populate from `PersonDiffComputer`'s diffable list (keyed off the computer, not a duplicated constant — drift-proof). `call`/`note` get the seam but are marked **unvalidated-payload** (documented gap, no consumer).
- Save-time **refusals**: `change.<field>`/`current.<field>` not in the person set (clear "field not captured — would never fire" message); old `webhook_fub`/`peopleUpdated`-typed trigger with a migration hint.
- **`event.*` (incl. `event.origin`) is always-valid metadata** — no special-casing. (`change.source` is no longer special either: `source` is a captured person field, so `change.source` validates like any field delta.)
- **Cross-checks**: `change.*` ⇒ `on = person.state_changed`; `current.*` ⇒ `on ∈ {person.created, person.state_changed}`.
- **Accept the optional `reactToEngineEvents` boolean** in the trigger config (validate type only). **No engine-echo warning** — exclusion is platform-enforced and safe-by-default (the two-level gate; `plan.md` "Engine-echo exclusion"), so there is no author predicate to forget. The whole warnings-channel question is moot.

**Verification:** validator unit tests — uncaptured-field refusal, old-shape refusal, `event.origin` recognized, cross-check failures, `reactToEngineEvents` accepted.
**Exit:** save-time validation closes the silent-no-fire gap for person triggers; append kinds explicitly deferred.
**Risk:** low — save-time only, no runtime path.

#### 4d — The switch

> **Plan locked 2026-06-03.** Dev-phase → no Rail 1 coexistence, so the whole cutover is one coordinated step (the old "4e" is folded in — there is no separate hard-cut phase). Split into two commits so the risky part is isolated. Decisions: **(1)** freeze the domain-event payload + proximate webhook payload into the run at plan time (step-time is a pure mapper; only `person` re-resolved live); **(2)** repurpose `/trigger-types` to a domain-event-kinds + `{on,filter}` schema catalog; **(3)** flip `engine.write.emit-events` **ON** at the end (consumption gate keeps engine events harmless); **(4)** global-capability flag `engine.events.workflow-consumption.enabled` (default `false`).

##### 4d.1 — Additive prep (behavior unchanged, reviewable)
- `WorkflowPlanRequest` += `domainEventId`; `WorkflowExecutionManager.plan` sets `run.domainEventId` (null for current callers → no change).
- **`EngineEchoGate`** — config flag `engine.events.workflow-consumption.enabled` (default false) + `shouldExclude(DomainEvent, triggerConfig)` = `event.origin == "ENGINE" AND NOT (globalCapability AND reactToEngineEvents)`. Built + unit-tested, **not yet wired**.
- *Safe: nothing reads the new column / gate until 4d.2.* (The step-time scope rewrite incl. `webhook.*` is coupled to `buildRunContext` → done in 4d.2.)

##### 4d.2 — The atomic switch (one coherent commit)
1. `WorkflowTriggerRouter implements DomainEventListener` → `onEvent` → `route(DomainEvent)`: select ACTIVE `{on}`-matching workflows, **apply `EngineEchoGate`** (skip + log engine echoes), evaluate filter, `plan(...)` with `domainEventId` + proximate `webhookEventId`.
2. **Single-mode `buildRunContext`/`ExpressionScope`** — domain-event bag via `DomainEventScopeBuilder` (`event.*`, `change.*`, `current.*`, `event.origin`, `person.*` live, `webhook.*` from frozen payload, `now`/`steps`). Run freezes the domain-event payload + proximate webhook payload at plan time (decision 1).
3. **Re-author** `Docs/features/agent-followup-enforcement/workflow.json` → `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' AND change.assignedUserId.changed" }`; migrate step `event.payload.*` → `webhook.payload.*`.
4. **Delete Rail 1:** `FubWebhookTriggerType`, `TriggerMatchContext`, `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `route(NormalizedWebhookEvent)` + the `WebhookEventProcessorService.process()` call. Update `AutomationWorkflowService` (drop the webhook validation branch + `WorkflowTriggerRegistry` dep — all triggers are `{on,filter}`) and `AdminWorkflowController` (`/trigger-types` → domain-event-kinds catalog, decision 2).
5. Flip `engine.write.emit-events` **ON** (decision 3).
- **Biggest cost: test rewrite** — every run-execution / trigger test that created runs via the webhook path or asserted on the old router/trigger gets rewritten to the domain-event path. The 709-test suite is the guard.

**Verification:** replay 20123 / 20235 / 20207 through the listener → bad runs ≈ 0; engine echoes excluded by the gate (with `emit-events` ON); `agent_followup_enforcement` runs end-to-end on `{on,filter}`; `domain_event_id` populated; Rail 1 gone; full suite green.
**Exit:** **Phase 4 complete** — workflows run on domain events; the bad-run-rate win is live.

**Verification:** replay bad-run-rate <5% on recorded field-obs traffic; the re-authored workflow's expressions all resolve; old-shape triggers rejected at save.
**Exit:** `agent_followup_enforcement` runs entirely on the new pipeline; old path deleted; crash-window decision recorded (accept gap + observability log); note channel deferred (#27).
**Risk — stale workflow rows.** All 18 local rows reference `webhook_fub`; deleting that type leaves them unresolvable. None are ACTIVE so runtime is unaffected (router skips unknown types / only routes ACTIVE), but **decide before the cut**: leave them (re-save/re-activate then fails loudly — the intended migration-forcing behavior) or purge the dev junk. Production reality must be re-confirmed against the live workflow table at cut time — the local DB is dev noise and is not authoritative for "exactly one active workflow."

---

### Risks (phase-level)

| Risk | Detection signal |
|---|---|
| Scope refactor breaks a live step expression | 4b unit matrix + 4d replay; validator runs at save |
| Listener consumes events the old path also acts on (double-run during 4d coexistence) | 4d replay compares both paths; the hard cut (4e) removes the overlap |
| Flag flipped before listener wired (wrong order) → engine events with no consumer, or consumer with un-annotated echoes | Sequencing note above; flag is a deliberate 4e step, not bundled into 4d |
| A second production workflow exists on the old shape | Re-confirm the live workflow table at 4e before the cut |
| Dispatch crash-window with a real consumer | Observability log (4e exit); durable poller is a separate follow-up |

### Non-goals (this phase)

- Run-collision handling — Phase 5 (now cancel-only; reuses the existing `WorkflowRunControlService` cancel, no new column).
- Durable-outbox poller — separate tracked follow-up.
- Note-trigger support / `note` validator schema — deferred until `notesCreated` is ingested (#27).
- Append-event (`call`) filter field validation — deferred until a `call`-triggered workflow exists.
- Removing the `engine.write.emit-events` toggle — it is a platform config, kept.

### Repo decisions impact

Likely `Yes` — the `{on, filter}` trigger schema is part of the workflow JSON contract; document it in `Docs/repo-decisions/` at 4e. The `engine.write.emit-events` platform toggle is a secondary candidate once the config page exists.

---

## Phase 3 — Race Matrix and Plan-Revision Findings

> **Status:** Research. Drives a forthcoming `plan.md` and a revision to [`README.md`](./README.md) §"Phase 3". Not yet approved as a plan.
>
> **Purpose:** Stress-test the thesis that *"local-state-first writes + `findBy…ForUpdate` pessimistic lock = echo suppression"* against every meaningful combination of (entity, operation, timing, concurrency). Be brutally honest about where the thesis holds, where it breaks, and what infrastructure each failure mode actually needs.
>
> **Conclusion in one line:** The thesis holds for ~50% of real scenarios (single scalar engine write, no concurrent activity, well-behaved echo). The other ~50% need the tracker, accept silent data loss, or — for `fub_create_note` — have no mechanism in the current plan at all.

---

### Reading order

1. [Smoking gun](#smoking-gun) — one structural defect that, if unaddressed, will pin the row lock across every FUB HTTP call. This is the highest-priority finding.
2. [The four step types as they exist today](#the-four-step-types-as-they-exist-today) — none touch local state. Phase 3 adds net-new behaviour, not a modification.
3. [The matrix](#the-matrix) — A1–A7, B (= A), C1–C3, D1–D4. Brutal cell-by-cell evaluation.
4. [Scalability and dependability](#scalability-and-dependability) — lock contention, tracker memory, durability, pattern enforcement, hidden coupling.
5. [Proposed Phase 3 plan revisions](#proposed-phase-3-plan-revisions) — concrete changes to [`README.md`](./README.md) deliverables.
6. [A separate race harness](#a-separate-race-harness) — why the existing harnesses can't cover this and what the new one looks like.

---

### Smoking gun

[`WorkflowStepExecutionService.executeClaimedStep`](../../../src/main/java/com/flux/service/workflow/WorkflowStepExecutionService.java) is `@Transactional` at line 68. The step's `execute(context)` — which performs the FUB HTTP call inside each `Fub*WorkflowStep` — runs **inside that outer transaction** at line 121.

**Implication for Phase 3's wrap pattern:** if the wrap takes `findBy…ForUpdate` and updates local state from inside `execute()`, the pessimistic row lock is held across the FUB HTTP call (200–800 ms per call) **by default**, because the outer transaction stays open for the entire step. Every concurrent webhook for the same person blocks waiting on that lock for the full FUB round-trip. Under FUB-burst load this pins the 2–4 thread webhook async pool.

This inverts the win of Phase 2's lock discipline (which only held the lock for the duration of an in-memory diff + save — milliseconds).

**Fix is not optional and must be designed up front:** the wrap's local update and tracker record MUST run in a `REQUIRES_NEW` inner transaction (or fully outside the outer `@Transactional` on `executeClaimedStep`), commit, release the lock, *then* call FUB. On failure, a second `REQUIRES_NEW` tx performs the revert.

Anyone implementing Phase 3 without internalising this will produce a working unit test (mocked FUB call returns immediately) and broken production behaviour (real FUB call holds the lock).

---

### The four step types as they exist today

`grep -E "personRepository|PersonEntity|personUpsertService|setPersonDetails|getPersonDetails"` against each of `FubReassignWorkflowStep`, `FubMoveToPondWorkflowStep`, `FubCreateNoteWorkflowStep`, `FubAddTagWorkflowStep` returns **zero matches**. Today these steps call FUB and return. They never read or write local Person state.

This is structurally good news. There is no entrenched pattern to fight; Phase 3 designs the local-write shape from scratch. The bad news is that the four steps' surface area today is misleadingly simple — `FubReassignWorkflowStep` is 132 lines. The wrap pattern roughly doubles each step's body, and the multi-tx discipline is non-trivial.

**FUB call shapes per step (from [`FollowUpBossClient`](../../../src/main/java/com/flux/service/FollowUpBossClient.java)):**

| Step | Method | FUB echoes |
|---|---|---|
| `fub_reassign` | `reassignPerson(personId, targetUserId)` — PUT person | `peopleUpdated` (`assignedUserId`, `assignedTo` changed) |
| `fub_move_to_pond` | `movePersonToPond(personId, targetPondId)` — PUT person | `peopleUpdated` (`assignedPondId` changed) |
| `fub_add_tag` | `addTag(personId, tagName)` — PUT person | `peopleUpdated` (`tags` accumulating change) |
| `fub_create_note` | `createNote(command)` — POST note | `notesCreated` (the new note) **plus probable** `peopleUpdated` (person's `lastNoteAt` or similar metadata) |

The first three echo as `peopleUpdated`. **`fub_create_note` echoes in a different domain entirely (`notesCreated`)**, and likely produces a second `peopleUpdated` echo for the person's note-metadata change. This is the key reason why [Section D](#d-fub_create_note--entity-creation--no-local-notes-table) of the matrix concludes the plan is structurally incomplete for note creation.

---

### The matrix

#### Legend

- **E** = engine write (workflow step)
- **W** = webhook handler (`PersonUpsertService.upsertFubPerson`)
- **L** = pessimistic row lock on `persons` (`findBy…ForUpdate`)
- **t<sub>x</sub>** = milliseconds since E started
- **✓** = lock + local-state-first thesis holds without further help
- **✗** = thesis breaks; needs tracker, accepts data loss, or has no mechanism

#### A. `fub_reassign` (scalar — `assignedUserId`)

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **A1** | Happy path. Single engine write; echo arrives later. | E commits Alice (t<sub>0</sub>). FUB PUT lands (t<sub>200</sub>). Echo arrives (t<sub>500</sub>). W takes L, reads local Alice, payload Alice, diff empty. No event. | ✓ |
| **A2** | Echo arrives during E's inner-tx commit. | W takes L, blocks until E commits and releases. Then reads new local, diff empty. No event. | ✓ (lock closes the race) |
| **A3** | FUB-burst echo (3–4 webhooks for one logical change). | All 3–4 serialize on L; each reads identical local; all diffs empty. No events. | ✓ |
| **A4** | **Real concurrent external change.** Another user reassigns the same person to Carol via FUB UI at t<sub>2</sub>. | E commits Alice (t<sub>0</sub>), releases L. External-W for Carol arrives (t<sub>30</sub>), takes L, reads local Alice, payload Carol → emits real event Alice→Carol, writes local Carol. E's FUB PUT for Alice lands at FUB (t<sub>200</sub>), **clobbering Carol at FUB**. Echo for Alice arrives (t<sub>500</sub>), takes L, reads local Carol, payload Alice → **emits phantom event Carol→Alice**. | ✗ — phantom event. Only the tracker (annotating the final echo as `source=ENGINE`) can suppress it. |
| **A5** | FUB write fails (5xx) → revert. No concurrent activity. | E captures Bob, writes Alice locally, calls FUB, FUB returns 500. E re-takes L, restores Bob, commits. Local now Bob. Net diff vs. pre-engine: none. No event. | ✓ |
| **A6** | **FUB write fails → revert, with concurrent legitimate change.** Carol-webhook arrives between local-commit and FUB-failure. | E commits Alice (t<sub>0</sub>). External-W for Carol (t<sub>20</sub>) → local Carol. FUB returns 500 (t<sub>50</sub>). E reverts to **Bob** (captured prior) → **destroys Carol locally**. Will self-heal *only when FUB sends another `peopleUpdated` for this person* — could be hours or days. | ✗ — silent data loss. Plan §5 chose "restore prior snapshot, not undo delta"; this is the cost. Acceptable for dev phase; needs a documented production decision before scaling. |
| **A7** | **Two engine writes back-to-back.** Workflow A writes Alice, Workflow B writes Carol, both claim work on the workflow pool within milliseconds. | A takes L, captures Bob, writes Alice, releases. B takes L, captures **Alice** (as prior), writes Carol, releases. A's FUB PUT and B's FUB PUT race. If **A's PUT lands second** (network reorder), FUB ends up Alice; echo Alice → local Carol → diff → **emits phantom Carol→Alice**. | ✗ under PUT reorder. The tracker (with entries for both writes) is the only mechanism — the final echo would match A's tracker record → annotate `source=ENGINE` → workflow filter suppresses. **Local-state-first does not help here; the tracker is the load-bearing piece.** |

**Verdict for `fub_reassign`:** 4 of 7 cells hold on local-state-first + lock alone. 3 cells (A4, A6, A7) need the tracker, accept silent data loss, or both.

#### B. `fub_move_to_pond` (scalar — `assignedPondId`)

Structurally identical to `fub_reassign`. Same scalar field semantics, same FUB echo shape, same matrix outcomes. No separate walk needed. **If A1–A7 are addressed for `fub_reassign`, B is covered.**

#### C. `fub_add_tag` (accumulating — `tags`)

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **C1** | Happy path. E adds `NEW` to `tags=[A,B]` → local `[A,B,NEW]` → FUB → echo `[A,B,NEW]`. | W reads local `[A,B,NEW]`, payload `[A,B,NEW]`, diff empty. No event. | ✓ |
| **C2** | **Concurrent external tag add lands BEFORE engine's FUB PUT.** External adds `X` at FUB (t<sub>5</sub>); FUB sends webhook with `tags=[A,B,X]` (no `NEW` — FUB hasn't processed our PUT yet). | E commits local `[A,B,NEW]` (t<sub>0</sub>). External-W arrives (t<sub>20</sub>), takes L, reads local `[A,B,NEW]`, payload `[A,B,X]` → diff says **"NEW removed, X added"** → **emits phantom event claiming NEW was removed externally.** `NEW` was never on FUB; the "removal" is a lie about FUB state, indistinguishable from a real removal. Local becomes `[A,B,X]`. E's PUT lands at FUB (t<sub>200</sub>) → FUB now `[A,B,X,NEW]`. Echo (t<sub>500</sub>) → local `[A,B,X]`, payload `[A,B,X,NEW]` → diff `NEW added` → **second phantom event "NEW added by engine"**. | ✗✗ — two phantom events, one of them a fabricated "removal." **This is a fundamental defect of local-state-first for accumulating fields**: we make local diverge from FUB in a way the diff machinery cannot distinguish from a real external removal. |
| **C3** | FUB add_tag fails → revert. Concurrent external tag `Y` was added between our commit and FUB's failure. | E captures `[A,B]`, writes `[A,B,NEW]` (t<sub>0</sub>). External-W for `Y` → local `[A,B,NEW,Y]`. FUB returns 500 (t<sub>50</sub>). E reverts to **`[A,B]`** (captured prior) → **destroys `Y` locally**. Emits phantom event "`Y` removed, `NEW` removed." | ✗ — destroys legitimate concurrent tag. Same trade-off as A6 but worse because accumulating fields aggregate multiple concurrent writers. |

**Verdict for `fub_add_tag`:** thesis holds for the happy path only. Any concurrent external `tags` write produces phantom "removal" events because local treats our optimistic state as ground truth. **Local-state-first is structurally wrong for accumulating fields.** Three viable options (deferred for decision — see [revisions](#proposed-phase-3-plan-revisions)):

- (a) Accept C2 and C3. Trust that workflows filter on `change.source` for engine writes; tolerate phantom external-removal events.
- (b) **Do not local-state-first for `tags`.** Call FUB, wait for echo, let `PersonUpsertService` apply truth-from-FUB. Annotate via tracker. Loses the "echo produces no event" win for tags but eliminates the phantom-removal class entirely.
- (c) Defer `fub_add_tag` wrapping to a later phase, after a real workflow exposes the trade-off.

#### D. `fub_create_note` (entity creation — no local notes table)

> **Correction (2026-06-01, post-implementation):** The **D2** row below — and every reference in this doc to a person-side `peopleUpdated` / `lastNoteAt` echo from note creation — rests on an assumption that turned out to be **false**. Creating a note does **not** fire a `peopleUpdated` webhook (confirmed empirically, by FUB's API docs, and by the fact that `lastNoteAt` isn't in `SNAPSHOT_FIELDS`/`PersonDiffComputer`). There is no person-side echo and no phantom on the person side. 3e shipped **single-channel** (`note.created` only); **D2 was dropped**. D1/D3/D4 stand. The "second mechanism for the person-side echo" called for in the verdict below is unnecessary. See [`plan.md`](./plan.md) §3e 2026-06-01 changelog and known-issue #27. The original analysis is left intact below as a historical record.

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **D1** | Happy path. Engine POSTs to FUB → FUB returns note id 100 → FUB sends `notesCreated` webhook with id 100. | `WebhookEventProcessorService` dispatches → `NoteEmissionService.emit` → emits `note.created` event for id 100. **No local notes state exists** — local-state-first cannot apply. | ✗ — thesis fundamentally doesn't apply |
| **D2** | FUB also sends `peopleUpdated` for the note's side-effect on the person (`lastNoteAt`, last activity timestamp). | The person echo produces a real `person.state_changed` event because the engine never wrote `lastNoteAt` locally, so the diff is non-empty. | ✗ — extra phantom on the person side |
| **D3** | Engine creates note, FUB returns success, but no `notesCreated` webhook arrives (FUB delivery failure). | No event ever emitted. Downstream workflows waiting on `note.created` wait forever. | Out of Phase 3 scope (FUB delivery reliability), but worth recording. |
| **D4** | Engine creates 3 notes in quick succession (e.g., for 3 mentioned users). | 3 `note.created` events emit. No suppression. A workflow filtering on `note.created` fires 3 times for our own writes. | ✗ — tracker (annotation) is the only mechanism |

**Verdict for `fub_create_note`:** **the plan is structurally incomplete here.** `README.md` lumps `fub_create_note` with the other three steps, but local-state-first doesn't apply (no local state), and FUB sends `notesCreated` (different domain) plus probable `peopleUpdated` (separate echo on the person). This needs:

- A tracker mechanism for `note.created` that keys on `(personId, noteContentHash?)` since the engine doesn't know FUB's note id until after the POST returns. Alternatively, the engine records on the tracker keyed by FUB's returned `noteId` immediately after the POST succeeds — but the tracker is only useful from then on; if the echo arrives before the POST returns (rare but possible), we miss the annotation window.
- A separate mechanism for the person-side echo (`lastNoteAt` change) — either tracker for that field too, or accept the phantom.

**This is the single most important plan-revision finding in the matrix.**

---

### Where the thesis holds vs. breaks — honest summary

| Cells where thesis holds | Why |
|---|---|
| A1, A2, A3, A5 | Single scalar engine write, no concurrent activity, well-behaved echo. Lock closes the during-commit race; local-state-first makes echo's diff empty. |
| B (all) | Same shape as A. |
| C1 | Happy path for accumulating field — works because no concurrent change. |

| Cells where thesis breaks | Mechanism needed |
|---|---|
| A4, A7 | Tracker annotation; workflows filter on `change.source` |
| A6 | Plan §5 accepts silent data loss; "self-heals on next webhook" is contingent on FUB sending future webhooks |
| C2 | **No clean fix.** Local genuinely thinks `tags=[A,B,NEW]` before FUB knows; any external webhook in between diffs against optimistic local and produces phantom removal |
| C3 | Plan §5 trade-off, worse for accumulating fields |
| D1, D2, D4 | Tracker-only; plan as written doesn't describe this |
| D3 | Out of scope (FUB delivery), but worth a known-issue entry |

**The thesis "lock + local-state-first does echo suppression on its own" is correct for ~50% of real scenarios across the 4 wrapped step types.** The other ~50% need the tracker, accept silent data loss, or have no mechanism in the current plan.

This is not an argument against Phase 3. It is an argument that the plan in `README.md` understates the role of the tracker and overstates the role of local-state-first.

---

### Scalability and dependability

#### Lock contention on the webhook async pool

- [`WebhookAsyncConfig`](../../../src/main/java/com/flux/config/WebhookAsyncConfig.java) configures 2–4 threads.
- Each webhook handler takes `findBy…ForUpdate` for the duration of the diff + save (single-digit to low-tens of ms in Phase 2 measurements).
- **N=10 burst for one person → serialized at ~10–50 ms per lock-hold → 100–500 ms for the burst to drain.** Other persons unaffected (different rows, different locks). Pool not globally blocked.
- **OK at current volume. Concerning at 10x–100x.**
- The [smoking gun](#smoking-gun) makes this materially worse: until the wrap pattern uses `REQUIRES_NEW`, every Phase 3 engine write pins the lock for the full FUB round-trip (200–800 ms). A handful of concurrent engine writes for the same person serialize at ~half a second each.

#### Tracker memory

- `ConcurrentHashMap<TrackerKey, EngineWriteRecord>` per [`plan.md` §6](./plan.md#L241).
- Entry size: small (key + changed-field set + runId + timestamp).
- 1000 writes/sec × 30 s TTL = 30 k entries. Trivial.
- **Not a real concern at any plausible scale.**

#### Tracker durability

- Process crash → all tracker entries lost.
- During the 30-second window after crash, every echo produces a phantom event (no tracker record to match against).
- Plan punts to "Redis-backed tracker, future" ([`plan.md` §"Out of scope"](./plan.md#L308)).
- **For dev phase: fine.** **For production: this needs to land before production traffic, because crashes happen and the phantom-event class is exactly what Phase 3 exists to prevent.** Recommend promoting `RedisEngineWriteTracker` (or a persisted equivalent) from "future" to "Phase 4 prerequisite."

#### Pattern enforcement

- Phase 3 wraps 4 step types. The pattern is **convention**, not type-enforced.
- Nothing prevents a 5th FUB-mutating step from being added without the wrap.
- **Recommendation:** introduce `interface FubMutatingStep extends WorkflowStepType` with required `capturePriorSnapshot` / `applyLocalUpdate` / `revertLocalUpdate` hooks. The orchestrator (a new `EngineWriteCoordinator`) drives the dance; the step provides only the field-level operations. Convention → SPI.
- Cost: one more file, one extra interface, ~50 LOC of orchestrator. Buys structural enforcement against the next forgotten wrap.

#### Hidden coupling — optimistic local

- Phase 3 introduces an **informal invariant**: "local state for fields {`assignedUserId`, `assignedPondId`, `tags`} may be optimistically ahead of FUB by up to ~500 ms."
- Any reader of local Person state that assumes it reflects FUB ground truth is now wrong inside that window.
- [`PersonSnapshotResolver`](../../../src/main/java/com/flux/service/person/PersonSnapshotResolver.java) is used in `RunContext.person` for workflow expressions. **Workflows can now act on optimistic state.**
- Whether that's a feature (workflows see immediate engine effects without waiting for echo) or a bug (workflows act on uncommitted FUB intent that may roll back) depends on the workflow.
- **Recommendation:** call this out explicitly in `plan.md` so workflow authors understand the new semantics. Document an example: a workflow that runs immediately after `fub_reassign` and reads `person.assignedUserId` will see the new value even if FUB's write later fails and reverts.

---

### A separate race harness

#### Why existing harnesses cannot cover this

- **[`ReplayHarnessTest`](../../../src/test/java/com/flux/replay/ReplayHarnessTest.java):** drives recorded webhook sequences with payload-relative timing. **Cannot drive engine-write → external-webhook → echo races** because external-webhook arrival timing is not in any recording. Replay timing is end-of-the-pipeline; race scenarios require controlling timing relative to engine writes that didn't exist when the incident was recorded.
- **[`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/flux/integration/PersonUpsertConcurrencyStressTest.java):** exercises only `PersonUpsertService.upsertFubPerson` under parallel calls. **No step execution, no FUB call timing, no tracker.** Right prior art for the lock-discipline pattern, wrong scope for Phase 3.

#### What the new harness looks like

```text
class EngineWriteRaceHarness {
  // Fake FUB client with configurable per-method delay.
  //   followUpBossClient.reassignPerson(...) → wait Nms, return canned response (or 500)
  // CountDownLatch orchestration: release E and W threads at known offsets.
  //   schedule(E, +0ms), schedule(externalW, +20ms), schedule(echoW, +500ms)
  // Assertions:
  //   - events table contains exactly the expected (kind, entity, payload, source-annotation) rows
  //   - tracker has expected (hit, miss, eviction) counts
  //   - local Person state matches expected "FUB ground truth" at end of scenario
  // Scenarios = the matrix cells codified as tests (A1–A7, C1–C3, D1–D4).
}
```

#### What it would catch beyond the matrix itself

- **The smoking gun structural defect.** A naive wrap that holds the lock across the FUB call would time out under fake-FUB-delay=500ms with concurrent webhooks, surfacing the lock-hold issue. Unit tests with mocked FUB return immediately and never expose this.
- **Tracker key collisions** under back-to-back same-person engine writes.
- **TTL-too-short regressions** if someone tunes the eviction interval.

#### Sizing

- Skeleton + fake FUB client + CountDownLatch orchestration: ~1.5 days
- Scenarios for A1–A7: ~1 day
- Scenarios for C1–C3 (accumulating field): ~0.5 day
- Scenarios for D1–D4 (note creation): ~1 day, contingent on the note tracker design landing
- **Total: ~3–5 days**

Cheaper than debugging a phantom-event incident in production once Phase 4 ships and workflows consume domain events.

#### Where it lives

`src/test/java/com/flux/integration/EngineWriteRaceHarness.java` (sibling to `PersonUpsertConcurrencyStressTest`) plus fixture data in `src/test/resources/engine-write-race/`. Testcontainers Postgres for real lock semantics; fake FUB client wired via a `@TestConfiguration`.

---

### Proposed Phase 3 plan revisions

These are concrete amendments to [`README.md`](./README.md) §"Phase 3" deliverables. They will be applied in the next plan-lock pass once the user approves direction.

#### Revision 1 — Reframe tracker role

Current `README.md` reads as if local-state-first is the primary suppression mechanism and the tracker is auxiliary. **Reverse the framing:** local-state-first handles the common case (single scalar write, no concurrency); the tracker handles the wider failure surface (cells A4, A6, A7, C2, C3, D1, D2, D4 above).

The deliverable list stays the same; the **rationale and exit-criteria language** needs to acknowledge what each mechanism actually owns.

#### Revision 2 — Carve out `fub_create_note` as a separate pattern

`README.md` deliverable 2 ("Wrap engine-write step types — the **four** FUB-mutating steps") lists `fub_create_note` alongside the three person-mutating steps. **Split this:**

- **Sub-pattern A** (the three person-mutating steps): local-state-first + tracker annotation, as currently described.
- **Sub-pattern B** (`fub_create_note`): tracker-only. Engine records on the tracker immediately after the FUB POST returns (keyed by the returned `noteId`). When `notesCreated` echo arrives, dispatcher annotates `source=ENGINE` if the tracker has a recent record. Separately, the engine records on the tracker for the person's `lastNoteAt` (or whatever person-side fields FUB updates on note creation) so the `peopleUpdated` echo can also be annotated.

Document the residual race window (echo arrives before POST returns) and either accept it or design a synchronous record-before-POST that uses a content hash.

#### Revision 3 — Decide explicitly on accumulating-field policy for `fub_add_tag`

The plan must pick one of:
- **(a) Local-state-first + accept C2/C3** — phantom "removal" events possible; rely on workflow `change.source` filtering.
- **(b) Tracker-only for tags** — do not write local optimistically; let FUB's echo be the source of truth; annotate via tracker. Loses the "echo produces no event" win but eliminates the phantom-removal class.
- **(c) Defer `fub_add_tag` wrapping** to a later phase.

**Recommendation:** (b). The phantom-removal class is genuinely dangerous because it's indistinguishable from a real external removal. The plan's lean of (a) carries this risk forward into Phase 4 workflows.

#### Revision 4 — Make `REQUIRES_NEW` a pattern requirement, not a footnote

The [smoking gun](#smoking-gun) is the most common implementation defect Phase 3 can ship with. The plan must state explicitly:

> The wrap's inner write (`capture prior → update local → record tracker`) MUST run in a `REQUIRES_NEW` transaction (or fully outside the outer `@Transactional` on `executeClaimedStep`). The FUB HTTP call happens **after** the inner transaction commits. On FUB failure, the revert runs in a second `REQUIRES_NEW` transaction. Holding the row lock across the FUB call is incorrect and will pin the webhook async pool under burst load.

Mirrors the discipline already established in `PersonUpsertService` for `DIVE` recovery — same pattern, different motivation.

#### Revision 5 — Promote tracker durability

`plan.md` Out-of-scope table lists "Redis-backed `EngineWriteTracker`" as "future." **Promote to a Phase 4 prerequisite, not Phase 3.** Document the in-memory tracker as dev-phase-only and the crash-window cost (~30s of phantom events) as the known trade-off.

The interface boundary is correct; only the impl swap needs to land before production traffic.

#### Revision 6 — Pattern enforcement via SPI

Introduce `interface FubMutatingStep extends WorkflowStepType` with the capture / apply / revert hooks. The new `EngineWriteCoordinator` invokes these in the correct order with the correct transaction semantics. Steps that mutate FUB but do not implement this interface fail validation at registration time.

#### Revision 7 — Document the optimistic-local invariant for workflow authors

Add a section to `plan.md` (and reference from the workflow-authoring guide once it exists) stating that local Person state for the engine-wrapped fields can be optimistically ahead of FUB by up to ~500 ms. Give one example of a workflow that reads `person.assignedUserId` immediately after `fub_reassign` and sees the engine's intended value even if FUB's write later fails.

#### Revision 8 — Add the race harness as a Phase 3 deliverable

Sized at ~3–5 days. Sibling to `PersonUpsertConcurrencyStressTest`. Scenarios A1–A7, C1–C3, D1–D4 codified as tests. Required for Phase 3 exit criteria.

---

### Risks revealed by the matrix (mid-flight detection signals)

| Risk | Signal during build |
|---|---|
| Wrap pattern holds lock across FUB call | Race harness with fake-FUB-delay=500ms + 10 concurrent webhooks → test exceeds threshold |
| Tracker TTL too short → phantom events on slow echoes | Tracker miss-rate metric > expected baseline (workflow-author-visible) |
| Accumulating-field optimism produces phantom "removal" events in dev | Replay harness extended with a synthesized concurrent external tag-add fixture |
| Note tracker keyed on `noteId` misses early echoes | Synthesized scenario in race harness with FUB POST-return delay > webhook arrival delay |
| `FubMutatingStep` SPI not adopted by new step types | Validator at workflow registration refuses to register an unwrapped FUB step |

---

### Open questions (need user input before drafting `plan.md`)

1. **Accept silent data loss on A6/C3 revert, or rework revert semantics?** Plan §5 chose "restore prior snapshot." The matrix shows this destroys legitimate concurrent changes. Defaulting to "self-heals on next webhook" is dev-phase-acceptable; we should make the production decision explicit.
2. **Accumulating-field policy for `fub_add_tag`** — (a), (b), or (c)?
3. **Promote Redis-backed tracker to Phase 4 prerequisite?** Or accept the crash-window phantoms as a documented dev limitation indefinitely?
4. **`FubMutatingStep` SPI yes/no?** Convention is cheaper; SPI is enforceable. Where does the team want the discipline?
5. **Race harness as Phase 3 deliverable or follow-up?** The matrix says deliverable. The cost is ~3–5 days against the rest of Phase 3 (~5–8 days of substrate work). Roughly doubles the phase's size.

---

### Cross-references

- High-level Phase 3 deliverables: [`README.md` §"Phase 3"](./README.md)
- Architectural rationale for local-state-first writes: [`plan.md` §5](./plan.md#L237)
- Tracker interface: [`plan.md` §6](./plan.md#L241)
- Out-of-scope (durable outbox, Redis tracker): [`plan.md` §"Out of scope"](./plan.md#L289)
- Phase 2 lock-discipline prior art: [`PersonUpsertService.upsertFubPerson`](../../../src/main/java/com/flux/service/person/PersonUpsertService.java) + [`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/flux/integration/PersonUpsertConcurrencyStressTest.java)
- Smoking gun: [`WorkflowStepExecutionService.executeClaimedStep`](../../../src/main/java/com/flux/service/workflow/WorkflowStepExecutionService.java) line 68 outer `@Transactional`
