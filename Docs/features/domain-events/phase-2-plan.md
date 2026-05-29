# Phase 2 — Implementation Plan (step-by-step)

Status: `DONE` — all 5 sub-phases shipped on `feature/domain-events` (commits `a9dc258`, `41044ba`, `26e8103`, `f3a8036`, `9b73759`). Decision narrative and deviations recorded in [`phase-2-implementation.md`](./phase-2-implementation.md).

Companion to [`phases.md`](./phases.md) §"Phase 2 — Domain events table + diff machinery". `phases.md` is the canonical statement of **what** Phase 2 delivers and **why**; this file is the **commit-level order of operations** — concrete files, sequencing, test gates, defaults.

> **Plan-lock changelog (2026-05-28):** The original 4-sub-phase split layered emission onto unrefactored services and produced four real defects under fresh-eyes review (MANDATORY-propagation crash on the call/note path; lock-hole on the brand-new-person insert race; replay-harness `min` assertions that silently accept a broken collapse; implicit `oldDetails` capture that can be overwritten before read). Root cause: `PersonUpsertService` and `WebhookEventProcessorService` were sized for "webhook → fetch → save," not for "atomic state-change-with-event." This revision inserts a pure **refactor sub-phase 2b** between the scaffold and emission — extracts `CallUpsertService.persistCallFacts`, restructures `PersonUpsertService` to a "capture-old / apply-new" shape, and adds `findBy…ForUpdate` at **both** read sites — so 2c/2d/2e land on a structure where the four defects are impossible to write. **This is the final plan revision.** Anything that surfaces during build goes into `phase-2-implementation.md`, not back into this plan.

## Scope reminder (one paragraph)

By the end of Phase 2, every webhook the engine processes either produces exactly one typed row in a new `events` table (`person.created`, `person.state_changed`, `call.created`, `note.created/updated/deleted`) or is silently collapsed (echoes, FUB-burst dupes, no-op edits). The collapse is **structurally guaranteed under concurrency** (per-person pessimistic write lock applied at both the primary find site and the unique-constraint-race recovery re-read), not just observed in lucky test runs. Events are inserted **inside the caller's transaction**, atomic with the state change, and the in-memory dispatcher fires **only after that transaction commits**. Every emission site is `@Transactional` by construction — `PersonUpsertService.upsertFubPerson`, a new `CallUpsertService.persistCallFacts`, and a newly-`@Transactional` `WebhookEventProcessorService.processNoteDomainEvent` — so the emitter's `MANDATORY` propagation guard fails loudly when violated, never silently. **No listeners are registered in Phase 2** — `WorkflowTriggerRouter.route(event)` continues to run on the existing path unchanged. The substrate Phase 4 needs is in place; Phase 4 wires consumers.

## Sub-phase split — 5 reviewable commits

| Sub-phase | Theme | Approx files / LOC | Risk |
|---|---|---|---|
| **2a** | Scaffold (events table + emitter + dispatcher; no callers) | ~10 / ~250 | Low — no behaviour change |
| **2b** | **Refactor only**: extract `CallUpsertService.persistCallFacts`; restructure `PersonUpsertService` to capture-old/apply-new shape; add `findBy…ForUpdate` and use at both read sites | ~6 / ~300 | Medium — load-bearing services touched, but **no behaviour change**; 528 tests stay green |
| **2c** | Person events emission (lands on the clean shape from 2b) | ~6 / ~400 | **High** — the load-bearing collapse claim is proven here |
| **2d** | Append events: `call.created` from `CallUpsertService`; `note.*` from `@Transactional processNoteDomainEvent` | ~5 / ~200 | Low |
| **2e** | Replay-harness assertions (**exact** counts for collapse fixtures, `min` for append) | ~6 / ~150 | Low |

Each commit ships with a green `./mvnw clean test` (528 tests pre-Phase-2 baseline; new tests added per sub-phase).

---

## Sub-phase 2a — Scaffold

