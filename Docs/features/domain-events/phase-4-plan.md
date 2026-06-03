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
2. **4e** — flip `emit-events` **ON**. Engine's own `person.state_changed` events become visible (annotated `source=ENGINE` → `event.origin='ENGINE'`), but the **platform engine-echo gate excludes them from every workflow by default** (two-level gate — see `plan.md` "Engine-echo exclusion"), so no per-workflow predicate is needed. Replay-verify bad-run-rate.
3. **4e** — hard-cut the old webhook-shaped path.

---

## Sub-phases

### 4a — Substrate (dormant, additive)

**Deliverables**
- `V23` migration: `workflow_runs.domain_event_id BIGINT NULL` (FK → `events.id`, `ON DELETE SET NULL`). *(The originally-planned `suppressed_by_run_id` was dropped 2026-06-03 — Phase 5 is cancel-only and attributes the cancel to an event via `reason_code` + `domain_event_id`, not a run-to-run link. See `phases.md` changelog.)*
- Entity/field wiring on the run entity for `domain_event_id`. No population yet.
- Skeleton `DomainEventScopeBuilder` (or equivalent seam) — signatures only, no live caller.

**Verification:** migration applies cleanly to dev DB; full suite green; no behavior change.
**Exit:** columns exist, all `NULL`; nothing references them at runtime.
**Risk:** none — purely additive.

### 4b — `DomainEventTriggerType` + trigger-time scope (additive, isolated, not wired)

> **Decisions locked 2026-06-03 (revised — "no Rail 1"):** app is dev-phase, so Rail 1 is **deleted, not coexisted** → no dual-mode anywhere. **4b is purely additive** — builds the new trigger front-end alongside the old, wired to nothing, deleting nothing. `buildRunContext`/`ExpressionScope` are **not touched in 4b**; the step-time scope is rewritten single-mode in **4d**, together with deleting Rail 1. Trigger type is clean/standalone (not implementing `WorkflowTriggerType`); `person.*` required → **closes #17**.

**Deliverables (all new — only `DomainEvent` is modified)**
- **`DomainEvent` += `Long id`** — `DomainEventEmitter` passes `saved.getId()` (already in hand). Gives `event.id` and lets 4d populate `workflow_runs.domain_event_id`. Sole construction site is the emitter.
- **`DomainEventScopeBuilder`** (`service/workflow/expression/`) — lean mapper, the single source of the domain-event scope shape. `build(DomainEvent, person) → Map`: `event{id,kind,entityType,entityId,payload}`; `change{<field>:{changed,old,new}, source}` (state_changed only, from `changed_fields/previous/current`; missing field → absent → falsy); `current` (= `payload.current`; created + state_changed); `person` (the snapshot). **No `webhook.*`** (step-time, 4d).
- **`DomainEventTriggerType`** (`service/workflow/trigger/`) — clean, **standalone** (does NOT implement `WorkflowTriggerType`). Deps: `PersonSnapshotResolver`, `DomainEventScopeBuilder`, `ExpressionEvaluator`. `matches(DomainEvent, config)`: `eventKind == config.on` → resolve person (via `entityId` when `entityType="person"`) → build scope → evaluate `config.filter` (no filter → match on kind). `extractEntity(DomainEvent)` → `EntityRef`.
- **Not wired** as a listener; nothing creates domain-event runs yet.

