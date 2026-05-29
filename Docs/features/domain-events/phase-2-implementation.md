# Phase 2 — Implementation Log

Status: `DONE` — all 5 sub-phases shipped. 589 tests green.

This file is the decision narrative for Phase 2 per the AGENTS.md convention: it answers *"why was it built this way?"* for a future reader, not *"what files changed"* (git is the source of truth for that). Entries are added as each sub-phase lands.

See [`phase-2-plan.md`](./phase-2-plan.md) for the locked commit-level plan and [`plan.md`](./plan.md) for architectural rationale.

| Sub-phase | Commit | Theme |
|---|---|---|
| 2a | `a9dc258` | Scaffold (events table, emitter, dispatcher) |
| 2b | `41044ba` | Refactor (extract `CallUpsertService`, restructure `PersonUpsertService`, both-site lock) |
| 2c | `26e8103` | Person events emission + concurrency proof |
| 2d | `f3a8036` | Append events (`call.created`, `note.*`) |
| 2e | `9b73759` | Replay-harness asserts events; extract `NoteEmissionService` |

---

## Sub-phase 2a — Scaffold

### `DomainEventEmitter` — what it does, in plain English

Every place in the codebase that genuinely changes state — a person got reassigned, a call came in, a note was created — calls `DomainEventEmitter.emit(...)`. Think of it as the engine saying out loud: **"this just happened, and it really happened."**

The emitter does two things, in this exact order:

1. **Writes the event into the `events` table** inside the same database transaction as the state change that caused it. So either *both* the state change and the event are saved, or *neither* is. There's no universe where the person's `assignedUserId` updates but the `person.state_changed` event goes missing — or vice versa.
2. **Tells the rest of the engine about it, but only after the database confirms the change is locked in.** It posts a "when this transaction successfully commits, run the dispatcher" note. If the transaction rolls back, the note is thrown away and nobody hears about the event — because it didn't really happen.

### Benefits