**Goal:** the `events` table exists, the emitter inserts in-tx and dispatches after commit, the dispatcher interface + an empty in-memory impl are registered. Nothing calls any of it yet.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/resources/db/migration/V22__create_events_table.sql` | Create `events` table per [`plan.md`](./plan.md) §"The `events` table"; two indexes (`(event_kind, created_at DESC)`, `(entity_type, entity_id, created_at DESC)`); FK to `webhook_events(id)` `ON DELETE SET NULL` |
| 2 | `src/main/java/com/fuba/automation_engine/persistence/entity/EventEntity.java` | JPA mapping; payload via `@JdbcTypeCode(SqlTypes.JSON)` |
| 3 | `src/main/java/com/fuba/automation_engine/persistence/repository/EventRepository.java` | `extends JpaRepository<EventEntity, Long>` |
| 4 | `src/main/java/com/fuba/automation_engine/service/event/DomainEvent.java` | Record: `eventKind`, `sourceSystem`, `sourceEventId`, `entityType`, `entityId`, `payload` (`JsonNode`) |
| 5 | `src/main/java/com/fuba/automation_engine/service/event/DomainEventListener.java` | Interface — `void onEvent(DomainEvent)` |
| 6 | `src/main/java/com/fuba/automation_engine/service/event/DomainEventDispatcher.java` | Interface — `void dispatch(DomainEvent)` |
| 7 | `src/main/java/com/fuba/automation_engine/service/event/InMemoryDomainEventDispatcher.java` | Impl — **constructor-injected** `List<DomainEventListener>` (Spring auto-wires all `@Component` listeners), modeled on the existing `WebhookDispatcher` pattern (no Spring `ApplicationEventPublisher`). Phase 4 adds a listener by creating a `@Component` implementing the interface — no registration code to touch |
| 8 | `src/main/java/com/fuba/automation_engine/service/event/DomainEventEmitter.java` | `@Transactional(propagation = MANDATORY)` `emit(...)`: INSERT row via `EventRepository`, then `TransactionSynchronizationManager.registerSynchronization(afterCommit → dispatcher.dispatch(event))` |
| 9 | `src/test/java/com/fuba/automation_engine/integration/EventsTableMigrationPostgresRegressionTest.java` | Testcontainers: column shape matches V22 (names + types), both indexes present, FK present with `ON DELETE SET NULL` |
| 10 | `src/test/java/com/fuba/automation_engine/service/event/DomainEventEmitterTest.java` | (a) commit → dispatch fires exactly once, **after** the row is visible to a fresh `Transactional` read; (b) rollback → dispatch never fires; (c) called outside any tx → throws `IllegalTransactionStateException` (this is the regression net that protects the `MANDATORY` invariant going forward) |

### Order within the commit

1 → (2, 3 in parallel) → 4 → (5, 6 in parallel) → 7 → 8 → (9, 10 in parallel).

### Test gate

`./mvnw clean test` green; V22 applies on a fresh Testcontainers Postgres; the new emitter test passes.

### What 2a does NOT change

No caller of `DomainEventEmitter` exists yet. `PersonUpsertService`, `WebhookEventProcessorService`, and `WorkflowTriggerRouter` are untouched. Production behaviour identical.

---

## Sub-phase 2b — Refactor (no behaviour change)

**Goal:** restructure the two load-bearing services so 2c/2d can wire emission cleanly. **Zero behaviour change.** All 528 existing tests stay green; new refactor-confirming tests are added.

Why this sub-phase exists: the current shape of `PersonUpsertService.upsertFubPerson` ("find → mutate entity in place → save") and `WebhookEventProcessorService.processCall` (no enclosing `@Transactional`) makes it impossible to land 2c's emission code without either (a) the emitter's `MANDATORY` guard tripping at first webhook (calls/notes path) or (b) silently overwriting `oldDetails` before the diff reads it. Fixing those at emission time spreads concurrency/transactional concerns across the orchestration layer. Fixing them as a pure refactor first contains the change.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/persistence/repository/PersonRepository.java` | Add `findBySourceSystemAndSourcePersonIdForUpdate(String sourceSystem, String sourcePersonId)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` + explicit `@Query("select p from PersonEntity p where p.sourceSystem = :sourceSystem and p.sourcePersonId = :sourcePersonId")` |
| 2 | `src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java` | Restructure `upsertFubPerson` to a **capture-old / apply-new** shape: <br>(a) switch the primary finder call to `findBy…ForUpdate`; <br>(b) **also** switch the `DataIntegrityViolationException` recovery re-read to `findBy…ForUpdate` — this closes the brand-new-row insert-race window; <br>(c) for the existing-row branch, capture `JsonNode oldDetails = existing.getPersonDetails()` as a **named local before any mutation**, then build `newDetails` as a separate value (`buildSnapshot(personPayload)` already returns this), then apply the in-memory mutations in one block (`setPersonDetails(newDetails)`, `setKind`, `setUpdatedAt`, `setLastSyncedAt`) immediately before `save`; <br>(d) no event emission yet — `oldDetails` is captured but unused in 2b (2c reads it). The capture-before-mutate shape is what makes 2c's diff correct by construction |
| 3 | `src/main/java/com/fuba/automation_engine/service/call/CallUpsertService.java` (new) | New service in a new package `service/call/`. Owns one method: `@Transactional public void persistCallFacts(NormalizedWebhookEvent event, ProcessedCallEntity entity, CallDetails callDetails)`. Body is **lifted verbatim** from `WebhookEventProcessorService.persistCallFacts` (lines ~268–290) — sets fields on `entity`, calls `processedCallRepository.save(entity)`, performs the orphan-person warn-log. **Surgical extraction**: retry/decision-engine logic stays in `WebhookEventProcessorService.processCall` |
| 4 | `src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java` | (a) inject `CallUpsertService`; (b) replace the inline `persistCallFacts(event, entity, callDetails)` call inside `processCall` with `callUpsertService.persistCallFacts(event, entity, callDetails)`; (c) **delete** the now-orphaned private `persistCallFacts` method and the `personRepository` field if it's only used there (verify grep first) |
| 5 | `src/test/java/com/fuba/automation_engine/service/call/CallUpsertServiceTest.java` (new) | Verifies the lifted persistence behaviour: saves the entity with correct fields; emits the orphan-person warn log when the person row is missing; runs inside a transaction (assert via `TransactionSynchronizationManager.isActualTransactionActive()` inside the method under test, or by checking `MANDATORY`-propagation emit would succeed when called from inside it — the latter is wired in 2d) |
| 6 | `src/test/java/com/fuba/automation_engine/service/person/PersonUpsertServiceTest.java` *(extend existing)* | New cases: brand-new + concurrent insert race → recovery path also takes the lock (assert by `TransactionSynchronizationManager` + integration test in 2c); capture-old / apply-new shape preserves all existing upsert behaviour byte-for-byte (the existing tests already enforce this — no new behaviour assertions needed beyond "the refactor didn't break anything"); existing test for `mapStageToKind` unaffected |

