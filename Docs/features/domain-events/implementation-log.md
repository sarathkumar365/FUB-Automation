# domain-events — implementation log

Append-only history. One dated section per completed phase, moved verbatim from the original `phase-<n>-implementation.md` files during the 2026-06-03 docs consolidation. **Do not rewrite an existing section** to chase a later decision — correct course in a new section or in [`plan.md`](./plan.md). Current state and the phase tracker live in [`README.md`](./README.md).


---

## Phase 0 — Replay harness — implementation log

Status: `DONE` — harness framework + one synthesized fixture + four real DB-extracted fixtures (lead 20123, 20207, 20231, 20235) all passing in 132s wall clock.

### Goal

Build a test harness that drives recorded sequences of FUB webhooks through the live engine and asserts on downstream effects (workflow runs, FUB calls, persisted rows). Without this, Phases 2–5 are nearly impossible to validate — every bug in the field observations is about *interactions in time*, which unit tests miss by construction.

The harness must reproduce the documented bad behavior of the 05-08/05-11/05-12 incidents deterministically. The same fixtures will later validate that each phase's win actually lands (e.g. after Phase 2, the FUB-burst fixture should produce one workflow run instead of three).

### What landed

- `src/test/java/com/fuba/automation_engine/replay/`
  - `ReplayFixture.java` — record types for fixture shape
  - `ReplayFixtureLoader.java` — classpath JSON loader
  - `ReplayHarnessFollowUpBossClient.java` — test FUB client (scriptable reads, recorded writes)
  - `ReplayHarnessTest.java` — `@SpringBootTest` driving fixtures via `@TestFactory` dynamic tests
  - `README.md` — comprehensive harness documentation (what/why/how/extension/limits)
- `src/test/resources/replay-fixtures/`
  - `README.md` — fixture format spec + naming conventions
  - `synthesized-fub-burst-3-webhooks.json` — harness fidelity validation
  - `lead-20235-fub-burst-2026-05-12.json` — real DB extract; 3 peopleUpdated webhooks in 8s
  - `lead-20231-fub-burst-2026-05-12.json` — real DB extract; 4 peopleUpdated webhooks in 16s
  - `lead-20207-triple-run-2026-05-11.json` — real DB extract; three workflow runs over 51 min (truncated to 60s for replay)
  - `lead-20123-echo-cascade-2026-05-08.json` — real DB extract; echo cascade incident (truncated to 120s)
- `scripts/build-replay-fixture.sh` — psql-based generator that turns `(source_lead_id, fixture_name, description, [max_delta_ms])` into a fixture JSON file, sourced from local dev DB

### Verification

- `./mvnw test -Dtest=ReplayHarnessTest` passes (11.87s wall clock — 3s context boot + 12s real-time replay of an 8-second event window + drain)
- Full test suite `./mvnw test` — 515 tests, 0 failures
- Synthesized fixture reproduces the documented bad behavior: three `peopleUpdated` webhooks for one lead within 8 seconds spawn three parallel workflow runs (`runId=1`, `runId=2`, `runId=3` visible in the trigger router logs). Same shape as 2026-05-12 lead 20235.

### Meaningful decisions

#### H2 + MockMvc, not Testcontainers Postgres

Existing pattern in `WebhookProcessingFlowTest` already uses the default H2 + MockMvc setup. The harness inherits this for fast startup (~3s vs ~15s+ for Postgres container). Trade-off: H2 doesn't support partial unique indexes (Phase 5) or true JSONB operators. Plan: revisit when Phase 5 lands; some replay tests may need to move to Testcontainers if H2 limitations become blocking. Documented in the test class Javadoc.

#### Fixture format — min-thresholds, not exact counts

Every `expected` field is a *minimum*, not an exact count. Async timing in Spring Boot tests makes exact assertions brittle (race between webhook dispatcher, workflow trigger router, and the polling assertion loop). The bad behaviors we care about are "≥ N parallel runs" or "≥ N reassigns" — minimum thresholds capture this fine and are stable.

#### Real wall-clock for timing within fixtures

`deltaMs` is honoured via `Thread.sleep` between events. Tests using the synthesized 8-second burst take ~12 seconds total. Acceptable for Phase 0 — keeps the harness conceptually simple. If Phase 2/3/5 need fixtures with minute-long internal gaps (e.g. the full 30-min reassign timeline), we'll need test-clock injection at that point. Not solving it preemptively.

#### Dynamic tests, not parameterized

`@TestFactory` produces one dynamic test per fixture file. Adding a new scenario = drop a JSON file; no Java changes. `@ParameterizedTest` was the alternative but would require an explicit provider method enumerating fixture names.

#### Test workflow registered programmatically in setup, not via Flyway seed

Phase 6 of `agent-followup-enforcement` (the workflow JSON seed migration) is `NOT_STARTED`. The harness can't depend on a seed that doesn't exist. Registering programmatically also keeps the harness self-contained — fixtures don't accidentally affect other tests' workflow state via `@BeforeEach` cleanup.

#### Single `@Primary` bean for the test FUB client

First attempt declared both a `@Bean @Primary ReplayHarnessFollowUpBossClient` AND a `@Bean @Primary FollowUpBossClient` delegating to it. Spring rejected this — "more than one 'primary' bean found." Fix: a single bean method returning the concrete class. Spring registers the bean under both the concrete type and the implemented interface; `@Primary` resolves against the real `FubFollowUpBossClient`.

### What this fixture demonstrates today

The `synthesized-fub-burst-3-webhooks.json` fixture reproduces the lead-20235 pattern at small scale: three `peopleUpdated` webhooks for one lead within ~8 seconds. Today's engine plans one workflow run per webhook → three parallel runs on the same lead, all proceeding independently. This is the behavior documented in `field-observations.md` for 2026-05-12.

Once Phase 2 lands (state diff + event emission, with the FUB-burst webhooks producing no diff after the first), the same fixture should produce **one** workflow run, not three. The fixture's `expected.notes` field documents this evolution so the same JSON file evolves with the phases.

### Repo decisions impact

`No` — local feature concern. The harness is internal test infrastructure; no cross-cutting decisions are introduced.

### Out of scope for Phase 0 (deferred)

- **Real DB-extracted fixtures for lead 20123, 20207, 20231, 20235** — pending payload export from dev DB. See [field-observations.md](../agent-followup-enforcement/plan.md) for the time windows.
- **Test-clock injection for minute-scale fast-forward** — needed if Phases 2/3/5 require fixtures with internal 30-minute gaps (full reassign timeline). Not preemptively built.
- **Testcontainers Postgres variant of the harness** — needed once Phase 5's partial unique index lands and H2 stops being adequate.
- **Recording mode** (capture a live webhook sequence into a fixture file) — would close the loop on harness usage but not needed for the current set of historical incidents.

### Verification (real fixtures)

```bash
$ ./mvnw test -Dtest=ReplayHarnessTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 132.1 s
[INFO] BUILD SUCCESS
```

All five fixtures reproduce the documented bad behavior. Time breakdown is dominated by wall-clock replay of the events: 20123 has events spanning 120s; 20207 spans 60s; the bursts (20231, 20235, synthesized) fit in <30s each.

Each fixture's `expected.minWorkflowRunsForLead` documents the BAD count (one workflow run per `peopleUpdated` webhook) — the same fixtures will be used to validate Phase 2's win (collapse to 1 run) and Phase 5's win (hard-cap at 1 even for distinct genuine transitions).

### Adding new fixtures going forward

The `scripts/build-replay-fixture.sh` script reads from the local dev DB and emits fixture JSON. Use it whenever a new incident worth reproducing surfaces:

```bash
./scripts/build-replay-fixture.sh <source_lead_id> <fixture-name> "<description>" [max_delta_ms]
```

Default `max_delta_ms=30000` (30s). Override for longer scenarios — but remember the harness uses real `Thread.sleep`, so a 600000ms cap means a 10-min test.

