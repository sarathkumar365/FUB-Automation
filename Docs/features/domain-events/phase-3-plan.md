# Phase 3 — Implementation Plan (step-by-step)

Status: `IN PROGRESS` — 3a, 3b, 3c, 3d shipped; 3e remains.

Companion to [`phases.md`](./phases.md) §"Phase 3 — Local-state-first engine writes". `phases.md` is the canonical statement of **what** Phase 3 delivers and **why**; this file is the **commit-level order of operations** — concrete files, sequencing, test gates, defaults.

> **Plan-lock changelog (2026-05-29):** Phase 3 was originally framed as "wrap 4 step types with local-state-first writes." [`phase-3-race-matrix.md`](./phase-3-race-matrix.md) walked the matrix of (step type × scenario × timing × concurrency) and exposed that the 4 steps fall into **3 fundamentally different mechanisms**, not one. This plan restructures Phase 3 accordingly:
>
> - **Revert dropped entirely.** `RetryPolicy.DEFAULT_FUB` already handles transient FUB failures; permanent failures accept drift until the next webhook re-syncs. Plan.md §5's "restore prior snapshot" semantics are reversed (see §5 changelog).
> - **Three operation modes via `EngineWriteCoordinator`,** not one wrap pattern: `SCALAR_FIELD_UPDATE` (reassign, move_to_pond), `ENTITY_APPEND_TRACKED_ONLY` (add_tag — no local-state-first), `ENTITY_CREATE_TRACKED_ONLY` (create_note — no local state to write).
> - **`REQUIRES_NEW` is a pattern requirement,** not a footnote. The outer `@Transactional` on `WorkflowStepExecutionService.executeClaimedStep` (line 68) would otherwise pin the row lock across the FUB HTTP call. Every inner write goes through `TransactionTemplate(REQUIRES_NEW)`, mirroring `PersonUpsertService`'s DIVE-recovery discipline.
> - **Tags become tracker-only.** Phase 3 will not local-state-first for `tags`. C2 (phantom "tag removed" event from concurrent external add) is structurally unfixable with optimistic local writes; tracker-only annotates the real echo as `source=ENGINE`.
> - **Notes become tracker-only on two channels.** `note.created` echo and the person-side `peopleUpdated` echo (`lastNoteAt` etc.) both get tracker annotation. Documented as a known annotation-needs-verification entry until Phase 4 actually consumes these events.
> - **Race harness is a Phase 3 deliverable**, distributed across sub-phases (A-cells in 3b, C-cells in 3d, D-cells in 3e). Skeleton + fake FUB client in 3a.
> - **Redis-backed tracker promoted to Phase 4 prerequisite.** Phase 3 ships in-memory; the crash-window phantom-event class is dev-acceptable until consumers exist.

## Scope reminder (one paragraph)

By the end of Phase 3, every engine-originated write to FUB (`fub_reassign`, `fub_move_to_pond`, `fub_add_tag`, `fub_create_note`) flows through `EngineWriteCoordinator`, which records the intent in `InMemoryEngineWriteTracker` and (for scalar updates only) applies the change to local Person state before the FUB call. `DomainEventEmitter` consults the tracker when a `person.state_changed`, `note.created`, or note-side `person.state_changed` event is about to emit, and annotates `payload.source = "ENGINE"` when the diff matches a recent tracker record. No workflow filters on this annotation in Phase 3 — `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` still runs on the old webhook-shaped path unchanged. Phase 4 is when annotation starts being read. Phase 3 is pure substrate; the bad-run-rate win arrives at Phase 4.

## Sub-phase split — 5 reviewable commits

| Sub-phase | Theme | Approx files / LOC | Risk |
|---|---|---|---|
| **3a** | Scaffold: tracker interface + in-memory impl + coordinator skeleton (3 op modes wired but no callers) + emitter annotation hook + race harness skeleton with fake FUB client | ~12 / ~600 | Low — no behaviour change |
| **3b** | Wrap `fub_reassign` (scalar mode) + race harness scenarios A1–A7 | ~5 / ~350 | **High** — the load-bearing pattern (REQUIRES_NEW + lock + tracker + emitter annotation) lands here |
| **3c** ✅ | Wrap `fub_move_to_pond` (scalar mode, reuses 3b coordinator path) + harness A1/A3/A4/A5 mirror for `assignedPondId`. **DONE** — 672 tests green. | ~3 / ~150 | Low — identical pattern to 3b |
| **3d** ✅ | Wrap `fub_add_tag` (tracker-only append mode) + harness C1–C3. **DONE** — 676 tests green. | ~4 / ~250 | Medium — different mechanism; first exercise of tracker-only path |
| **3e** | Wrap `fub_create_note` (tracker-only on `note.created` and person-side `peopleUpdated` echoes) + harness D1–D4 + `NoteEmissionService` annotation hook | ~5 / ~300 | Medium — two-channel tracker, content-hash key for the early-echo race |