### Order within the commit

1 (lock method on repo) → 2 (PersonUpsertService restructure, run existing tests to confirm zero diff) → 3 (extract CallUpsertService) → 4 (rewire WebhookEventProcessorService) → 5 + 6 (refactor-confirming tests).

### Test gate

`./mvnw clean test` green. All 528 existing tests pass unchanged. New `CallUpsertServiceTest` passes.

### What 2b does NOT change

No `events` row is ever written (no caller of `DomainEventEmitter` yet). No event_kind logic. `WorkflowTriggerRouter.route(event)` is untouched. Production-observable behaviour identical to before 2b.

---

## Sub-phase 2c — Person events emission

**Goal:** `PersonUpsertService` emits `person.created` / `person.state_changed` under the per-person row lock established in 2b. The collapse claim is proven by a deterministic concurrency stress test.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/person/PersonDiffComputer.java` (new) | Per-field strategy: scalars via `JsonNode.equals`; `tags` → sort both then equals; `phones`/`emails` → `Set<JsonNode>` equals (order-independent at element level). Returns `DiffResult(List<String> changedFields, ObjectNode previous, ObjectNode current)` containing **only changed fields** in `previous`/`current` |
| 2 | `src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java` | Inject `DomainEventEmitter` + `PersonDiffComputer` + (already-present) `ObjectMapper`. Wire emission inside the existing `@Transactional`, on the clean structure from 2b: <br>• brand-new row (either the no-existing branch OR the DIVE-recovery branch — both must emit consistently) → emit `person.created` with payload `{ current: <full snapshot> }`, set `previousState = null`; <br>• existing + non-empty diff → set `previousState = oldDetails` (the local captured in 2b), emit `person.state_changed` with `{ changed_fields, previous, current }`; <br>• empty diff → emit nothing, `previousState` untouched |
| 3 | `src/test/java/com/fuba/automation_engine/service/person/PersonDiffComputerTest.java` (new) | Matrix: scalar change; scalar no-op; tag add; tag reorder (no-op); tag remove; phone add; phone reorder (no-op); phone remove; email change; mixed multi-field change |
| 4 | `src/test/java/com/fuba/automation_engine/service/person/PersonUpsertServiceTest.java` *(extend)* | New cases: brand-new → 1 `person.created` row + correct payload + `previousState` null; update with diff → 1 `person.state_changed` row, payload contains only changed fields in `previous`/`current`, `previousState` = old details; echo upsert (identical JSON in) → 0 events, `previousState` untouched; `mapStageToKind` runs unaffected by emission |
| 5 | `src/test/java/com/fuba/automation_engine/integration/PersonUpsertConcurrencyStressTest.java` (new) | Testcontainers Postgres + `CountDownLatch`-released N=10 parallel `upsertFubPerson` calls for the **same** `sourcePersonId`. Two scenarios: (a) row already exists → assert **exactly 1** `person.state_changed` row in `events` (the row lock from 2b serializes); (b) row does not exist → assert **exactly 1** `person.created` row (the DIVE recovery path now takes the lock too — closes the brand-new-row race). Run twice in the test (`@RepeatedTest(3)`) to catch nondeterminism |

### Order within the commit

1 + 3 first (diff computer in isolation) → 2 (wiring on the 2b structure) → 4 (emission unit tests) → 5 (concurrency proof).

### Test gate

`./mvnw clean test` green + concurrency stress test green on `@RepeatedTest(3)`. If 5 flakes on any run, **investigate and fix before merging** — flakiness here means the collapse claim is not actually held.

### What 2c changes for users

The `events` table starts getting `person.created` / `person.state_changed` rows. Nothing reads them. `WorkflowTriggerRouter.route(event)` still runs unchanged.

---

## Sub-phase 2d — Append events

**Goal:** `call.created` and `note.created/updated/deleted` flow through the same emitter, each from inside its own `@Transactional` boundary.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/fuba/automation_engine/service/webhook/parse/FubWebhookParser.java` | Map `notesCreated` → `(NormalizedDomain.NOTE, NormalizedAction.CREATED)`; `notesUpdated` → `UPDATED`; `notesDeleted` → `DELETED` |
| 2 | `src/main/java/com/fuba/automation_engine/service/webhook/support/StaticWebhookEventSupportResolver.java` | Register `notesCreated/Updated/Deleted` as supported, with consistent descriptions |
| 3 | `src/main/java/com/fuba/automation_engine/service/call/CallUpsertService.java` | Inject `DomainEventEmitter`. After `processedCallRepository.save(entity)`, emit `call.created` with the full FUB call payload. `source_event_id` is the **webhook** id (`event.webhookEventId()`), not the FUB call id — that's already the entity_id |
| 4 | `src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java` | (a) add `case NOTE -> processNoteDomainEvent(event)` to the `process()` switch; (b) new **`@Transactional` private** method `processNoteDomainEvent(NormalizedWebhookEvent event)` — emits `note.created` / `note.updated` / `note.deleted` per `event.normalizedAction()`, payload = the webhook payload. The `@Transactional` annotation is what makes the emitter's `MANDATORY` guard pass. **No `notes` table** — body content not fetched; deferred to a future workflow that needs it. No `NoteUpsertService` — there's no state to own |
| 5 | `src/test/java/com/fuba/automation_engine/service/FubWebhookParserNormalizedContractTest.java` *(extend)* | Note event types parse to `NormalizedDomain.NOTE` + the right `NormalizedAction` |
| 6 | `src/test/java/com/fuba/automation_engine/service/call/CallUpsertServiceTest.java` *(extend)* | A `callsCreated` webhook → 1 `call.created` row with correct payload + `source_event_id` set |
| 7 | `src/test/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorServiceTest.java` *(extend)* | `notesCreated/Updated/Deleted` → 1 matching `note.*` row each; emission happens inside the `processNoteDomainEvent` transaction (assert by rolling the tx back in a test and confirming no event row appears) |

