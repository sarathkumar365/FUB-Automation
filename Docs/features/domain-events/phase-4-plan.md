# Phase 4 — Commit-level plan

> **Status:** Planning (not started). Decomposes the Phase 4 section of [`phases.md`](./phases.md) into five reviewable sub-phases. Built on the fresh-eyes verification of 2026-06-02 (see the `phases.md` changelog) and a local-DB workflow check the same day.
>
> **Intent (unchanged):** workflows stop subscribing to raw webhooks and subscribe to **domain events**; the one production workflow (`agent_followup_enforcement`) is re-authored against the new `{on, filter}` shape; the bad-run-rate win (~50% → <5%) lands here. Phases 0–3 are dormant plumbing until this phase consumes them.

---

## Ground truth at plan time (verified against code + local DB, 2026-06-02)

- **Latest migration is `V22`.** Phase 4's additive migration is **`V23`**.
- **Events are already flowing.** `DomainEventEmitter.emit()` is called ungated from `PersonUpsertService` / `CallUpsertService` / `NoteEmissionService`; rows land in `events` and after-commit dispatch fires on every webhook. `InMemoryDomainEventDispatcher`'s listener list is **empty** — so registering the first `DomainEventListener` is what starts consumption, **not** the `engine.write.emit-events` flag.
- **`engine.write.emit-events`** (`application.properties:79`, default `false`) gates only `DefaultEngineWriteCoordinator:180` — the engine's emission of *its own* `person.state_changed` event. Annotation (`source=ENGINE`) is always on irrespective of the flag. It is a **platform config toggle**, target ON.
- **Router:** `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` has a single caller — `WebhookEventProcessorService:114` — and only routes `findByStatus(ACTIVE)`. The new path is a second method registered as a listener.
- **Trigger types:** `FubWebhookTriggerType` (id `webhook_fub`, retiring). `DomainEventTriggerType` is new.
- **Scope today:** `FubWebhookTriggerType.matches()` puts only `{event: {payload}}` into scope. `person.*` is NOT in trigger scope (closes #17). Run-time scope is built in `WorkflowStepExecutionService.buildRunContext`.
- **Validator:** `WorkflowGraphValidator.validatePersonFieldReferences` checks `person.*` against `capturedFieldNames()` (`SNAPSHOT_FIELDS` + `kind`). Diffable-field source of truth: `PersonDiffComputer`.
- **Local-DB finding:** 18 workflow rows, **all `webhook_fub`**, **none `ACTIVE`** (DRAFT/INACTIVE/ARCHIVED; several duplicate dev keys). Runtime hard-cut risk is therefore zero today (router only touches ACTIVE). The stale rows need a stance at 4e (see its Risk note) — they are dev noise, not production workflows to migrate.

---

## Cutover sequencing (the spine of the phase)

Because events already flow, the listener and the flag must flip in order, never bundled:

1. **4d** — register listener + migrate `agent_followup_enforcement`, `emit-events` **OFF**. Engine writes stay invisible (pure echo-suppression, proven in Phase 3). Replay-verify old workflow behaves.
2. **4e** — flip `emit-events` **ON**. Engine's own `person.state_changed` events become visible, annotated `source=ENGINE`, and are filtered out by the workflow's `change.source != 'ENGINE'`. Replay-verify bad-run-rate.
3. **4e** — hard-cut the old webhook-shaped path.

---

## Sub-phases

### 4a — Substrate (dormant, additive)

**Deliverables**
- `V23` migration: `workflow_runs.domain_event_id BIGINT NULL` (FK → `events.id`, `ON DELETE SET NULL`) + `workflow_runs.suppressed_by_run_id BIGINT NULL` (self-FK, used in Phase 5; added now to avoid a second migration).
- Entity/field wiring on the run entity for both columns. No population yet.
- Skeleton `DomainEventScopeBuilder` (or equivalent seam) — signatures only, no live caller.

**Verification:** migration applies cleanly to dev DB; full suite green; no behavior change.
**Exit:** columns exist, all `NULL`; nothing references them at runtime.
**Risk:** none — purely additive.

### 4b — `DomainEventTriggerType` + expression scope

**Deliverables**
- `DomainEventTriggerType implements WorkflowTriggerType` (id e.g. `domain_event`): reads `config.on` (event kind) + `config.filter` (JSONata). `matches()` checks `event.eventKind == config.on` then evaluates the filter against the new scope. `extractEntities()` returns the event's entity ref.
- **Expression scope refactor** for domain-event evaluation:
  - `event.*` → domain event metadata (`id`, `kind`, `entityType`, `entityId`, `payload`)
  - `change.<field>.changed/.old/.new` → sugar over `event.payload` (only for `person.state_changed`)
  - `current.<field>` → sugar over `event.payload.current` (`person.created` + `person.state_changed`)
  - `change.source` → `event.payload.source` (the annotation)
  - `webhook.*` → proximate raw webhook payload
  - `person.*` → current Person snapshot, now in **trigger** scope too (closes #17)
- Registered in `WorkflowTriggerRegistry` alongside `webhook_fub`, but **not** wired as a dispatcher listener yet.

**Verification:** unit tests for each scope key + a matrix of filter expressions against synthetic `DomainEvent`s (match/no-match, `change.source` present/absent). No runtime path exercised.
**Exit:** trigger type resolves and evaluates filters correctly in isolation.
**Risk:** the scope refactor touches `buildRunContext` (a live path). Contained by not registering the listener — the old webhook path keeps its existing scope until 4d.

### 4c — Validator generalization

**Deliverables**
- Per-event-kind **field-schema registry**: each event kind declares the fields a filter may reference. Person kinds populate from `PersonDiffComputer`'s diffable list (keyed off the computer, not a duplicated constant — drift-proof). `call`/`note` get the seam but are marked **unvalidated-payload** (documented gap, no consumer).
- Save-time **refusals**: `change.<field>`/`current.<field>` not in the person set (clear "field not captured — would never fire" message); old `webhook_fub`/`peopleUpdated`-typed trigger with a migration hint.
- **Allowlist** `change.source`.
- **Cross-checks**: `change.*` ⇒ `on = person.state_changed`; `current.*` ⇒ `on ∈ {person.created, person.state_changed}`.
- **Warn (not refuse)** on a `person.state_changed` filter missing `change.source`.

**Verification:** validator unit tests — uncaptured-field refusal, old-shape refusal, `change.source` allowed, cross-check failures, missing-`change.source` warning.
**Exit:** save-time validation closes the silent-no-fire gap for person triggers; append kinds explicitly deferred.
**Risk:** low — save-time only, no runtime path.

### 4d — Wire listener, flag OFF (cutover step 1)

**Deliverables**
- `WorkflowTriggerRouter.route(DomainEvent)` registered as a `DomainEventListener` (Spring auto-wires the constructor-injected list). Looks up `domain_event`-typed ACTIVE workflows whose `on` matches `event.eventKind`, evaluates filters, calls `WorkflowExecutionManager.plan` populating **both** `domain_event_id` and the proximate `webhook_event_id`.
- Planner/manager populate `workflow_runs.domain_event_id`.
- `engine.write.emit-events` stays **OFF**.

**Verification:** replay the three incidents (20123 echo cascade, 20235 FUB burst, 20207 triple-run) through the **new** listener; assert bad runs = 0 and engine reassigns produce no event (echo-suppression). Old webhook path still present, runs in parallel for comparison.
**Exit:** new pipeline drives runs end-to-end with the flag off; `domain_event_id` populated on every new run.
**Risk:** first live consumption. Mitigated by echo-suppression already proven in Phase 3 and by the flag staying off (engine writes invisible, so no annotated-event handling tested yet).

### 4e — Re-author, flip flag, hard cut (cutover steps 2–3)

**Deliverables**
- Re-author `agent_followup_enforcement` trigger → `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' AND change.assignedUserId.changed AND change.source != 'ENGINE'" }`. Migrate any step expression using `event.payload.*` → `webhook.payload.*`.
- Flip `engine.write.emit-events` **ON**; verify annotated engine events are filtered out.
- **Hard cut:** delete `route(NormalizedWebhookEvent)`, `FubWebhookTriggerType`, and the `WebhookEventProcessorService:114` call. ~2 new classes (4b/4d) + 2 deletions.

**Verification:** replay bad-run-rate <5% on recorded field-obs traffic; the re-authored workflow's expressions all resolve; old-shape triggers rejected at save.
**Exit:** `agent_followup_enforcement` runs entirely on the new pipeline; old path deleted; crash-window decision recorded (accept gap + observability log); note channel deferred (#27).
**Risk — stale workflow rows.** All 18 local rows reference `webhook_fub`; deleting that type leaves them unresolvable. None are ACTIVE so runtime is unaffected (router skips unknown types / only routes ACTIVE), but **decide before the cut**: leave them (re-save/re-activate then fails loudly — the intended migration-forcing behavior) or purge the dev junk. Production reality must be re-confirmed against the live workflow table at cut time — the local DB is dev noise and is not authoritative for "exactly one active workflow."

---

## Risks (phase-level)

| Risk | Detection signal |
|---|---|
| Scope refactor breaks a live step expression | 4b unit matrix + 4d replay; validator runs at save |
| Listener consumes events the old path also acts on (double-run during 4d coexistence) | 4d replay compares both paths; the hard cut (4e) removes the overlap |
| Flag flipped before listener wired (wrong order) → engine events with no consumer, or consumer with un-annotated echoes | Sequencing note above; flag is a deliberate 4e step, not bundled into 4d |
| A second production workflow exists on the old shape | Re-confirm the live workflow table at 4e before the cut |
| Dispatch crash-window with a real consumer | Observability log (4e exit); durable poller is a separate follow-up |

## Non-goals (this phase)

- Run-level uniqueness / suppression — Phase 5 (the `suppressed_by_run_id` column is added in 4a, used later).
- Durable-outbox poller — separate tracked follow-up.
- Note-trigger support / `note` validator schema — deferred until `notesCreated` is ingested (#27).
- Append-event (`call`) filter field validation — deferred until a `call`-triggered workflow exists.
- Removing the `engine.write.emit-events` toggle — it is a platform config, kept.

## Repo decisions impact

Likely `Yes` — the `{on, filter}` trigger schema is part of the workflow JSON contract; document it in `Docs/repo-decisions/` at 4e. The `engine.write.emit-events` platform toggle is a secondary candidate once the config page exists.