Each commit ships with a green `./mvnw clean test` (589 tests post-Phase-2 baseline; new tests added per sub-phase).

---

## Sub-phase 3a — Scaffold

**Goal:** the tracker exists, the coordinator exists with all 3 op modes wired, the emitter consults the tracker for annotation. Nothing calls the coordinator yet. Race harness skeleton + fake FUB client + assertion infrastructure are in place but no scenarios are codified.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/event/EngineWriteTracker.java` | Interface: `record(TrackerEntry)`, `findMatching(entityType, entityId, changedFields, withinMs) → Optional<EngineWriteRecord>`, `evictExpired(now)`. **No `markFailed` method** — revert is dropped (see plan-lock §"Revert dropped"). |
| 2 | `src/main/java/com/fuba/automation_engine/service/event/EngineWriteRecord.java` | Record: `id`, `entityType`, `entityId`, `changedFields` (Set<String>), `runId`, `recordedAt`. Used as the tracker's value type and the `findMatching` return. |
| 3 | `src/main/java/com/fuba/automation_engine/service/event/InMemoryEngineWriteTracker.java` | Impl: `ConcurrentHashMap<TrackerKey, List<EngineWriteRecord>>` keyed on `(entityType, entityId)`. **Scheduled eviction every 10s** via `@Scheduled`. **Default TTL 30s** configurable via `engine.write.tracker.ttl-seconds`. Hit/miss/eviction logged at INFO per [`phases.md`](./phases.md) §Phase 3 deliverable 5. |
| 4 | `src/main/java/com/fuba/automation_engine/service/event/EngineWriteCoordinator.java` | Interface with three methods: <br>• `<T> StepExecutionResult applyScalarFieldUpdate(String sourcePersonId, Map<String, JsonNode> fieldUpdates, Long runId, Supplier<T> fubCall)` <br>• `<T> StepExecutionResult applyEntityAppendTrackedOnly(String sourcePersonId, String fieldName, JsonNode appendedValue, Long runId, Supplier<T> fubCall)` <br>• `<T extends CreatedEntityRef> StepExecutionResult applyEntityCreateTrackedOnly(String sourcePersonId, String entityType, Long runId, Supplier<T> fubCall, BiConsumer<EngineWriteTracker, T> recordSideEffects)` <br>Each method also returns the FUB call's result via the `StepExecutionResult` payload. |
| 5 | `src/main/java/com/fuba/automation_engine/service/event/DefaultEngineWriteCoordinator.java` | Impl: <br>• **Scalar mode**: `REQUIRES_NEW` inner tx → `personRepository.findBy…ForUpdate` → apply field updates → `tracker.record(...)` → commit. Lock released. FUB call **outside any tx**. Return success or step-failure result. **No revert path.** <br>• **Append-tracked-only mode**: FUB call first (no tx). On success, `REQUIRES_NEW` inner tx → `tracker.record(...)` (entity + field + appended value as the "changedField" marker) → commit. <br>• **Entity-create-tracked-only mode**: FUB call first. On success, `REQUIRES_NEW` inner tx → invoke `recordSideEffects` callback (caller decides what tracker entries to write — typically one for the new entity id and one for the person-side echo field). |
| 6 | `src/main/java/com/fuba/automation_engine/service/event/DomainEventEmitter.java` *(modify)* | Inject `EngineWriteTracker`. In `emit(...)`, before the in-tx insert, call `tracker.findMatching(entityType, entityId, payloadChangedFields, withinMs=tracker.ttl)`. If hit, mutate the payload to add `"source": "ENGINE"` at the top level. Emit row + after-commit dispatch as before. Tracker miss → no annotation; payload unchanged. |
| 7 | `src/main/java/com/fuba/automation_engine/service/event/DomainEvent.java` *(possibly modify)* | If the `source` annotation is part of the in-memory `DomainEvent` shape (not just the persisted `payload` JSON), add an optional `source` field. **Decision: keep it in `payload` only.** Tracker hits mutate the payload JSON; downstream consumers (Phase 4) read `event.payload.source`. No new field on the record. Avoids churning every listener signature. |
| 8 | `src/main/resources/application.properties` *(extend)* | `engine.write.tracker.ttl-seconds=30`; `engine.write.tracker.eviction-interval-ms=10000` |
| 9 | `src/test/java/com/fuba/automation_engine/race/EngineWriteRaceHarness.java` | Skeleton: Testcontainers Postgres + Spring context with `FakeFollowUpBossClient` wired. Provides scenario DSL: `engine().reassigns(personId).to(userId).at(tMs)`, `external().reassigns(personId).to(userId).at(tMs)`, `expect().exactStateChangedEvents(personId, n)`, `expect().eventPayloadAnnotated("ENGINE")`. CountDownLatch orchestration for releasing engine + webhook threads at configured offsets. |
| 10 | `src/test/java/com/fuba/automation_engine/race/FakeFollowUpBossClient.java` | `@TestConfiguration` `@Primary` bean replacing `FollowUpBossClient`. Per-method configurable response delay + canned response (success / transient / permanent). Records every call for harness assertions. |
| 11 | `src/test/java/com/fuba/automation_engine/service/event/InMemoryEngineWriteTrackerTest.java` | Record + findMatching with exact field-set match; with field-set subset (engine wrote A,B; diff includes A,B,C → hit because `engineFields ⊆ diffFields`); TTL eviction; concurrent record + read. |
| 12 | `src/test/java/com/fuba/automation_engine/service/event/DefaultEngineWriteCoordinatorTest.java` | Scalar mode happy path; scalar mode FUB failure (no revert — local stays updated, step returns failure result); append mode happy path; entity-create mode happy path; **REQUIRES_NEW semantics**: if the outer caller is in a transaction, the inner write commits independently (verify by rolling back the outer and confirming the tracker record + local change persist). |
| 13 | `src/test/java/com/fuba/automation_engine/service/event/DomainEventEmitterAnnotationTest.java` | Tracker hit → payload gains `source=ENGINE`; tracker miss → payload unchanged; emit-then-rollback still doesn't dispatch (Phase 2 invariant preserved). |

### Order within the commit

1, 2 → 3 → 4 → 5 → 6 → 7 (decision-only) → 8 → 9, 10 (in parallel) → 11, 12, 13 (in parallel).

### Test gate

`./mvnw clean test` green. 589 → 600+ tests (12 new). No existing test changes behaviour.

### What 3a does NOT change

No production caller of `EngineWriteCoordinator` exists yet. `FubReassignWorkflowStep`, `FubMoveToPondWorkflowStep`, `FubAddTagWorkflowStep`, `FubCreateNoteWorkflowStep` are untouched. `DomainEventEmitter` is modified but tracker is always empty in production → annotation never triggers → existing behaviour preserved.

### Tracker match semantics — locked here

- `findMatching(entityType, entityId, changedFields, withinMs)` returns a hit when **the tracker record's `changedFields` ⊆ the supplied `changedFields`**. Rationale: the engine wrote a known set of fields; the diff may include additional fields touched by concurrent activity; we annotate as ENGINE because at least some of the diff is ours.
- This is the loosest reasonable match and intentionally annotates the [A4](./phase-3-race-matrix.md#a-fub_reassign-scalar--assignedUserId) "real concurrent external change → phantom event" case as `ENGINE` even though the *trigger* of the event was external. The trade-off: Phase 4 workflows filtering `change.source != "ENGINE"` will silently suppress these. Acceptable because the alternative (strict equality match) misses A4 entirely.
- Documented in `known-issues.md` as a deliberate annotation-over-detection bias.

---

## Sub-phase 3b — Wrap `fub_reassign` (scalar mode) + race harness A-cells

**Goal:** `fub_reassign` routes through the coordinator's scalar mode. The race harness proves cells A1–A7 from [`phase-3-race-matrix.md`](./phase-3-race-matrix.md).

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubReassignWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Replace the direct `fubCallHelper.executeWithRetry(() -> followUpBossClient.reassignPerson(...))` call with `coordinator.applyScalarFieldUpdate(sourcePersonId, Map.of("assignedUserId", numericNode(targetUserId)), runId, () -> fubCallHelper.executeWithRetry(...))`. The retry stays inside the FUB-call Supplier — the coordinator owns tx semantics, the helper owns retry semantics. |
| 2 | `src/test/java/com/fuba/automation_engine/service/workflow/steps/FubReassignWorkflowStepTest.java` *(extend)* | Existing tests stay green (FUB call path unchanged from the workflow runtime's perspective). New tests: coordinator is invoked with the right field map; transient FUB failure still propagates as `transientFailure(FUB_REASSIGN_TRANSIENT)`; permanent FUB failure still propagates as `failure(FUB_REASSIGN_PERMANENT)`; local Person state is updated before the FUB call (verify via Testcontainers). |
| 3 | `src/test/java/com/fuba/automation_engine/race/scenarios/ReassignScenariosTest.java` | All 7 A-cells codified: <br>**A1** happy path — single engine reassign, echo arrives later, expect 0 `person.state_changed` events. <br>**A2** echo-during-commit — fake FUB delays 10ms; webhook fires at t=5ms; expect lock blocks, 0 events. <br>**A3** FUB-burst echo — 3 echoes for one engine write; expect 0 events. <br>**A4** real concurrent external change — engine writes Alice at t=0; external webhook to Carol arrives t=30ms; engine FUB PUT lands t=200ms; echo arrives t=500ms. Expect: 1 real event (Alice→Carol from external), 1 phantom event (Carol→Alice) **annotated `source=ENGINE`**. Document the annotation-over-detection bias in test comments. <br>**A5** FUB permanent failure, no concurrent activity — engine writes Alice, FUB returns 400 permanent. Expect: local stays Alice (no revert), step returns FAILED, no event. Next synthetic webhook with FUB ground-truth Bob → diff Alice→Bob → real event emits (the "misleading echo after permanent failure" known issue). <br>**A6** *(was: FUB transient with concurrent — destroys Carol)* — re-cast as: FUB transient, retry succeeds. Engine writes Alice, FUB transient at t=10ms, external Carol at t=20ms, retry succeeds at t=60ms putting Alice. Expect: 1 real event from external (Alice→Carol), 1 phantom (Carol→Alice) annotated ENGINE. Same outcome as A4; retry doesn't change the race. <br>**A7** two engine writes back-to-back — workflow A writes Alice (t=0), workflow B writes Carol (t=5ms); both FUB PUTs race. Expect: final echo annotated ENGINE regardless of PUT order (both writes have tracker records). |

### Order within the commit

1 → 2 → 3.

### Test gate

`./mvnw clean test` green. Race harness scenarios `@RepeatedTest(3)` to catch nondeterminism — if any A-cell flakes, **investigate before merge**, do not rerun.

### What 3b changes for users

`fub_reassign` engine writes now update local first and record on tracker. Echo webhooks for reassigns produce no event (happy path) or annotated events (concurrent-change cases). Nothing reads the annotation yet.

### Smoking-gun verification

A1 + A3 with `fakeFub.delayMs(500)` confirm that the lock is NOT held across the FUB call: concurrent webhooks for the same person don't block on the engine's FUB round-trip. If the implementation accidentally holds the lock (the smoking-gun defect), these tests time out or report serialized webhook processing.

---

## Sub-phase 3c — Wrap `fub_move_to_pond`

**Goal:** identical wrap as `fub_reassign` for `assignedPondId`. Reuses the entire scalar mode pipeline. Race harness B coverage is identical to A; codify by parameterization, not duplication.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubMoveToPondWorkflowStep.java` *(modify)* | Same shape as 3b#1: route through `coordinator.applyScalarFieldUpdate(sourcePersonId, Map.of("assignedPondId", numericNode(targetPondId)), runId, ...)`. |
| 2 | `src/test/java/com/fuba/automation_engine/service/workflow/steps/FubMoveToPondWorkflowStepTest.java` *(extend)* | Mirror 3b#2's test additions for the pond field. |
| 3 | `src/test/java/com/fuba/automation_engine/race/scenarios/MoveToPondScenariosTest.java` | A1, A3, A4, A5 mirrored for `assignedPondId`. A2/A6/A7 are coordinator-level concerns already proven in 3b — not re-tested per step. Keeps the suite focused. |