### Order within the commit

1 + 5 → 2 → 3 + 6 (call.created on the extracted service) → 4 + 7 (note branch on `WebhookEventProcessorService`).

### Test gate

`./mvnw clean test` green.

### What 2d changes for users

`events` table now also receives `call.created` and `note.*` rows. Still no consumers.

---

## Sub-phase 2e — Replay-harness assertions

**Goal:** turn the harness from "fires webhooks and asserts workflow_runs" into "fires webhooks and asserts the events table" so the collapse + append invariants are verified end-to-end on the real recorded incidents. **Exact counts** for collapse claims; `min` only where uniqueness isn't an invariant.

### Files

| # | Path | Purpose |
|---|---|---|
| 1 | `src/test/java/com/fuba/automation_engine/replay/ReplayFixture.java` | Add to `Expected`: <br>• `Map<String,Integer> expectedStateChangeEventsForPerson` (**exact** count per `sourcePersonId`) <br>• `Map<String,Integer> expectedCreatedEventsForPerson` (**exact**) <br>• `Map<String,Integer> minAppendEvents` (`event_kind → min count`, e.g. `{"call.created": 1, "note.created": 2}` — `min` is correct here because no uniqueness claim applies to append events) |
| 2 | `src/test/java/com/fuba/automation_engine/replay/ReplayHarnessTest.java` | Inject `EventRepository`; assertion methods that count events by kind + entity; plug into `expectationsMet` poll and `assertExpectations`. The `expected*` assertions fail on **either** under-count (collapse missed) **or** over-count (collapse broke) — that's the whole point |
| 3 | `src/test/resources/replay-fixtures/person-20235-fub-burst-2026-05-12.json` | Add `expected.expectedStateChangeEventsForPerson: {"20235": 1}` — exact-1 proves the FUB-burst collapse |
| 4 | `src/test/resources/replay-fixtures/person-20231-fub-burst-2026-05-12.json` | Add `expected.expectedStateChangeEventsForPerson: {"20231": 1}` — exact-1 on the 4-webhook burst |
| 5 | `src/test/resources/replay-fixtures/person-20123-echo-cascade-2026-05-08.json` | Add `expected.expectedStateChangeEventsForPerson: {"20123": 2}` (1 real assignment + 1 phantom from the engine echo) with `notes` field saying *"Phase 3's local-state-first writes will flip this to 1"*. The exact-2 is honest about Phase 2's behaviour today — flipping to exact-1 is Phase 3's assertion change |
| 6 | `src/test/resources/replay-fixtures/synthesized-note-created.json` (new) | One `notesCreated` webhook → assert `expected.minAppendEvents: {"note.created": 1}` |