---

## Phase 1 — Foundation — implementation log

Status: `DONE` — all five deliverables landed; 537 tests pass (515 baseline + 9 validator + 13 from other added suites since baseline), replay harness's new Phase 1 invariant assertion holds across all 5 fixtures in 131.8 s.

### Goal

Cheap, no-behaviour-change groundwork that unblocks Phases 2–5:

1. Populate `workflow_runs.webhook_event_id` on every run (resolves known-issue #25).
2. Thread `webhookEventId` through `RunContext` so steps can reach it.
3. Add `leads.previous_state` JSONB column (Phase 2 will populate it).
4. Workflow-creation-time validator refusing unknown `lead.<field>` references.
5. Audit `LeadUpsertService.SNAPSHOT_FIELDS` coverage.

### What landed

| File | Change |
|---|---|
| `src/main/resources/db/migration/V20__add_previous_state_to_leads.sql` | **new** — `ALTER TABLE leads ADD COLUMN previous_state JSONB` |
| `persistence/entity/LeadEntity.java` | `@JdbcTypeCode(SqlTypes.JSON)` mapped `previousState` JSONB field |
| `service/webhook/model/NormalizedWebhookEvent.java` | added `Long webhookEventId` + `withWebhookEventId(Long)` wither |
| `service/webhook/WebhookIngressService.java` | dispatches `event.withWebhookEventId(savedEntity.getId())` after persistence |
| `service/webhook/parse/FubWebhookParser.java` | passes `null` webhookEventId (pre-persistence) |
| `service/webhook/ProcessedCallAdminService.java` | passes `null` webhookEventId (replay path skips persistence) |
| `service/workflow/trigger/WorkflowTriggerRouter.java` | replaced hardcoded `null` at L156 with `event.webhookEventId()` |
| `service/workflow/RunContext.java` | added `Long webhookEventId` to `RunMetadata` record (after `runStartedAt`) |
| `service/workflow/WorkflowStepExecutionService.java` | `buildRunContext` reads `run.getWebhookEventId()` into `RunMetadata` |
| `service/workflow/WorkflowGraphValidator.java` | added `validateLeadFieldReferences(node, nodeId, errors)` + regex patterns + recursive config walk |
| `service/lead/LeadUpsertService.java` | exposed `capturedFieldNames()` returning `Set<String>` view of `SNAPSHOT_FIELDS` |
| `test/.../replay/ReplayHarnessTest.java` | added `assertPhase1Invariants` — every workflow_run must have non-null `webhook_event_id` |
| `test/.../service/workflow/WorkflowGraphValidatorFieldReferenceTest.java` | **new** — 9 tests covering known refs, unknown refs, templates, expressions, false-positive substrings, agent-followup workflow regression |
| 11 existing test files | mechanical sweep adding the new `null` arg to `NormalizedWebhookEvent` constructors |
| 2 test files | mechanical sweep adding the new `null` arg to `RunMetadata` constructors |

### Verification

```
$ ./mvnw test
[INFO] Tests run: 528, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ ./mvnw test -Dtest=ReplayHarnessTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 131.8 s
[INFO] BUILD SUCCESS
```

The Phase 1 invariant assertion in the replay harness is the strongest evidence: each of the five recorded incidents now produces workflow_runs with `webhook_event_id` populated — a regression-proof gate on known-issue #25.

### Meaningful decisions

#### `NormalizedWebhookEvent.withWebhookEventId(Long)` wither over a constructor mutation

The DTO is a record. Could have changed every caller to pass the id in the constructor; instead added a copy-on-write `withWebhookEventId` so only `WebhookIngressService` (where the id becomes available) calls it. Test/non-persistence call sites stay readable — they pass `null` and a single line in ingress promotes that to the real id post-save. Smaller blast radius on changes that aren't conceptually about webhook event ids.

#### `webhookEventId` placed inside `RunMetadata`, not as a top-level `RunContext` field

Conceptually it IS run metadata — alongside `runId`, `workflowKey`, `workflowVersion`, `runStartedAt`. Cost: 2 test fixtures needed their `RunMetadata(...)` constructor calls updated. The third RunContext call site (`ExpressionEvaluatorTest:40`) passes `null` for metadata so no change was needed there.

#### `SNAPSHOT_FIELDS` kept as `List<String>` internally, `Set<String>` exposed externally

Briefly tried converting `SNAPSHOT_FIELDS` to `Set.of(...)` directly. Reverted because `Set.of` has undefined iteration order, which would scramble the snapshot's JSON key order in `buildSnapshot()`. Solution: keep the `List<String>` for deterministic iteration, add a frozen `Set.copyOf(SNAPSHOT_FIELDS)` for the public `capturedFieldNames()` getter. Best of both — O(1) membership lookup for the validator, stable JSON output for upserts.

#### Regex extraction over JSONata AST inspection for field references

`JsonataExpressionEvaluator` evaluates expressions but exposes no AST. Two regexes — one for bare JSONata (`\blead\.([a-zA-Z][a-zA-Z0-9_]*)`) and one for templates (`\{\{\s*lead\.([a-zA-Z][a-zA-Z0-9_]*)`) — cover the field-reference shapes that actually appear in workflow JSON. Word boundaries on `\blead` prevent false positives on `mislead.foo` (test case asserts this). Phase 4 may upgrade to AST-based extraction if the regex bites; not preempting.

#### Field-reference check is per-node, not graph-global

Errors include the node id so a workflow author can locate the bad reference fast. The recursive walk happens inside `validateLeadFieldReferences` (called once per node from the existing loop) rather than once on the whole graph. Slight duplication of traversal but reads more naturally.

#### Validator runs even when no `lead.*` references exist

`validateLeadFieldReferences` short-circuits cheaply when the referenced set is empty. Cost is one regex scan per node config string, which is negligible compared to graph-shape validation that already runs.

### SNAPSHOT_FIELDS audit result

Production workflow `agent_followup_enforcement` references only `lead.assignedUserId` and `lead.assignedTo`. Both already in `SNAPSHOT_FIELDS`. **No additions needed today.** Future workflows that reference new fields will be caught by the validator at save time — operators extend `SNAPSHOT_FIELDS` then, no migration needed because leads are upserted on every webhook.

### Repo decisions impact

`No` — Phase 1 is local feature-internal scaffolding. The new `previous_state` column, `NormalizedWebhookEvent` field, and `RunMetadata` field are all opaque to the rest of the system. The validator's reject-on-unknown-field behaviour is a workflow-shape constraint, not a repo-wide decision; if a second feature ever needs the same "validate before save" pattern, then promote.

### Out of scope (deferred to later phases)

- Reading `previousState` and computing diffs — Phase 2
- Emitting domain events on diff — Phase 2
- Exposing `webhookEventId` through `ExpressionScope` — Phase 4
- Engine-write attribution via `EngineWriteTracker` — Phase 3
- `state_change_event_id` column on workflow_runs — Phase 4
- Run-level uniqueness (partial unique index) — Phase 5
- Hardcoded `"FUB"` source-system fix (known-issue #18) — separate
- Re-authoring `agent_followup_enforcement` workflow — Phase 4

---

## Pre-Phase-2 — Lead to Person rename — implementation log

Status: `DONE`

### Goal

Complete the vocabulary substrate before Phase 2 adds durable domain events. The entity tracked from FUB `/v1/people` is now consistently `Person`; `Lead` remains a possible business classification via `person.kind = LEAD`, not the name of the persisted CRM-contact record.

### Meaningful decisions

#### Admin API route hard-cut

The app is not deployed, so the admin read surface was renamed directly from `/admin/leads` to `/admin/persons` with no compatibility alias. The response shape follows the new vocabulary too: summary payloads expose `person`, and list/detail rows expose `sourcePersonId`.

#### Full SPA vocabulary rename

The UI rename covers routes, ports, HTTP adapter, schemas, query keys, shared types, page components, labels, and tests. This avoids a half-renamed state where `/admin/persons` is backed by `Lead*` UI types and makes Phase 2's domain-event work easier to reason about.

#### Stage filtering stays in workflow logic

The old ingestion-time stage filter remains removed. `PersonUpsertService` persists every FUB person webhook it can fetch, maps stage to `PersonKind`, and the production workflow gates on `person.kind = "LEAD"` so non-lead-stage persons are stored but do not trigger the lead follow-up workflow.

#### Historical docs stay historical

Phase 0 and Phase 1 implementation logs keep their original `Lead` wording where they describe what was built at that time. Current feature planning docs and current implementation surfaces use `Person`.

### Validation evidence

- `./mvnw -q -DskipTests compile` passed.
- `./mvnw test` passed: 525 tests, 0 failures, 0 errors.
- `./mvnw test -Dtest=ReplayHarnessTest` passed: 5 tests, 0 failures, 0 errors.
- `cd ui && npm test` passed: 71 files, 349 tests.
- `cd ui && npm run test:e2e` passed: persons route shell smoke.
- Stale-reference grep over production Java and UI source returned no matches for old lead routes, DTO/type names, `sourceLeadId`, `lead_details`, or `NormalizedDomain.LEAD`.

### Repo decisions impact

`No` — local feature concern. The rename aligns this feature's substrate with FUB's `/v1/people` terminology but does not create a new repo-wide architectural rule.

---

## Phase 2 — Implementation Log

Status: `DONE` — all 5 sub-phases shipped. 589 tests green.

This file is the decision narrative for Phase 2 per the AGENTS.md convention: it answers *"why was it built this way?"* for a future reader, not *"what files changed"* (git is the source of truth for that). Entries are added as each sub-phase lands.

See [`plan.md`](./plan.md) for the locked commit-level plan and [`plan.md`](./plan.md) for architectural rationale.

| Sub-phase | Commit | Theme |
|---|---|---|
| 2a | `a9dc258` | Scaffold (events table, emitter, dispatcher) |
| 2b | `41044ba` | Refactor (extract `CallUpsertService`, restructure `PersonUpsertService`, both-site lock) |
| 2c | `26e8103` | Person events emission + concurrency proof |
| 2d | `f3a8036` | Append events (`call.created`, `note.*`) |
| 2e | `9b73759` | Replay-harness asserts events; extract `NoteEmissionService` |

---

### Sub-phase 2a — Scaffold

#### `DomainEventEmitter` — what it does, in plain English

Every place in the codebase that genuinely changes state — a person got reassigned, a call came in, a note was created — calls `DomainEventEmitter.emit(...)`. Think of it as the engine saying out loud: **"this just happened, and it really happened."**

The emitter does two things, in this exact order:

1. **Writes the event into the `events` table** inside the same database transaction as the state change that caused it. So either *both* the state change and the event are saved, or *neither* is. There's no universe where the person's `assignedUserId` updates but the `person.state_changed` event goes missing — or vice versa.
2. **Tells the rest of the engine about it, but only after the database confirms the change is locked in.** It posts a "when this transaction successfully commits, run the dispatcher" note. If the transaction rolls back, the note is thrown away and nobody hears about the event — because it didn't really happen.

#### Benefits

| Benefit | Plain English |
|---|---|
| **Atomicity** | The event row and the state change are inseparable. You can't get one without the other. No more "the database says X but our audit log says Y." |
| **No phantom events on rollback** | If the upsert fails halfway through and rolls back, no listener ever hears about a change that never happened. Eliminates a whole class of ghost workflow runs. |
| **No locked-row contention with listeners** | Listeners (Phase 4's workflow trigger) run *after* the transaction commits, so they can take their time — they're not holding the `persons` row hostage while they decide what to do. |
| **Loud failures, not silent ones** | If a future developer adds a new caller and forgets to mark it `@Transactional`, the code throws an exception the first time it runs. The bug surfaces in dev, not in production three months later as a missing audit trail. The `MANDATORY` propagation guard is what enforces this. |
| **One central choke point** | Every domain event in the system flows through this one method. Want to add metrics, tracing, an outbox poller, schema validation? One place to change. |

#### The enabling tech — one-liner

> Spring's **`TransactionSynchronizationManager`** lets you attach a callback that fires only *after* the current database transaction commits — so the emitter can record the event inside the transaction and notify listeners outside it, atomically.

Without this single piece of framework machinery, you'd have to choose between:
- **Notify inside the transaction** — slow, locks held too long, listener failures roll back the state change
- **Notify outside the transaction** — fast, but you can lose the event if the process dies between commit and notify

The synchronization hook gives you both halves of the deal: the event row is durable before any listener runs, and the listener runs outside the lock-holding window of the upsert.

#### Naming caution

Three related-but-distinct concepts live in this codebase. Easy to confuse on first read:

| Class | What it is |
|---|---|
| `WebhookEventEntity` (table `webhook_events`) | The raw inbound webhook from FUB. Pre-existing. |
| `EventEntity` (table `events`) | The persisted domain event the engine emits when state actually changes or an append happens. NEW in Phase 2. |
| `DomainEvent` (Java record) | The in-memory shape handed to listeners after commit. Same data as `EventEntity`, but a plain value type so listeners don't depend on JPA. |

`EventEntity.sourceEventId` is the FK linking each domain event back to the webhook that caused it (null for engine-synthesized events).

---

### Sub-phase 2b — Refactor (zero behaviour change)

#### Why a pure refactor sub-phase exists at all

The original 4-sub-phase split layered emission onto the unrefactored services and surfaced four real defects under fresh-eyes review (`MANDATORY`-propagation crash on the calls/notes path; lock-hole on the brand-new-person insert race; harness `min` assertions that silently accept a broken collapse; implicit `oldDetails` capture that can be overwritten before read). The root cause was structural — `PersonUpsertService` and `WebhookEventProcessorService` were sized for "webhook → fetch → save," not for "atomic state-change-with-event." Doing the restructure first means 2c lands on a shape where those defects are **impossible to write**, not just absent.

#### Capture-old / apply-new shape

`upsertFubPerson` now captures `JsonNode oldDetails = entity.getPersonDetails()` as a named local **before any mutation**, in both the existing-row branch and the `DataIntegrityViolationException` recovery branch. 2c reads this local to compute the diff. The capture-before-mutate shape makes "diff sees the post-save value" structurally impossible — a future reader cannot accidentally reorder the lines such that the bug returns.

#### Both-site `findBy…ForUpdate`

The collapse claim ("N webhooks → 1 event") fails for brand-new persons if only the primary find site uses the pessimistic lock. When N parallel inserts race, the unique-constraint losers each go through the `DIVE`-recovery re-read; if that re-read is non-locking, every loser reads the winner's row independently and each emits its own event. The recovery path is the *only* path that runs during the race, so it must take the lock too. Both sites use `findBySourceSystemAndSourcePersonIdForUpdate` (explicit `@Query` JPQL — Spring Data is finicky about applying `@Lock` to plain derived methods).

#### Surgical `CallUpsertService` extraction

`CallUpsertService` owns one method: `persistCallFacts`. The body lifted verbatim from `WebhookEventProcessorService` — only the save and orphan-person warn log moved. Retry, the decision engine, and task creation stay in `WebhookEventProcessorService.processCall`. The narrow extraction is what made the call-path `@Transactional` (the unit of work the emitter requires) without bundling an unrelated orchestration refactor. 2d injects `DomainEventEmitter` into this service; the boundary established here is what 2d consumes.

#### Verification of "zero behaviour change"

Existing `PersonUpsertServiceTest` cases switched mocks from `findBy…` to `findBy…ForUpdate` with **identical assertions** and stayed green — the cleanest possible signal that the restructure preserved behaviour. New `CallUpsertServiceTest` (13 cruel tests) pins the lifted persistence behaviour: all seven call-fact field mappings, orphan-warn semantics with full log-message context (`eventId` + `callId` + `sourcePersonId`), `personId=0L` treated as a real id, `save` exception propagation, and so on. Tests: 532 → 545 green.

---

### Sub-phase 2c — Person events emission

#### Three real branches, one structural rule

The emission paths line up with the capture-old / apply-new shape from 2b:

| Branch | Emit | `previousState` |
|---|---|---|
| Brand-new row (no existing OR `DIVE`-recovery winner) | `person.created` with `{ current: <full snapshot> }` | `null` |
| Existing + non-empty diff | `person.state_changed` with `{ changed_fields, previous, current }` (only changed fields) | `oldDetails` |
| Empty diff (echo / no-op) | nothing | untouched |

Echoes touching `previousState` would be the wrong contract — `previous_state` is "what state did this row hold last time we observed a meaningful change," not "what state did we have one webhook ago." An echo is an observation that nothing meaningful happened.

#### `PersonDiffComputer` — why these strategies

Per-field strategies matter because the **wrong strategy emits phantom events on identical data**.

- **Scalars** — `JsonNode.equals`. Straight-line.
- **`tags`** — set-of-string equality. FUB does not guarantee tag order; client reordering is not a state change.
- **`phones` / `emails`** — set-of-`JsonNode` equality using Jackson's deep structural equals on each element. Order-independent at the element level.
- **Missing field ≡ JSON null ≡ empty array** — FUB beginning to send `{}` or `[]` for a previously-absent field must not fire a phantom event. The 17-case `PersonDiffComputerTest` caught a real bug here on first run: the collapse only worked when both sides were `null`. Fixed.

#### The Hibernate session corruption surfaced (and fixed) here

The `DIVE`-recovery path predates Phase 2 — it has existed since the leads era. The concurrency stress test caught a latent bug in it on first run: when the failed `INSERT` rolls back inside the outer `@Transactional`, Hibernate's session is left with a stale entity reference without an ID. Subsequent operations on the same `EntityManager` fail with `"Entry for instance of PersonEntity has a null identifier."` Mocked-`save()` unit tests never caught this — they sidestep real Hibernate flush entirely.

Fix: the `INSERT` runs in its own `REQUIRES_NEW` tx via `TransactionTemplate` so `DIVE` rolls back only the inner tx, leaving the outer's `EntityManager` clean for the recovery `findBy…ForUpdate`. Emission of `person.created` happens inside the inner tx, atomic with the `persons` row. `save → saveAndFlush` in the insert path so the `DIVE` fires inside the inner tx, not at outer-tx commit time.

This is the same lesson 2e re-learns: framework-level mechanics (proxy boundaries, session flush, lock semantics) are exactly what mocks bypass.

#### The collapse proof

`PersonUpsertConcurrencyStressTest` fires N=10 parallel `upsertFubPerson` calls for the same `sourcePersonId` against real Postgres (Testcontainers), `@RepeatedTest(3)`. Scenario A (row exists, identical payload from all 10): asserts **exactly 1** `person.state_changed`. Scenario B (row doesn't exist, identical payload from all 10): asserts **exactly 1** `person.created`, **0** `state_changed` (losers' `DIVE`-recovery sees identical data, empty diff, no emit). Hikari pool bumped to 30 — each thread holds the outer + briefly the inner connection; the default 10 deadlocks. **Policy**: if this ever flakes, the collapse invariant is broken; investigate before merge — do not rerun until green.

Tests: 545 → 574 green (+17 diff matrix, +6 emission, +6 concurrency).

---

### Sub-phase 2d — Append events

#### `call.created` — payload built manually, not via `valueToTree`

`jackson-datatype-jsr310` is a transitive dep that's not on the compile classpath. Directly serializing `OffsetDateTime` via `valueToTree` would compile but fail at runtime once the transitive dep dropped out — a fragile contract. The manual payload builder produces an ISO-8601 string for `createdAt` (matching what `JavaTimeModule` would emit) and adds no new project dep, no transitive classpath assumption. YAGNI-clean for what we actually need.

`source_event_id` on the emitted row is the **webhook** id, not the FUB call id. The FUB call id is `entity_id`. Mixing them would corrupt the lineage Phase 4 walks to attribute runs to triggering webhooks.

#### Note webhooks — no `notes` table, no `NoteUpsertService` (yet)

`NormalizedDomain` gains `NOTE`; `NormalizedAction` gains `DELETED`. The V21 CHECK constraint on `webhook_events` already permits `NOTE` (added in Pre-Phase-2 for exactly this). `WebhookEventProcessorService` gets a `case NOTE → processNoteDomainEvent` branch — package-private `@Transactional` private method that emits `note.created` / `note.updated` / `note.deleted` per `event.normalizedAction()`. The `@Transactional` annotation is what makes the emitter's `MANDATORY` propagation pass.

There is no `notes` table and no FUB body fetch. Workflows that need note body content can fetch on demand from `/v1/notes/{id}` when a real consumer arrives. Building the table now would be ceremony for nobody.

Tests: 574 → 586 green (+3 parser, +3 call emission, +6 note branch).

#### Why this 2d-as-shipped does NOT survive 2e — see below

The package-private `@Transactional processNoteDomainEvent` ships here and 2e immediately extracts it. The reason is Spring proxy mechanics, not a planning miss — 2d's tests passed locally with mocked emitter; the harness in 2e caught the proxy issue. See 2e for the full story.

---

### Sub-phase 2e — Replay-harness assertions + `NoteEmissionService` extraction

#### Honest contract on the harness assertions

`ReplayFixture.Expected` gains three fields: `expectedCreatedEventsForPerson` (exact), `expectedStateChangeEventsForPerson` (exact — catches both undershoot and overshoot), and `minAppendEvents` (min by `event_kind` — no uniqueness claim applies to append events). The drain loop checks at-least to terminate promptly; exact-count overshoot is asserted **post-drain** so a fast-failing overshoot doesn't busy-wait.

All four person fixtures (20123, 20207, 20231, 20235) plus a new synthesized note fixture assert exact counts. The honest contract: `expectedStateChangeEventsForPerson = 0` for all four because the harness mock returns the **same** snapshot for every `getPersonRawById` call — every subsequent webhook produces an empty diff and no emission. That's a **stronger** collapse claim than "1 state_changed": *"N webhooks of identical data → 1 created, 0 state_changed."* Per-payload-difference scenarios are covered structurally by `PersonUpsertConcurrencyStressTest` against real Postgres; the harness's job is end-to-end replay against recorded sequences, not lock-semantics proof.

#### The deviation: `NoteEmissionService` (not `NoteUpsertService`)

`plan.md` §"Defaults" says: *"Inline `@Transactional processNoteDomainEvent` on `WebhookEventProcessorService`. No `NoteUpsertService` — there's no state to own."*

2e ships a `NoteEmissionService` anyway. **The deviation is real and worth recording so a future reader doesn't think it's accidental.**

##### What the harness caught that unit tests missed

The 2d implementation of `processNoteDomainEvent` was package-private `@Transactional` **inside** `WebhookEventProcessorService`, called via `this.processNoteDomainEvent(event)` from `process()`. Spring's proxy boundary is bypassed on `this.` self-invocation, so `@Transactional` **never applied**, `DomainEventEmitter`'s `MANDATORY` guard threw `IllegalTransactionStateException` on every notes webhook, `AsyncWebhookDispatcher` swallowed the exception, and the harness saw 0 note events.

Unit tests with a mocked emitter missed this entirely — they sidestep the framework-level proxy mechanics, exactly the way mocked `save()` sidesteps Hibernate flush in the 2c story. Same lesson, second instance.

##### Why a separate `@Component`, named `*EmissionService` not `*UpsertService`

The fix is to put the `@Transactional` method on a separate `@Component` so the call from `WebhookEventProcessorService.process()` goes through Spring's proxy. That makes `@Transactional` apply and `MANDATORY` pass.

The naming is deliberate. `*UpsertService` belongs where there is persistence to own (`PersonUpsertService` owns `persons`; `CallUpsertService` owns `processed_calls`). There is no `notes` table — `NoteEmissionService` owns no state, only the emission semantics for note webhooks. The name reflects what the component does: emit, not upsert. The structural symmetry across all three emission paths — every domain emission flows through a proxy-boundaried `@Component` — is the point. (`*EmissionService` when there is no persistence, `*UpsertService` when there is.)

`NoteEmissionServiceTest` (7 tests): all three actions map to right `event_kind`; missing `resourceIds` emits nothing; multiple `resourceIds` → one event per note; payload passes through by reference; unmapped action skips emission. `WebhookEventProcessorServiceTest`'s 6 note emission-semantics tests moved here (they belong where the emission lives now); a single delegation check stays in `WebhookEventProcessorServiceTest`.

#### The two latent bugs Phase 2 surfaced are the same class

| Stage | Sidestepped by mocks | Surfaced by |
|---|---|---|
| 2c — Hibernate session corruption in `DIVE`-recovery | mocked `save()` never flushes | `PersonUpsertConcurrencyStressTest` against real Postgres |
| 2e — `@Transactional` self-invocation bypassing the proxy | mocked emitter never executes the propagation guard | `ReplayHarnessTest` driving the real Spring container |

Both lived undetected behind mocks for an unknown duration before this feature touched the area. Both are the kind of bug that demands real-wiring verification — Testcontainers + the replay harness are how this feature catches them.

#### Phase 2 complete

Tests: 586 → 589 green (+9 from `NoteEmissionServiceTest` + harness fixture + harness inject; -6 from the collapsed note tests in `WebhookEventProcessorServiceTest`).

The collapse claim is verified at three levels — unit (`PersonDiffComputerTest`), concurrency (`PersonUpsertConcurrencyStressTest` against real Postgres), end-to-end (`ReplayHarnessTest` against recorded incidents). Substrate is ready for Phase 3.

#### Repo decisions impact

`No` — feature-internal. The proxy / `@Transactional` / `*EmissionService` vs `*UpsertService` distinction is a pattern this feature establishes, but it's a local naming convention, not a repo-wide architectural rule worth promoting to `Docs/repo-decisions/`. If a future feature replicates the pattern (proxy-boundaried emission service, no state to own), then promote.

---

## Phase 3a — Implementation Log

Status: `DONE` — scaffold landed; no production caller of the coordinator yet, no behaviour change. 620 tests green (589 baseline + 31 new).

Decision narrative for Phase 3a per the AGENTS.md convention: answers *"why was it built this way?"* — git is the source of truth for what changed.

See [`plan.md`](./plan.md) for the locked plan, [`plan.md`](./plan.md) for the analysis that drove the design.

### Deliverables shipped

| Layer | Surface |
|---|---|
| Tracker interface | `EngineWriteTracker`, `EngineWriteRecord` |
| Tracker impl | `InMemoryEngineWriteTracker` (`ConcurrentHashMap` + `@Scheduled` eviction) |
| Coordinator interface | `EngineWriteCoordinator` (3 op modes) |
| Coordinator impl | `DefaultEngineWriteCoordinator` (REQUIRES_NEW inner tx) |
| Emitter hook | `DomainEventEmitter.maybeAnnotateEngineSource(...)` |
| Config | `engine.write.tracker.ttl-seconds`, `eviction-interval-ms`, `emit-events` |
| Test infra | `FakeFollowUpBossClient`, `EngineWriteRaceHarness` (skeleton) |
| Tests | tracker (12), coordinator (12), emitter annotation (6), harness smoke (1) |

### Why these decisions

#### No `markFailed` on the tracker

The plan dropped revert entirely. `RetryPolicy.DEFAULT_FUB` handles transient FUB failures; permanent failures accept drift until the next webhook re-syncs. There's no failure state for a tracker to record. The interface stayed honest by not exposing a method nobody calls.

#### Subset match — annotation-over-detection bias

`findMatching` returns a hit when `record.changedFields ⊆ diffFields`. The looser direction (annotate when the diff is at least the engine's intent, even if more fields changed) deliberately annotates concurrent-external scenarios as `ENGINE`. Strict equality would miss the A4 case in the matrix.

The trade-off is documented as a deliberate platform choice. Phase 4 workflows filtering `change.source != "ENGINE"` will suppress some events triggered by external concurrent changes. The alternative — letting phantom events flow through — was the worse failure mode.

Pinned by `findMatchingSubsetHits_engineFieldsAreSubsetOfDiff` and `findMatchingSupersetMisses_engineWroteMoreThanDiffShows` in the tracker test.

#### Engine event emission is gated, not yet emitting

`engine.write.emit-events` defaults to `false`. The coordinator prepares the would-be `person.state_changed` payload and logs it at INFO with the marker `[engine-write-emit:LOG_ONLY]`. The `emit(...)` code path is exercised under `true` via a dedicated unit test (`scalarEmitsAnnotatedEvent_whenEmitEventsTrue`) so Phase 4 can flip the flag without code surprises.

Decision context: the user asked for the code path to be ready but not active. Keeps the implementation honest (no dead code, no theoretical paths) while preserving today's event-stream behaviour.

#### `REQUIRES_NEW` is a real, tested requirement

[Smoking gun](./plan.md#smoking-gun): `WorkflowStepExecutionService.executeClaimedStep` is `@Transactional`. If the coordinator's inner write joined that outer tx, the row lock would be held across the FUB HTTP call and across the entire step's lifecycle. Under burst load this would pin the 2–4 thread webhook async pool.

Mitigation: `TransactionTemplate(REQUIRES_NEW)` for all inner writes. The unit test `scalarRequiresNew_outerRollbackDoesNotUndoInnerWrite` wraps the coordinator call in an outer `TransactionTemplate`, marks rollback-only, and verifies the inner write persists. The defence is structural, not aspirational.

#### Annotation lives in `payload.source`, not on the `DomainEvent` record

Choosing a new field on `DomainEvent` would have churned every listener signature and forced Phase 2's existing dispatcher tests to be updated. Storing in the JSON payload is what Phase 4 consumers will read anyway (workflow filters access `change.source`). The emitter mutates the payload in place when it's an `ObjectNode`, or wraps via `ObjectMapper.valueToTree` when not.

#### Tracker key shape

`(entityType, entityId)`. `runId` is recorded on each `EngineWriteRecord` for the Phase 4 audit trail but is **not** part of the match key. Rationale: two engine writes for the same person from different workflow runs (A7) should both annotate echoes. Keying by `runId` would partition tracker records and cause spurious misses on the second write's echo.

#### `ConcurrentHashMap.compute` for thread-safe per-key list mutation

The naive shape (read list, mutate, put back) has a TOCTOU race. `compute(...)` holds the per-bucket lock for the duration of the lambda, making `record(...)`, `findMatching(...)`, and `evictExpired(...)` atomic per key. The internal list inside each bucket is mutated exclusively under that lock, so the un-synchronized `ArrayList` is safe by construction.

Verified by `concurrentRecordAndFindIsThreadSafe` — 20 threads × 50 ops with no record loss.

#### Test infrastructure for 3b/3c/3d/3e to extend

`EngineWriteRaceHarness` and `FakeFollowUpBossClient` ship in 3a as skeletons. The smoke test (`EngineWriteRaceHarnessSmokeTest`) exists solely to prove the harness wires correctly — concrete A/B/C/D scenarios land in their respective sub-phases. This is the same playbook Phase 2 followed (Phase 2a built the scaffold; Phase 2c put it to use).

### Defaults locked in code

| Default | Value | Why |
|---|---|---|
| Tracker impl | `InMemoryEngineWriteTracker` | Phase 3 has no consumers; crash-window phantom-event class is dev-acceptable. Redis impl is a Phase 4 prerequisite. |
| TTL | 30s | Covers FUB echo arrival window (300–800ms) with safe headroom. |
| Eviction interval | 10s | Bounded loop cost; no observable lag for echo annotation. |
| `emit-events` | `false` | Phase 3 default; Phase 4 flips. |
| `source` annotation location | `event.payload.source` | Avoids churning listener signatures. |
| Match semantics | subset (`record ⊆ diff` → hit) | Annotation-over-detection bias; alternative misses A4. |

### Repo decisions impact

`No` — feature-internal. No new project-wide convention. The `*Coordinator` naming for op-mode-dispatching services and the gated-emission pattern (`[engine-write-emit:LOG_ONLY]` log marker) are local conventions; if a future feature replicates them, promote to `Docs/repo-decisions/`.

---

## Phase 3b — Implementation Log

Status: `DONE` — `fub_reassign` routes through the coordinator; A1–A7 codified and green. 644 tests pass.

Decision narrative per AGENTS.md convention.

### Deliverables shipped

| Layer | Surface |
|---|---|
| Step wrap | `FubReassignWorkflowStep` now calls `coordinator.applyScalarFieldUpdate(...)` with `Map.of("assignedUserId", LongNode.valueOf(targetUserId))` |
| Step test | `FubReassignWorkflowStepTest` (new — no test class existed for this step) — 17 tests |
| Race scenarios | `ReassignScenariosTest` (A1–A7) — 7 tests against real Postgres |
| Parity test patch | `WorkflowParityTest` seeds local person row in tests that exercise reassign |
| Harness lifecycle fix | `EngineWriteRaceHarness` switched to JVM-shared Postgres container |

### Why these decisions

#### Retry stays inside the coordinator's `Supplier`

The wrap shape is:

```java
coordinator.applyScalarFieldUpdate(
    context.sourcePersonId(),
    Map.of("assignedUserId", LongNode.valueOf(targetUserId)),
    context.runId(),
    () -> fubCallHelper.executeWithRetry(
            () -> followUpBossClient.reassignPerson(personId, targetUserId)));
```

Retry is inside the `Supplier` passed to the coordinator. Two reasons:

1. If retry happened **outside** the coordinator call, the coordinator's REQUIRES_NEW tx (local update + tracker record) would commit once, then retry loops would call FUB without re-recording. State would be inconsistent across attempts.
2. The coordinator's job is "commit local intent atomically, then do the side effect once." Retries are part of the side effect, not part of the intent.

Verified by `retryHappensInsideCoordinatorSupplier_notOutsideIt`: mocks the helper to invoke the Supplier and asserts both `helper.executeWithRetry` AND `fub.reassignPerson` were called, proving the chain.

#### `LongNode.valueOf` for the field value, then JSON round-trips to `IntNode`

`LongNode` is the simplest constructor for a `Long`-typed `targetUserId`. After the coordinator persists `personDetails`, Hibernate serializes to JSON and the next read deserializes — Jackson chooses `IntNode` for values fitting in `int`. This means production echoes (also from JSON parsing) compare type-correctly against the persisted state.

This works because **every comparison happens after a JSON round-trip on at least one side**. The same isn't true in tests that construct payloads with `createObjectNode().put(long)` — those produce `LongNode` without round-trip, which mismatches `IntNode` from DB load. See "Test bugs surfaced" below.

#### Step constants promoted to `public`

`FubReassignWorkflowStep`'s result-code constants (`SOURCE_LEAD_ID_MISSING`, `FUB_REASSIGN_TRANSIENT`, etc.) were package-private. The new test class lives one package up (matching `FubAddTagWorkflowStepTest`'s convention). Two options: move tests into the steps package, or promote constants to `public`. Chose promotion — matches the existing `FubAddTagWorkflowStep` convention and avoids a one-off package split.

#### `WorkflowParityTest` needed local person seeding

The parity test exercises `fub_reassign` end-to-end. Pre-Phase-3, the step never touched local state, so no `persons` row was needed. Post-3b, the coordinator's `findBy…ForUpdate` throws `IllegalStateException` if the row doesn't exist → step returns `REASSIGN_EXECUTION_ERROR` → parity assertion fails.

Fix: added a `seedPersonRow(sourcePersonId)` helper called by the two tests that actually execute reassign (`shouldReassignWhenNoCommunicationFound`, `shouldHandleReassignFailure`). The seed contains `assignedUserId=0, stage=Lead` — minimal valid state. The tests that test the SKIPPED path for reassign don't need the seed.

This change is honest about the new Phase 3 requirement: engine scalar writes target existing persons. Workflows triggered by webhooks always have an existing person (the webhook created it), so production behaviour is fine. Test fixtures need to match.

### A1–A7 — what each cell verifies

| Cell | Property verified |
|---|---|
| **A1** | Happy path: engine writes local, echo from FUB sees `local == payload`, emits 0 events. |
| **A2** | **Smoking-gun structural proof.** Sets `fakeFub.delayMs=500`. Engine starts on background thread; main thread fires the echo at t≈50ms and measures elapsed time. If the row lock were held across the FUB call, echo would block until t≈500ms+. Assertion: echo elapsed < FUB-delay/2. |
| **A3** | 3 burst echoes for one engine write → still 0 events; tracker still has the original record. |
| **A4** | Engine + concurrent external change. Both events emit, **both annotated `source=ENGINE`** under the subset-match rule. Documented in test as the annotation-over-detection bias. |
| **A5** | Permanent FUB failure → no revert → local stays at engine value → next echo carries FUB's actual state → emits 1 event annotated `ENGINE`. This is the misleading-echo-after-permanent-failure case (known-issue #26). |
| **A6** | Transient failure with concurrent external — same outcome as A4. Retry doesn't change the race shape. |
| **A7** | Two engine writes back-to-back on the same person. The pessimistic lock serializes them. Both FUB calls succeed. Final echoes annotated `ENGINE` regardless of PUT order — tracker has records for both writes. |

### Test bugs surfaced (worth recording)

#### `PersonDiffComputer` is type-strict on `JsonNode.equals`

`Objects.equals(LongNode(200), IntNode(200))` → `false` (different Jackson classes). The first race-scenario run had 0-event tests emitting events because:

1. Engine wrote `LongNode(200)` via the coordinator.
2. Hibernate persisted as JSON; next load deserialized as `IntNode(200)`.
3. Test's `fireEcho` built payload with `createObjectNode().put("assignedUserId", 200L)` → `LongNode(200)`.
4. `buildSnapshot` of the test payload returned `LongNode` (no round-trip happened — the test built the JsonNode directly, not via JSON parsing).
5. Diff against the IntNode-loaded local → non-empty → event emitted.

The bug is in the test, not in production code. Real FUB webhooks arrive as JSON text, parsed by Jackson → `IntNode` for small values, matching the type of the round-tripped DB load. Fix in test: `fireEcho` constructs the payload by parsing a JSON string, matching production. The type fragility of the diff is a latent risk worth being aware of — documented as a follow-up consideration if future code paths construct `JsonNode` directly.

#### Hikari pool exhaustion under many Spring contexts

The full suite has ~19+ `@SpringBootTest` classes. Several of them (mine and Phase 2's) use `@Container` with their own `PostgreSQLContainer`. When run sequentially, Docker resource accumulation caused some later containers (specifically `ReassignScenariosTest`'s) to fail to start — `HikariPool-19: Connection to localhost:60418 refused`.

Fix: replaced `@Container static` with a JVM-shared `static final PostgreSQLContainer` initialized in a static block, with shutdown hook. All race subclasses share one container for the JVM lifetime. The smoke test (`EngineWriteRaceHarnessSmokeTest`) and the scenarios (`ReassignScenariosTest`) now share one Postgres.

Side benefit: faster full-suite run because one Postgres startup, not two.

#### `verify(coordinator).applyScalarFieldUpdate(...)` syntax cleanup

Initial draft of `happyPath_invokesCoordinatorWithExactFieldMapAndRunId_thenSucceeds` used a malformed Mockito verify construction (`ArgumentCaptor.forClass(...).capture() == null ? null : "20235"`) that happened to match leniently. Rewrote using two explicit captors and direct `assertEquals` against `personIdCap.getValue()`. The test was passing for the wrong reason; the rewrite makes it pass for the right reason.

### What's NOT in 3b

- No `change.source` consumer. `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` still runs unchanged. Phase 4 wires the consumer.
- No engine-write event emission in production (gate stays `false`). The code path is exercised under `true` in `DefaultEngineWriteCoordinatorTest` only.
- No revert code path. Phase 3's design choice; A5 documents the known-issue #26 trade-off in test form.

### Repo decisions impact

`No` — feature-internal. The wrap pattern is now exemplified for one step; 3c (`fub_move_to_pond`) follows the same shape. If a generic `FubMutatingStep` SPI ever materializes (deferred per `plan.md` §"Out of scope"), promote then.

---

## Phase 3 — Implementation Log (wrap-up)

Status: `COMPLETE` — all five sub-phases shipped. 682 tests green.

Decision narrative per the AGENTS.md convention: *why was it built this way?* — git is the source of truth for what changed. 3a and 3b have their own detailed logs ([implementation-log.md](./implementation-log.md), [implementation-log.md](./implementation-log.md)); this file records 3c–3e and the phase-level outcome.

### What Phase 3 delivered

Every engine-originated FUB write now flows through `EngineWriteCoordinator`, which records the intent on `InMemoryEngineWriteTracker` so the resulting echo webhook is either **suppressed** (scalar modes) or **annotated** `source=ENGINE` (tracker-only modes). All of it is dormant behind `engine.write.emit-events=false` — Phase 4 flips the flag and wires the first consumer. The bad-run-rate win arrives in Phase 4; Phase 3 is pure substrate.

| Sub-phase | Step | Mode | Echo handling | Scenarios |
|---|---|---|---|---|
| 3b | `fub_reassign` | scalar (local-first) | echo diffs to empty → **0 events** | A1–A7 |
| 3c | `fub_move_to_pond` | scalar (local-first) | echo diffs to empty → **0 events** | A1/A3/A4/A5 (pond) |
| 3d | `fub_add_tag` | tracker-only append | echo emits **1 annotated** event | C1–C3 |
| 3e | `fub_create_note` | tracker-only entity-create | `note.created` echo emits **1 annotated** event | D1/D3/D4 |

The headline behavioural split a reviewer must hold: **scalar mode suppresses to zero; tracker-only modes emit one annotated event.** Different success criteria — scalar writes local first (so the echo is a no-op diff); tracker-only modes never write local optimistically (so the echo legitimately diffs and is merely stamped).

### 3c — `fub_move_to_pond` (no surprises)

A verbatim mirror of 3b for `assignedPondId`. No new pattern; no separate decision log. The only carry-over gotchas (both from 3b): the coordinator's `findBy…ForUpdate` requires a pre-existing local person row, and test echo payloads must be built by JSON parsing (not `.put(long)`) so numeric node types match `PersonDiffComputer`'s type-strict equality.

### 3d — `fub_add_tag` (first tracker-only path)

`tags` is an accumulating field. Optimistic local-state-first writes are structurally unfixable here (race-matrix C2: a concurrent external tag change landing before our FUB PUT fabricates a phantom "tag removed" event). So 3d inverts the ordering: **FUB first, record on the tracker only on success, never write local.** Local catches up when FUB's echo arrives. C2 proves the payoff — an external concurrent add stays a real unannotated event and there is no phantom removal. Cost: one annotated event per engine tag-add (vs. zero in the rejected optimistic model), accepted in exchange for eliminating the phantom class.

### 3e — `fub_create_note` (single channel — the plan changed mid-flight)

3e was planned as **two channels**: the `note.created` echo *and* a person-side `peopleUpdated` echo (assumed to carry `lastNoteAt`). Before implementing, the person-side assumption was investigated and **confirmed false** three independent ways:

1. **Empirical** — creating notes produced no `peopleUpdated` webhook.
2. **FUB API docs** — `peopleUpdated`'s trigger field list excludes note activity; note creation fires only `notesCreated`.
3. **Code** — `lastNoteAt`/`lastActivity` are in neither `SNAPSHOT_FIELDS` nor `PersonDiffComputer`, so even a hypothetical person echo would diff to empty and emit nothing.

With no person event ever produced, the person-side channel is inert. 3e shipped **single-channel** (`note.created` only). Consequences:

- The coordinator's `applyEntityCreateTrackedOnly` keeps its `SideEffectRecorder` callback (over-general for one channel, but zero churn to tested infra — the recorder is a one-line lambda recording the note entry, guarded against a null note).
- **D2 was dropped** — there is no person-side echo to test. Deliberately **no tripwire/disabled test** stands in for it: a unit test cannot detect FUB changing its webhook semantics. The "remember this" job goes to durable docs instead — a code comment at `PersonUpsertService.SNAPSHOT_FIELDS`, known-issue #27, and a Phase 4 exit criterion.
- Note annotation lives in `NoteEmissionService`, **not** the universal `DomainEventEmitter` hook — that hook keys off `payload.changed_fields`, which note webhooks don't carry. The annotation deep-copies the payload on a hit (so it never leaks `source` onto sibling notes in a multi-`resourceId` webhook) and passes the original reference through on a miss.
- **D3 (early-echo race) retained** as a diagnostic: if the `notesCreated` webhook beats the `createNote` POST response, the tracker record doesn't exist yet and the echo emits unannotated. Documented, not fixed (a content-hash key would close it; deferred until observed). Made deterministic by resetting the fake client's note-id sequence per test.

Full narrative and the three-way confirmation: [`plan.md`](./plan.md) §3e + the 2026-06-01 changelog.

### Cross-cutting notes

- **The smoking-gun lock discipline holds for all scalar wraps.** A2 (3b) proves the row lock is released before the FUB call via `REQUIRES_NEW`; 3c reuses the same coordinator path, so it inherits the proof rather than re-testing it.
- **No production behaviour changed.** `agent_followup_enforcement` still runs on the old webhook-shaped trigger and consumes no events. The wraps are invisible until Phase 4.
- **Phase 4 prerequisites surfaced here:** (a) the in-memory tracker's crash-window must be re-evaluated (Redis-backed impl) once a real consumer exists; (b) `notesCreated` must be added to `config/fub-webhook-events.txt` and webhooks re-synced for the note channel to deliver live (blocked on restoring prod FUB credentials, found invalid 2026-06-01); (c) verify the #27 note-trigger filter against real traffic.

### Repo decisions impact

`No` — feature-internal. The coordinator op-mode pattern and gated-emission marker remain local conventions. Promote to `Docs/repo-decisions/` only if a future feature replicates them.

---

## Phase 4 — Implementation log (the rail switch)

> **Status:** Done (2026-06-03). Shipped as sub-phases 4a → 4b → 4c → 4d.1 → 4d.2. This is the decision narrative; the commit-level breakdown is in [`plan.md`](./plan.md). Phase 4 is the headline phase — workflows stop reacting to raw webhooks and subscribe to typed domain events; the bad-run-rate win lands here.

### What Phase 4 delivered

Workflows now consume **typed domain events** ("Rail 2") instead of raw webhooks ("Rail 1", deleted). The one production workflow (`agent_followup_enforcement`) was re-authored to `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' and change.assignedUserId.changed" }`. Replaying the recorded incident bursts end-to-end, the bad-run rate drops from ~50% to <5% — each burst now collapses to one run.

### The sub-phases

- **4a — substrate.** Added `workflow_runs.domain_event_id` (Flyway V23) and plumbed it through `WorkflowPlanRequest`/`WorkflowExecutionManager`. The originally-planned `suppressed_by_run_id` was dropped (Phase 5 became supersede, not a run-to-run link).
- **4b — trigger type + scope.** `DomainEvent` gained an `id`; `DomainEventScopeBuilder` builds the trigger-time JSONata scope (`event.*`, `change.*`, `current.*`, `event.origin`, `person.*`), reused at step time so trigger- and step-time scopes can't drift. `DomainEventTriggerType` matches `{on, filter}` against an event and extracts entities.
- **4c — validator.** `DomainEventTriggerValidator` validates `{on, filter, reactToEngineEvents}` and exposes `knownEventKinds()` for the admin `/trigger-types` surface.
- **4d.1 — engine-echo gate (RD-006).** `EngineEchoGate`: `shouldExclude = engineCaused AND NOT (globalCapability AND reactToEngineEvents)`, both default OFF. Flipped `engine.write.emit-events` on; added the `engine.events.workflow-consumption.enabled` platform-capability flag.
- **4d.2 — the switch.** `WorkflowTriggerRouter` became a `DomainEventListener` (`onEvent → route(DomainEvent)`); `WebhookEventProcessorService` no longer routes (emission + after-commit dispatch is the only path to a run). Deleted Rail 1 entirely: `FubWebhookTriggerType`, `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `TriggerMatchContext` (+ tests). Admin/service layers now validate and surface the `{on, filter}` shape.

### Key decisions & surprises

- **Listener wiring caused a startup cycle.** Registering `WorkflowTriggerRouter` as a `DomainEventListener` created `DomainEventEmitter → Dispatcher → (listeners) Router → WorkflowExecutionManager → step types → EngineWriteCoordinator → DomainEventEmitter`. Broken with `@Lazy` on `InMemoryDomainEventDispatcher`'s `List<DomainEventListener>` (listeners aren't needed until dispatch, always post-startup).
- **`event.origin` collision.** `source` is both a diffable person field and the engine-write annotation key. Exposed provenance as `event.origin` (always `ENGINE`/`EXTERNAL`, never absent — an absent value would make `event.origin != 'ENGINE'` evaluate to JSONata *undefined* and silently drop real events).
- **known-issue #31 — the after-commit transaction gap (caught by the replay harness).** Under Rail 1 the router ran inside the live webhook transaction; under Rail 2 it runs in the `afterCommit` hook, where the committing transaction is still bound. `plan()` was `@Transactional` (REQUIRED) → it joined the completing transaction → `saveAndFlush` threw "No active transaction" → **zero runs would have been created in production**. Fixed with `@Transactional(REQUIRES_NEW)` on `plan()`. The replay harness replaying real bursts surfaced this (0 runs where 1 was expected) — exactly its job.

### Validation

The replay harness (`ReplayHarnessTest`) replays the five recorded incident fixtures end-to-end; each burst collapses to exactly one run. Full suite green at the cutover. See [`README.md`](./README.md) Phase 4 and the bad-run-rate evidence in [`plan.md`](./plan.md) §validation.

### Repo decisions impact

`Yes` — enforces **RD-006** (engine-echo exclusion, safe-by-default) via `EngineEchoGate` + the `reactToEngineEvents`/`workflow-consumption.enabled` two-level gate. No new RD created.

---

## Phase 5 — Implementation log (run-collision handling)

> **Status:** Done (2026-06-03). Closes the domain-events feature. Built as **field-aware supersede-at-plan-time**, a re-rescope from the cancel-only spec — see the `README.md` changelog and known-issue #29.

### What Phase 5 delivered

When a newer triggering event arrives for a `(workflow, person)` that already has an in-flight run, the stale run is **superseded** (cancelled, `reason_code = SUPERSEDED_BY_NEWER_EVENT`, attributed to the newer `domain_event_id`) and the newer run — which the event already spawned under Rail 2 — proceeds to enforce the newest state. One run, on the freshest data, no stale/duplicate action.

### The decisions that shaped it

- **Cancel-only → supersede.** The spec called for cancel-only ("cancel the stale run, start nothing") to avoid building supersede's restart machinery. But under Rail 2 the replacement run **already exists** — the newer event passed the filter and spawned its own run. So "supersede" (cancel stale, newer enforces) costs the same as cancel-only while *enforcing* the newest change instead of dropping it. Cancel-only's accepted limitation ("newer change unenforced") simply evaporates.
- **No freshness gate.** The original "ideal" paired supersede with an action-step freshness re-check, for the edge where a cancelled run had already acted. That edge doesn't arise here: the stale run's irreversible actions sit behind 3-/30-min waits, and supersede cancels it the moment the newer event is processed — always before it acts. Investigated and confirmed; the freshness gate was designed and then dropped as unnecessary.
- **Field-aware, via `changed_fields` overlap.** A newer event supersedes an in-flight run only when their `changed_fields` intersect. This was the answer to a real question: "a phone change shouldn't cancel an assignment-premised run." Overlap on the change-set is the precise gate — and it's *better* than an entity-type check, because only `person.state_changed` carries `changed_fields` (so calls/notes/`person.created` are excluded for free, and `person.created` — a person event with no transition — is correctly excluded where an `entityType == person` check would wrongly include it).
- **Extracted `RunSupersedePolicy`** rather than inlining the decision in `plan()`. Keeps `plan()` thin and documents the entity-generalization seam: supersede is person-scoped because person is the only stateful entity today (`workflow_runs.source_person_id` is the run's only identity). If a second entity ever emits `*.state_changed`, generalize the identity key in one place; the field-overlap logic carries over unchanged. We explicitly chose *not* to generalize the run model now (YAGNI — no consumer, and it would be schema surgery).
- **Reused the cancel internals.** `WorkflowRunControlService.supersede` shares `finalizeCanceled` with the operator `cancelRun` (step-skip + status set). The due-worker's `runs.status = PENDING` claim filter and the executor's run-status re-check already stop a cancelled run cleanly — no new stop machinery. The spec's mooted `BLOCKED`-guard relaxation was dropped: `BLOCKED` is never set; active = `PENDING`, which the cancel path already permits.

### Surprises

- **`BLOCKED` is dead.** The spec assumed waiting runs are `BLOCKED` and that the cancel guard needed relaxing to reach them. In reality a run stays `PENDING` throughout; dependency-waiting lives at the *step* level (`WAITING_DEPENDENCY`). So "active run" = `PENDING`, and the existing guard already permits cancelling it.
- **Ordering matters.** Supersede runs *after* the idempotency check, so a webhook replay (same idempotency key → `DUPLICATE_IGNORED`) never supersedes; it only fires for genuinely new events.

### Accepted residual

Truly-simultaneous same-person events can both miss each other's not-yet-committed run (no lock / no partial unique index) → two runs proceed. Doubly-rare after Phases 2–4; tracked in known-issue #29 with a revisit-trigger (partial unique index or a per-step freshness backstop if the data ever justifies it).

### Validation

- `RunSupersedePolicyTest` (unit) — overlap supersedes; non-overlap and no-`changed_fields`/no-person no-op; only overlapping runs among many.
- `WorkflowRunControlServiceTest` (unit) — supersede cancels `PENDING` with reason + `domain_event_id` + step-skip; no-ops on non-`PENDING`/missing.
- `WorkflowTriggerRouterIntegrationTest` (Testcontainers) — overlapping events supersede; non-overlapping both survive; per-`workflow_key` scoping.
- Full suite green (715 run, 2 skipped).

### Repo decisions impact

`No` — collision handling is feature-internal. It flips the documented choice in known-issue #29 (cancel-only/deferred-supersede → adopted field-aware supersede; the residual is now the simultaneity race), updated in place. No new or changed `Docs/repo-decisions/` entry.