| Benefit | Plain English |
|---|---|
| **Atomicity** | The event row and the state change are inseparable. You can't get one without the other. No more "the database says X but our audit log says Y." |
| **No phantom events on rollback** | If the upsert fails halfway through and rolls back, no listener ever hears about a change that never happened. Eliminates a whole class of ghost workflow runs. |
| **No locked-row contention with listeners** | Listeners (Phase 4's workflow trigger) run *after* the transaction commits, so they can take their time — they're not holding the `persons` row hostage while they decide what to do. |
| **Loud failures, not silent ones** | If a future developer adds a new caller and forgets to mark it `@Transactional`, the code throws an exception the first time it runs. The bug surfaces in dev, not in production three months later as a missing audit trail. The `MANDATORY` propagation guard is what enforces this. |
| **One central choke point** | Every domain event in the system flows through this one method. Want to add metrics, tracing, an outbox poller, schema validation? One place to change. |

### The enabling tech — one-liner

> Spring's **`TransactionSynchronizationManager`** lets you attach a callback that fires only *after* the current database transaction commits — so the emitter can record the event inside the transaction and notify listeners outside it, atomically.

Without this single piece of framework machinery, you'd have to choose between:
- **Notify inside the transaction** — slow, locks held too long, listener failures roll back the state change
- **Notify outside the transaction** — fast, but you can lose the event if the process dies between commit and notify

The synchronization hook gives you both halves of the deal: the event row is durable before any listener runs, and the listener runs outside the lock-holding window of the upsert.

### Naming caution

Three related-but-distinct concepts live in this codebase. Easy to confuse on first read:

| Class | What it is |
|---|---|
| `WebhookEventEntity` (table `webhook_events`) | The raw inbound webhook from FUB. Pre-existing. |
| `EventEntity` (table `events`) | The persisted domain event the engine emits when state actually changes or an append happens. NEW in Phase 2. |
| `DomainEvent` (Java record) | The in-memory shape handed to listeners after commit. Same data as `EventEntity`, but a plain value type so listeners don't depend on JPA. |

`EventEntity.sourceEventId` is the FK linking each domain event back to the webhook that caused it (null for engine-synthesized events).

---

## Sub-phase 2b — Refactor (zero behaviour change)

### Why a pure refactor sub-phase exists at all

The original 4-sub-phase split layered emission onto the unrefactored services and surfaced four real defects under fresh-eyes review (`MANDATORY`-propagation crash on the calls/notes path; lock-hole on the brand-new-person insert race; harness `min` assertions that silently accept a broken collapse; implicit `oldDetails` capture that can be overwritten before read). The root cause was structural — `PersonUpsertService` and `WebhookEventProcessorService` were sized for "webhook → fetch → save," not for "atomic state-change-with-event." Doing the restructure first means 2c lands on a shape where those defects are **impossible to write**, not just absent.

### Capture-old / apply-new shape

`upsertFubPerson` now captures `JsonNode oldDetails = entity.getPersonDetails()` as a named local **before any mutation**, in both the existing-row branch and the `DataIntegrityViolationException` recovery branch. 2c reads this local to compute the diff. The capture-before-mutate shape makes "diff sees the post-save value" structurally impossible — a future reader cannot accidentally reorder the lines such that the bug returns.

### Both-site `findBy…ForUpdate`

The collapse claim ("N webhooks → 1 event") fails for brand-new persons if only the primary find site uses the pessimistic lock. When N parallel inserts race, the unique-constraint losers each go through the `DIVE`-recovery re-read; if that re-read is non-locking, every loser reads the winner's row independently and each emits its own event. The recovery path is the *only* path that runs during the race, so it must take the lock too. Both sites use `findBySourceSystemAndSourcePersonIdForUpdate` (explicit `@Query` JPQL — Spring Data is finicky about applying `@Lock` to plain derived methods).

### Surgical `CallUpsertService` extraction

`CallUpsertService` owns one method: `persistCallFacts`. The body lifted verbatim from `WebhookEventProcessorService` — only the save and orphan-person warn log moved. Retry, the decision engine, and task creation stay in `WebhookEventProcessorService.processCall`. The narrow extraction is what made the call-path `@Transactional` (the unit of work the emitter requires) without bundling an unrelated orchestration refactor. 2d injects `DomainEventEmitter` into this service; the boundary established here is what 2d consumes.

### Verification of "zero behaviour change"

Existing `PersonUpsertServiceTest` cases switched mocks from `findBy…` to `findBy…ForUpdate` with **identical assertions** and stayed green — the cleanest possible signal that the restructure preserved behaviour. New `CallUpsertServiceTest` (13 cruel tests) pins the lifted persistence behaviour: all seven call-fact field mappings, orphan-warn semantics with full log-message context (`eventId` + `callId` + `sourcePersonId`), `personId=0L` treated as a real id, `save` exception propagation, and so on. Tests: 532 → 545 green.

---

## Sub-phase 2c — Person events emission

### Three real branches, one structural rule

The emission paths line up with the capture-old / apply-new shape from 2b:

| Branch | Emit | `previousState` |
|---|---|---|
| Brand-new row (no existing OR `DIVE`-recovery winner) | `person.created` with `{ current: <full snapshot> }` | `null` |
| Existing + non-empty diff | `person.state_changed` with `{ changed_fields, previous, current }` (only changed fields) | `oldDetails` |
| Empty diff (echo / no-op) | nothing | untouched |

Echoes touching `previousState` would be the wrong contract — `previous_state` is "what state did this row hold last time we observed a meaningful change," not "what state did we have one webhook ago." An echo is an observation that nothing meaningful happened.

### `PersonDiffComputer` — why these strategies

Per-field strategies matter because the **wrong strategy emits phantom events on identical data**.

- **Scalars** — `JsonNode.equals`. Straight-line.
- **`tags`** — set-of-string equality. FUB does not guarantee tag order; client reordering is not a state change.
- **`phones` / `emails`** — set-of-`JsonNode` equality using Jackson's deep structural equals on each element. Order-independent at the element level.
- **Missing field ≡ JSON null ≡ empty array** — FUB beginning to send `{}` or `[]` for a previously-absent field must not fire a phantom event. The 17-case `PersonDiffComputerTest` caught a real bug here on first run: the collapse only worked when both sides were `null`. Fixed.

### The Hibernate session corruption surfaced (and fixed) here

The `DIVE`-recovery path predates Phase 2 — it has existed since the leads era. The concurrency stress test caught a latent bug in it on first run: when the failed `INSERT` rolls back inside the outer `@Transactional`, Hibernate's session is left with a stale entity reference without an ID. Subsequent operations on the same `EntityManager` fail with `"Entry for instance of PersonEntity has a null identifier."` Mocked-`save()` unit tests never caught this — they sidestep real Hibernate flush entirely.

Fix: the `INSERT` runs in its own `REQUIRES_NEW` tx via `TransactionTemplate` so `DIVE` rolls back only the inner tx, leaving the outer's `EntityManager` clean for the recovery `findBy…ForUpdate`. Emission of `person.created` happens inside the inner tx, atomic with the `persons` row. `save → saveAndFlush` in the insert path so the `DIVE` fires inside the inner tx, not at outer-tx commit time.

This is the same lesson 2e re-learns: framework-level mechanics (proxy boundaries, session flush, lock semantics) are exactly what mocks bypass.

### The collapse proof

`PersonUpsertConcurrencyStressTest` fires N=10 parallel `upsertFubPerson` calls for the same `sourcePersonId` against real Postgres (Testcontainers), `@RepeatedTest(3)`. Scenario A (row exists, identical payload from all 10): asserts **exactly 1** `person.state_changed`. Scenario B (row doesn't exist, identical payload from all 10): asserts **exactly 1** `person.created`, **0** `state_changed` (losers' `DIVE`-recovery sees identical data, empty diff, no emit). Hikari pool bumped to 30 — each thread holds the outer + briefly the inner connection; the default 10 deadlocks. **Policy**: if this ever flakes, the collapse invariant is broken; investigate before merge — do not rerun until green.

Tests: 545 → 574 green (+17 diff matrix, +6 emission, +6 concurrency).

---

## Sub-phase 2d — Append events

### `call.created` — payload built manually, not via `valueToTree`

`jackson-datatype-jsr310` is a transitive dep that's not on the compile classpath. Directly serializing `OffsetDateTime` via `valueToTree` would compile but fail at runtime once the transitive dep dropped out — a fragile contract. The manual payload builder produces an ISO-8601 string for `createdAt` (matching what `JavaTimeModule` would emit) and adds no new project dep, no transitive classpath assumption. YAGNI-clean for what we actually need.

`source_event_id` on the emitted row is the **webhook** id, not the FUB call id. The FUB call id is `entity_id`. Mixing them would corrupt the lineage Phase 4 walks to attribute runs to triggering webhooks.

### Note webhooks — no `notes` table, no `NoteUpsertService` (yet)

`NormalizedDomain` gains `NOTE`; `NormalizedAction` gains `DELETED`. The V21 CHECK constraint on `webhook_events` already permits `NOTE` (added in Pre-Phase-2 for exactly this). `WebhookEventProcessorService` gets a `case NOTE → processNoteDomainEvent` branch — package-private `@Transactional` private method that emits `note.created` / `note.updated` / `note.deleted` per `event.normalizedAction()`. The `@Transactional` annotation is what makes the emitter's `MANDATORY` propagation pass.

There is no `notes` table and no FUB body fetch. Workflows that need note body content can fetch on demand from `/v1/notes/{id}` when a real consumer arrives. Building the table now would be ceremony for nobody.

Tests: 574 → 586 green (+3 parser, +3 call emission, +6 note branch).

### Why this 2d-as-shipped does NOT survive 2e — see below

The package-private `@Transactional processNoteDomainEvent` ships here and 2e immediately extracts it. The reason is Spring proxy mechanics, not a planning miss — 2d's tests passed locally with mocked emitter; the harness in 2e caught the proxy issue. See 2e for the full story.

---

## Sub-phase 2e — Replay-harness assertions + `NoteEmissionService` extraction

### Honest contract on the harness assertions

`ReplayFixture.Expected` gains three fields: `expectedCreatedEventsForPerson` (exact), `expectedStateChangeEventsForPerson` (exact — catches both undershoot and overshoot), and `minAppendEvents` (min by `event_kind` — no uniqueness claim applies to append events). The drain loop checks at-least to terminate promptly; exact-count overshoot is asserted **post-drain** so a fast-failing overshoot doesn't busy-wait.

All four person fixtures (20123, 20207, 20231, 20235) plus a new synthesized note fixture assert exact counts. The honest contract: `expectedStateChangeEventsForPerson = 0` for all four because the harness mock returns the **same** snapshot for every `getPersonRawById` call — every subsequent webhook produces an empty diff and no emission. That's a **stronger** collapse claim than "1 state_changed": *"N webhooks of identical data → 1 created, 0 state_changed."* Per-payload-difference scenarios are covered structurally by `PersonUpsertConcurrencyStressTest` against real Postgres; the harness's job is end-to-end replay against recorded sequences, not lock-semantics proof.

### The deviation: `NoteEmissionService` (not `NoteUpsertService`)

`phase-2-plan.md` §"Defaults" says: *"Inline `@Transactional processNoteDomainEvent` on `WebhookEventProcessorService`. No `NoteUpsertService` — there's no state to own."*

2e ships a `NoteEmissionService` anyway. **The deviation is real and worth recording so a future reader doesn't think it's accidental.**

#### What the harness caught that unit tests missed

The 2d implementation of `processNoteDomainEvent` was package-private `@Transactional` **inside** `WebhookEventProcessorService`, called via `this.processNoteDomainEvent(event)` from `process()`. Spring's proxy boundary is bypassed on `this.` self-invocation, so `@Transactional` **never applied**, `DomainEventEmitter`'s `MANDATORY` guard threw `IllegalTransactionStateException` on every notes webhook, `AsyncWebhookDispatcher` swallowed the exception, and the harness saw 0 note events.

Unit tests with a mocked emitter missed this entirely — they sidestep the framework-level proxy mechanics, exactly the way mocked `save()` sidesteps Hibernate flush in the 2c story. Same lesson, second instance.

#### Why a separate `@Component`, named `*EmissionService` not `*UpsertService`

The fix is to put the `@Transactional` method on a separate `@Component` so the call from `WebhookEventProcessorService.process()` goes through Spring's proxy. That makes `@Transactional` apply and `MANDATORY` pass.

The naming is deliberate. `*UpsertService` belongs where there is persistence to own (`PersonUpsertService` owns `persons`; `CallUpsertService` owns `processed_calls`). There is no `notes` table — `NoteEmissionService` owns no state, only the emission semantics for note webhooks. The name reflects what the component does: emit, not upsert. The structural symmetry across all three emission paths — every domain emission flows through a proxy-boundaried `@Component` — is the point. (`*EmissionService` when there is no persistence, `*UpsertService` when there is.)

`NoteEmissionServiceTest` (7 tests): all three actions map to right `event_kind`; missing `resourceIds` emits nothing; multiple `resourceIds` → one event per note; payload passes through by reference; unmapped action skips emission. `WebhookEventProcessorServiceTest`'s 6 note emission-semantics tests moved here (they belong where the emission lives now); a single delegation check stays in `WebhookEventProcessorServiceTest`.

### The two latent bugs Phase 2 surfaced are the same class

| Stage | Sidestepped by mocks | Surfaced by |
|---|---|---|
| 2c — Hibernate session corruption in `DIVE`-recovery | mocked `save()` never flushes | `PersonUpsertConcurrencyStressTest` against real Postgres |
| 2e — `@Transactional` self-invocation bypassing the proxy | mocked emitter never executes the propagation guard | `ReplayHarnessTest` driving the real Spring container |

Both lived undetected behind mocks for an unknown duration before this feature touched the area. Both are the kind of bug that demands real-wiring verification — Testcontainers + the replay harness are how this feature catches them.

### Phase 2 complete

Tests: 586 → 589 green (+9 from `NoteEmissionServiceTest` + harness fixture + harness inject; -6 from the collapsed note tests in `WebhookEventProcessorServiceTest`).

The collapse claim is verified at three levels — unit (`PersonDiffComputerTest`), concurrency (`PersonUpsertConcurrencyStressTest` against real Postgres), end-to-end (`ReplayHarnessTest` against recorded incidents). Substrate is ready for Phase 3.

### Repo decisions impact

`No` — feature-internal. The proxy / `@Transactional` / `*EmissionService` vs `*UpsertService` distinction is a pattern this feature establishes, but it's a local naming convention, not a repo-wide architectural rule worth promoting to `Docs/repo-decisions/`. If a future feature replicates the pattern (proxy-boundaried emission service, no state to own), then promote.