### Order within the commit

1 + 2 → 3, 4, 5 → 6.

### Test gate

`./mvnw clean test` green. Replay harness reports the collapse and append invariants holding on every recorded incident.

### What 2e changes for users

Verification net is structural, not lucky. Phase 2 complete.

---

## Defaults (locked at plan-finalize time — override on PR review only with explicit justification)

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

## Out of scope (deferred to other phases)

| Item | Why deferred | Phase that owns it |
|---|---|---|
| `dispatched` flag + durable outbox poller | No consumers in Phase 2; app not deployed; cleanly additive later | Phase 4 decision |
| `change.source = "ENGINE"` annotation on echoes | Needs `EngineWriteTracker` | Phase 3 |
| Any change to `WorkflowTriggerRouter.route()` | Old path runs unchanged until Phase 4's hard cut | Phase 4 |
| `notes` table + on-demand FUB note body fetch | Workflows can fetch on demand when a real consumer needs body content | Follow-up after Phase 4 |
| Full extraction of `processCall` orchestration into `CallUpsertService` (retry / decision engine / task creation) | Phase 2 only needs the persistence layer to be transactional; bundling the orchestration refactor would bloat the diff | Follow-up after Phase 4 |

## Cross-references

- High-level deliverables: [`phases.md`](./phases.md) §"Phase 2 — Domain events table + diff machinery"
- Architectural rationale + invariants: [`plan.md`](./plan.md) §"The `events` table", §"Per-person serialization of the upsert", §"Emit in-transaction, dispatch after commit"
- Driving evidence: [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md)
- Phase 0 harness this builds on: [`phase-0-implementation.md`](./phase-0-implementation.md)
- Implementation log to be written at end: `phase-2-implementation.md` (does not exist yet; create when 2e ships)