**Explicitly NOT in 4b → deferred to 4d:** `buildRunContext`/`ExpressionScope` step-time rewrite (single-mode), `webhook.*`, listener wiring / `route(DomainEvent)`, **Rail 1 deletion** (`FubWebhookTriggerType`, `TriggerMatchContext`, `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `route(NormalizedWebhookEvent)` + its `process()` call). Validator → 4c.

**Verification:** unit tests only — scope builder per-kind + `change`/`current`/`event.origin`; trigger filter matrix — engine echo filtered, real assignment passes, kind-mismatch rejected, watched-field-unchanged rejected. (The test filter includes `event.origin != 'ENGINE'` to exercise the `event.origin` scope key — as an *opt-in* workflow would; the **default** production filter omits it, since engine-echo exclusion is platform-enforced.) **`event.origin` is always present (`ENGINE`/`EXTERNAL`)** so the `!= 'ENGINE'` predicate is never undefined in JSONata. No runtime path.
**Exit:** new trigger + scope resolve correctly in isolation; full suite green (4b touched no live path; Rail 1 untouched). **✅ DONE — 12 tests, full suite 696 green.**

### 4c — Validator generalization

**Deliverables**
- Per-event-kind **field-schema registry**: each event kind declares the fields a filter may reference. Person kinds populate from `PersonDiffComputer`'s diffable list (keyed off the computer, not a duplicated constant — drift-proof). `call`/`note` get the seam but are marked **unvalidated-payload** (documented gap, no consumer).
- Save-time **refusals**: `change.<field>`/`current.<field>` not in the person set (clear "field not captured — would never fire" message); old `webhook_fub`/`peopleUpdated`-typed trigger with a migration hint.
- **`event.*` (incl. `event.origin`) is always-valid metadata** — no special-casing. (`change.source` is no longer special either: `source` is a captured person field, so `change.source` validates like any field delta.)
- **Cross-checks**: `change.*` ⇒ `on = person.state_changed`; `current.*` ⇒ `on ∈ {person.created, person.state_changed}`.
- **Accept the optional `reactToEngineEvents` boolean** in the trigger config (validate type only). **No engine-echo warning** — exclusion is platform-enforced and safe-by-default (the two-level gate; `plan.md` "Engine-echo exclusion"), so there is no author predicate to forget. The whole warnings-channel question is moot.

**Verification:** validator unit tests — uncaptured-field refusal, old-shape refusal, `event.origin` recognized, cross-check failures, `reactToEngineEvents` accepted.
**Exit:** save-time validation closes the silent-no-fire gap for person triggers; append kinds explicitly deferred.
**Risk:** low — save-time only, no runtime path.

### 4d — The switch

> **Plan locked 2026-06-03.** Dev-phase → no Rail 1 coexistence, so the whole cutover is one coordinated step (the old "4e" is folded in — there is no separate hard-cut phase). Split into two commits so the risky part is isolated. Decisions: **(1)** freeze the domain-event payload + proximate webhook payload into the run at plan time (step-time is a pure mapper; only `person` re-resolved live); **(2)** repurpose `/trigger-types` to a domain-event-kinds + `{on,filter}` schema catalog; **(3)** flip `engine.write.emit-events` **ON** at the end (consumption gate keeps engine events harmless); **(4)** global-capability flag `engine.events.workflow-consumption.enabled` (default `false`).

#### 4d.1 — Additive prep (behavior unchanged, reviewable)
- `WorkflowPlanRequest` += `domainEventId`; `WorkflowExecutionManager.plan` sets `run.domainEventId` (null for current callers → no change).
- **`EngineEchoGate`** — config flag `engine.events.workflow-consumption.enabled` (default false) + `shouldExclude(DomainEvent, triggerConfig)` = `event.origin == "ENGINE" AND NOT (globalCapability AND reactToEngineEvents)`. Built + unit-tested, **not yet wired**.
- *Safe: nothing reads the new column / gate until 4d.2.* (The step-time scope rewrite incl. `webhook.*` is coupled to `buildRunContext` → done in 4d.2.)

#### 4d.2 — The atomic switch (one coherent commit)
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

## Risks (phase-level)

| Risk | Detection signal |
|---|---|
| Scope refactor breaks a live step expression | 4b unit matrix + 4d replay; validator runs at save |
| Listener consumes events the old path also acts on (double-run during 4d coexistence) | 4d replay compares both paths; the hard cut (4e) removes the overlap |
| Flag flipped before listener wired (wrong order) → engine events with no consumer, or consumer with un-annotated echoes | Sequencing note above; flag is a deliberate 4e step, not bundled into 4d |
| A second production workflow exists on the old shape | Re-confirm the live workflow table at 4e before the cut |
| Dispatch crash-window with a real consumer | Observability log (4e exit); durable poller is a separate follow-up |

## Non-goals (this phase)

- Run-collision handling — Phase 5 (now cancel-only; reuses the existing `WorkflowRunControlService` cancel, no new column).
- Durable-outbox poller — separate tracked follow-up.
- Note-trigger support / `note` validator schema — deferred until `notesCreated` is ingested (#27).
- Append-event (`call`) filter field validation — deferred until a `call`-triggered workflow exists.
- Removing the `engine.write.emit-events` toggle — it is a platform config, kept.

## Repo decisions impact

Likely `Yes` — the `{on, filter}` trigger schema is part of the workflow JSON contract; document it in `Docs/repo-decisions/` at 4e. The `engine.write.emit-events` platform toggle is a secondary candidate once the config page exists.