### Order within the commit

1 → 2 → 3.

### Test gate

`./mvnw clean test` green. Coverage delta: 4 new race scenarios.

### What 3c changes for users

`fub_move_to_pond` engine writes go through the same pipeline as reassign. Same suppression / annotation behaviour.

---

## Sub-phase 3d — Wrap `fub_add_tag` (tracker-only mode)

**Goal:** `fub_add_tag` calls FUB first, then records the tag-append on the tracker. **No local-state-first write to `tags`.** Race harness scenarios C1–C3 prove the tracker-only path correctly annotates echoes for engine writes and lets external concurrent tag changes flow through unaffected.

### Why tracker-only for tags

`tags` is an accumulating field. Optimistic local-state-first writes (write `[A,B,NEW]` locally before FUB confirms) cause [C2](./phase-3-race-matrix.md#c-fub_add_tag-accumulating--tags): any concurrent external webhook arriving before our FUB PUT lands sees local `[A,B,NEW]` and FUB payload `[A,B,X]` (no NEW yet) and emits a phantom `person.state_changed` event claiming NEW was removed. The "removal" is fabricated and indistinguishable from a real external removal.

Tracker-only inverts this: the FUB call happens first; the local update is applied by `PersonUpsertService` when the echo arrives (truth-from-FUB); the tracker annotates the echo's `person.state_changed` event as `source=ENGINE` so workflows can filter.

The cost is one extra event per engine tag-add (annotated). The benefit is eliminating the phantom-removal class entirely.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubAddTagWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Route through `coordinator.applyEntityAppendTrackedOnly(sourcePersonId, "tags", textNode(tagName), runId, () -> followUpBossClient.addTag(personId, tagName))`. **No local Person write.** The coordinator records on the tracker with `changedFields = Set.of("tags")` only on FUB success. |
| 2 | `src/test/java/com/fuba/automation_engine/service/workflow/steps/FubAddTagWorkflowStepTest.java` *(extend)* | Existing tests stay green. New: coordinator invoked with field=`tags` + tag value; FUB failure → no tracker record; FUB success → tracker record present. |
| 3 | `src/test/java/com/fuba/automation_engine/race/scenarios/AddTagScenariosTest.java` | <br>**C1** happy path — engine adds NEW; FUB returns success; echo `[A,B,NEW]` arrives; expect 1 `person.state_changed` event **annotated `source=ENGINE`** (because local started at `[A,B]`, payload is `[A,B,NEW]`, diff is real but tracker hit). <br>**C2** external concurrent add — engine adds NEW (t=0), external adds X at FUB (t=10), external webhook `[A,B,X]` arrives t=30ms, engine FUB PUT lands t=200ms, engine echo `[A,B,X,NEW]` arrives t=500ms. Expect: 1 real event from external (no tracker hit because engine hasn't recorded yet — recording happens after FUB success), 1 event from engine echo annotated `ENGINE`. **No phantom "NEW removed" event** because local never had an optimistic NEW. <br>**C3** FUB failure — engine adds NEW, FUB returns 500. Expect: no tracker record; no local change; step returns FAILED. No phantom event possible because local was never modified. |
| 4 | `src/test/resources/known-issues-domain-events.md` *(or update existing)* | Entry: "When Phase 4 introduces `note.created` triggers, the `fub_create_note` tracker annotation needs verification against real consumer behaviour." See [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) Phase 3 entries (added separately). |

### Order within the commit

1 → 2 → 3.

### Test gate

`./mvnw clean test` green. C1, C2, C3 deterministic (real Postgres + controlled timing).

### What 3d changes for users

Engine tag-adds no longer modify local state directly. The tag appears in local only when FUB's echo confirms. Net behaviour for end-users is unchanged; the event stream gains one annotated `person.state_changed` event per engine tag-add (versus zero in the optimistic-local model, but with no phantom-removal risk).

---

## Sub-phase 3e — Wrap `fub_create_note` (tracker-only, two-channel)

**Goal:** `fub_create_note` calls FUB first, then records on the tracker for **both** echo channels — the `notesCreated` echo (key: returned `noteId`) and the person-side `peopleUpdated` echo (key: `lastNoteAt` / whatever person fields FUB updates on note creation). Race harness D1–D4 exercise both channels.

### Why two channels

[Matrix D2](./phase-3-race-matrix.md#d-fub_create_note--entity-creation--no-local-notes-table) — FUB updates the person's `lastNoteAt` (or similar metadata) when a note is created and sends a separate `peopleUpdated` echo. Without tracker annotation on the person side, every engine note creation produces a real `person.state_changed` event that workflows might consume as if a human made the change.

### Two-channel design

- **Channel 1 — `note.created` echo:** the coordinator records on the tracker with `entityType="note"`, `entityId=<returned FUB note id>`, `changedFields=Set.of("created")`. When `NoteEmissionService` is about to emit `note.created`, it consults the tracker and annotates if hit.
- **Channel 2 — person-side `peopleUpdated` echo:** the coordinator records on the tracker with `entityType="person"`, `entityId=<sourcePersonId>`, `changedFields=Set.of("lastNoteAt", "lastActivity")` (FUB's actual field names — TBD at impl time; verify via observation in a 3e investigation step). `DomainEventEmitter` (already tracker-aware from 3a) annotates the `person.state_changed` echo if hit.

### The early-echo race (documented, not fixed)

If FUB's `notesCreated` echo arrives **before** `followUpBossClient.createNote` returns to our coordinator (i.e., the FUB POST response is slower than the FUB webhook fire), the tracker record doesn't exist when `NoteEmissionService` processes the echo. The annotation is missed; the event emits without `source=ENGINE`. Phase 4 workflows would treat this as a real `note.created`.

Empirically rare (POST responses are typically faster than webhook fires) but possible. Documented in `known-issues.md`. A content-hash key (record on tracker before the POST, keyed by hash of note body + personId; match on echo by computing same hash) would close this but adds complexity. Deferred until observation shows it firing.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubCreateNoteWorkflowStep.java` *(modify)* | Inject `EngineWriteCoordinator`. Replace direct `followUpBossClient.createNote(command)` with `coordinator.applyEntityCreateTrackedOnly(sourcePersonId, "note", runId, () -> followUpBossClient.createNote(command), (tracker, createdNote) -> { tracker.record(noteChannelRecord(createdNote)); tracker.record(personChannelRecord(sourcePersonId)); })`. The `recordSideEffects` callback decides what gets tracked. |
| 2 | `src/main/java/com/fuba/automation_engine/service/note/NoteEmissionService.java` *(modify)* | Inject `EngineWriteTracker`. Before emitting each `note.<action>` event, consult tracker with `entityType="note"`, `entityId=<noteId>`, `changedFields=Set.of("created")` (or "updated", "deleted" — match the action). On hit, mutate payload to add `"source": "ENGINE"`. |
| 3 | `src/main/java/com/fuba/automation_engine/service/event/DomainEventEmitter.java` *(no change)* | Person-side annotation already lands via 3a's emitter hook because person-side echo flows through `DomainEventEmitter.emit("person.state_changed", ...)`. The coordinator's `recordSideEffects` callback registers the person tracker entry; 3a's emitter does the rest. |
| 4 | `src/test/java/com/fuba/automation_engine/service/note/NoteEmissionServiceTest.java` *(extend)* | Tracker hit on the note channel → `note.created` payload annotated ENGINE; miss → unannotated. Mirror tests for `note.updated`, `note.deleted` (engine doesn't create those today, but the annotation logic is symmetrical). |
| 5 | `src/test/java/com/fuba/automation_engine/race/scenarios/CreateNoteScenariosTest.java` | <br>**D1** happy path — engine creates note 100; FUB returns; `notesCreated` echo arrives. Expect: 1 `note.created` event **annotated `source=ENGINE`**. <br>**D2** person-side echo — same scenario; FUB also sends `peopleUpdated` for `lastNoteAt`. Expect: 1 `person.state_changed` event **annotated `source=ENGINE`** (because coordinator recorded person channel too). <br>**D3** early-echo race — fake FUB fires `notesCreated` webhook at t=50ms but `createNote` POST returns only at t=200ms. Expect: 1 `note.created` event **NOT annotated** (tracker record didn't exist yet when echo processed). Document as the known early-echo race. <br>**D4** multiple notes — engine creates 3 notes in rapid succession. Expect: 3 `note.created` events, all annotated ENGINE; tracker has 3 records. |

### Order within the commit

1 → 2 → 4 → 5.

### Test gate

`./mvnw clean test` green. D3 is the diagnostic test for the early-echo race; if it ever passes (annotation present despite the race), the race-window assumption changed and the test needs updating.

### What 3e changes for users

`note.created` events from engine writes carry `source=ENGINE` annotation (except in the rare early-echo race). Person-side `person.state_changed` events triggered by engine note creation also carry the annotation. Phase 4 workflow filters work uniformly across all engine-write step types.

### Phase 3 complete

After 3e, every engine-originated FUB write either:
- Updates local first and emits no echo event (scalar mode happy path), **OR**
- Lets the echo flow through `PersonUpsertService`/`NoteEmissionService` annotated `source=ENGINE` (all other modes / concurrent cases)

Phase 4 can subscribe to events and filter on `change.source != "ENGINE"` to exclude engine echoes. The bad-run-rate win arrives in Phase 4.

---

## Defaults (locked at plan-finalize time — override on PR review only with explicit justification)

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
| Race harness location | `src/test/java/com/fuba/automation_engine/race/` | Sibling to `replay/` and `integration/` packages. Testcontainers Postgres + `FakeFollowUpBossClient`. |
| Race harness scenario distribution | A in 3b, B subset in 3c, C in 3d, D in 3e | Each sub-phase ships its own proof. No "all scenarios at the end" deliverable. |

## Out of scope (deferred to other phases)

| Item | Why deferred | Phase that owns it |
|---|---|---|
| `RedisEngineWriteTracker` (or persisted equivalent) | No event consumers in Phase 3; crash-window cost is observable only when Phase 4 subscribers exist | Phase 4 prerequisite |
| Content-hash tracker keying for the early-echo race on note creation | Empirically rare; cost of fixing is high; revisit if observed | Post-Phase-4 follow-up |
| `FubMutatingStep` SPI for type-enforced pattern | With 4 steps using 3 mechanisms, SPI doesn't fit; reconsider when a 5th step joins | Speculative future |
| Tracker hit/miss metrics surfaced via admin UI | INFO-level structured logs are sufficient for Phase 3; admin UI surface is a separate feature | Admin UI roadmap |
| Smarter revert ("undo delta") for accumulating fields | Research project; not justified until production data shows pain from drift-on-permanent-failure | Speculative future |
| `change.source` annotation on `call.created` events | Engine doesn't write calls today; if a future `fub_create_call` step is added, this lands with it | Not currently planned |
| Workflow validator refuses `change.source` reference for non-`person.state_changed` triggers | Phase 4 territory; lives with the validator updates there | Phase 4 |

## Risks and mid-flight detection

| Risk | Detection signal during build |
|---|---|
| Smoking-gun lock-across-FUB-call defect | Race harness A1 + A3 with `fakeFub.delayMs(500)` and N=5 concurrent webhooks → test exceeds 2s timeout if lock is held |
| Tracker TTL too short → echo arrives after eviction → unannotated phantom | INFO log `engine.write.tracker.miss` ratio > expected baseline (≥10% miss on echo-window scenarios) |
| Match-semantics false positives (engine record matches an unrelated diff) | Race harness scenarios with non-overlapping engine + external field sets; assert no false annotation |
| Early-echo race on note creation | D3 scenario passes — annotation present despite race timing |
| `REQUIRES_NEW` not actually applied (caller's outer tx swallows the inner) | `DefaultEngineWriteCoordinatorTest` REQUIRES_NEW test: roll back outer, confirm tracker + local change persist |
| Memory leak in tracker under sustained load | Periodic log: tracker entry count + TTL distribution; alert if entry count grows unbounded between eviction runs |

## Cross-references

- High-level deliverables: [`phases.md`](./phases.md) §"Phase 3"
- Architectural rationale: [`plan.md`](./plan.md) §5 (note the 2026-05-29 revert reversal)
- Race matrix driving these decisions: [`phase-3-race-matrix.md`](./phase-3-race-matrix.md)
- Phase 2 lock-discipline prior art: [`PersonUpsertService.upsertFubPerson`](../../../src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java) + [`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/fuba/automation_engine/integration/PersonUpsertConcurrencyStressTest.java) — the REQUIRES_NEW pattern this plan reuses
- Known issues tracker: [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) — Phase 3 entries added under #26, #27, #28
- Implementation log to be written at end: `phase-3-implementation.md` (does not exist yet; create when 3e ships)
