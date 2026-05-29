# Domain Events — Phases

> **Plan-lock changelog (2026-05-28, late-day):** Final fresh-eyes audit before starting Phase 2. This is the last revision to the roadmap; subsequent surprises go into phase implementation logs, not this file.
> - **Phase 2 restructured into 5 sub-phases.** A new **2b (pure refactor, no behaviour change)** is inserted between scaffold and emission: extracts `CallUpsertService.persistCallFacts` so the call/note emission paths are `@Transactional` by construction; restructures `PersonUpsertService` to capture-old / apply-new shape; uses `findBy…ForUpdate` at **both** the primary finder and the `DataIntegrityViolationException` recovery re-read (closes the brand-new-row insert-race window). 2c/2d/2e land on a structure where the previously-uncaught defects are impossible to write. See [`phase-2-plan.md`](./phase-2-plan.md).
> - **Phase 2 replay-harness assertions changed from `min*` to `expected*` (exact counts)** for collapse fixtures. `min: 1` silently accepts a broken collapse that produces 3 events. Append events keep `min` (no uniqueness claim).
> - **Phase 3 wraps `fub_add_tag` too** — five FUB-mutating step types exist in the codebase; the original list of three missed `fub_add_tag`, which mutates `person.tags` and produces an echo. Without wrapping it, every tag-adding workflow self-triggers.
> - **Phase 3 revert semantics clarified** in `plan.md` §5: revert means *restore the captured prior snapshot of affected fields*, not *undo the delta we applied*. Equivalent for scalars; materially different for accumulating fields (`tags`, `phones`, `emails`).
> - **Phase 4 adds a new `DomainEventTriggerType`** alongside the retiring `FubWebhookTriggerType`; `WorkflowTriggerRouter` gains a `route(DomainEvent)` overload registered as a listener on `DomainEventDispatcher`. The "hard cut" framing was under-scoped — these are concrete new classes, not just a rewire.
> - **Phase 4 validator warns (not refuses) on missing `change.source` predicate** for `person.state_changed` triggers. The `excludeEngineEchoes` opt-in default is per-`plan.md` policy; the warning prevents the silent regression of issue #23 when a future workflow author forgets the predicate.
> - **Phase 5 partial unique index includes `source_system`** (now `(workflow_key, source_system, source_person_id)`). The `events` table already carries `source_system` from day 1 for future CRM adapters; the run-uniqueness index has to match.
>
> **Earlier changelog (2026-05-28, morning):** Architecture review before starting Phase 2.
> - Pre-Phase-2 rename was under-scoped: the admin read-feed surface (controller, DTOs, read-repo, exception package, HTTP/UI routes) was never enumerated and was still `Lead`-named. Added to deliverables below. The app is not deployed anywhere, so renaming the `/admin/leads` route + admin-ui paths is free.
> - Phase 2 gains two correctness deliverables surfaced by the review: **per-person upsert serialization** (the diff-collapse invariant is not safe under the 2–4 thread async webhook pool without it) and **after-commit event dispatch** (emit row in-tx, fan out after commit — not inline in the write transaction).
> - The durable outbox poller (a `dispatched` flag + crash-recovery job) is explicitly **deferred to a Phase 4 decision** — there are no event consumers until then, and the app isn't running anywhere, so the crash-between-commit-and-dispatch window carries no risk today.

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
Status: `DONE` — Java/backend admin read-feed, SPA routes/API/types, replay fixtures, and workflow JSON are swept to `Person`; `/admin/persons` and `/admin-ui/persons` are canonical. See [phase-pre-2-implementation.md](./phase-pre-2-implementation.md) for details.

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

**Admin read-feed surface rename** (missed in the original scoping — added 2026-05-28):

The initial Pre-Phase-2 deliverable list covered the entity, repository, and core upsert layer, but missed the admin/read-feed surface entirely. `PersonAdminQueryService` was renamed but it still imported and returned `Lead*` types, leaving the substrate internally inconsistent. The app is not deployed anywhere, so renaming the HTTP route and SPA paths is free.

- Controller: `AdminLeadController` → `AdminPersonController`; `@RequestMapping("/admin/leads")` → `/admin/persons`
- DTOs (package `controller/dto/`):
  - `LeadFeedItemResponse` → `PersonFeedItemResponse`
  - `LeadFeedPageResponse` → `PersonFeedPageResponse`
  - `LeadSummaryResponse` → `PersonSummaryResponse`
  - `LeadActivityKind` → `PersonActivityKind`
  - `LeadActivityEventResponse` → `PersonActivityEventResponse`
  - `LeadLiveStatus` → `PersonLiveStatus`
  - `LeadRecentCallResponse` → `PersonRecentCallResponse`
  - `LeadRecentWorkflowRunResponse` → `PersonRecentWorkflowRunResponse`
  - `LeadRecentWebhookEventResponse` → `PersonRecentWebhookEventResponse`
- Read repository: `LeadFeedReadRepository` → `PersonFeedReadRepository`; `JdbcLeadFeedReadRepository` → `JdbcPersonFeedReadRepository`; inner types `LeadFeedReadQuery` → `PersonFeedReadQuery`, `LeadFeedRow` → `PersonFeedRow`
- Service inner type: `PersonAdminQueryService.LeadFeedQuery` → `PersonFeedQuery`
- Exception: package `exception/lead/` → `exception/person/`; `InvalidLeadFeedQueryException` → `InvalidPersonFeedQueryException`
- Admin UI: deep-link route `/admin-ui/leads/{id}` → `/admin-ui/persons/{id}` in `AdminUiController` + the SPA's fetch URLs and router config; the existing comment in `AdminUiController` referencing the old path
- Tests: `AdminLeadsFlowTest` → `AdminPersonsFlowTest` (+ all `/admin/leads` MockMvc paths); `JdbcLeadFeedReadRepositoryTest` → `JdbcPersonFeedReadRepositoryTest`; `LeadScopedTopNRepositoryTest` → `PersonScopedTopNRepositoryTest`
- Cosmetic: stale `leads.person_details` comment in [`PersonUpsertService.java:63`](../../../src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java)

**Workflow JSON sweep** (the one production workflow):
- `agent_followup_enforcement.workflow.json`:
  - `eventDomain: "LEAD"` → `eventDomain: "PERSON"`
  - `{{ lead.assignedUserId }}` → `{{ person.assignedUserId }}`
  - `{{ lead.assignedTo }}` → `{{ person.assignedTo }}`
  - `$boolean(lead.assignedUserId)` (in the `gate_assigned` node) → `$boolean(person.assignedUserId) and person.kind = "LEAD"`
  - **The `person.kind = "LEAD"` predicate is mandatory.** It compensates for the dropped `isFubLeadPerson` ingest filter. Uses our normalized `kind` enum rather than FUB's raw `stage` string.
  - **Known limitation (YAGNI — accepted 2026-05-28):** `mapStageToKind` does **exact** case-insensitive match on `lead` / `agent` / `realtor`. Custom FUB stage labels (`Hot Lead`, `Cold Lead`, `Buyer Lead`, etc.) map to `UNKNOWN` and will **not** fire this workflow. This preserves the pre-rename behaviour of the dropped `isFubLeadPerson` filter (which also matched exactly `"Lead"`). Broaden to token/substring matching only when a tenant actually adopts a custom stage label and needs the workflow to fire — at that point fix `PersonUpsertService.mapStageToKind` and the V21 backfill in lockstep.

**Replay harness sweep:**
- `personSnapshots` field already correctly named — no change
- `ReplayFixture.Expected.minWorkflowRunsForLead` → `minWorkflowRunsForPerson`
- All 5 fixture JSONs swept

**Docs sweep:**
- `plan.md` and `phases.md` (this file) — `lead.*` → `person.*` throughout (already done in plan.md as part of this Pre-Phase-2 prep)
- `known-issues.md` — references to entity names updated; references to `lead.*` JSONata vocabulary updated
- `phase-1-implementation.md` — keep as-is (historical record); top-line note added
- `field-observations.md` — keep as-is (historical record; "person" wording reflects what was observed)

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
- `grep -rE 'Lead[A-Z]|\bleads\b|source_lead_id|lead_details|NormalizedDomain\.LEAD' src/main src/test` (excluding `src/main/resources/db/migration/` — historical migrations correctly keep their original names) returns zero matches in production code; in tests only intentional historical-record comments. The admin read-feed rename above is what closes this; without it, the grep currently reports ~128 matches in `src/main` alone.
- `GET /admin/persons` (renamed from `/admin/leads`) returns the expected shape; admin-ui deep links `/admin-ui/persons/{id}` resolve.

### Exit criteria
- V21 migration applies cleanly to dev DB.
- `persons` table exists with `kind` column populated for every row (backfilled from `person_details.stage`).
- No production code references `LeadEntity`, `LeadRepository`, `LeadUpsertService`, `LeadSnapshotResolver`, `LeadAdminQueryService`, `LeadFeedCursorCodec`, or `NormalizedDomain.LEAD`.
- The `peopleUpdated` webhook arrives → `PersonUpsertService` runs regardless of `stage` value; `kind` is set per the mapping.
- Workflow validator accepts `person.kind` as a known field. (We do **not** add an explicit `lead.<field>` rejection rule: a repo-wide grep confirms no production code, test, or live workflow JSON emits the old `lead.*` vocabulary, so there is nothing to reject. The validator stays single-purpose — recognise the current `person.*` namespace, silently ignore everything else. Decided 2026-05-28 during the post-rename audit.)

### Out of scope (deliberately deferred)
- FUB Users (`/v1/users`) ingestion as Persons — separate feature; closes #19 when done.
- Any actual diff / event-emission work — that's Phase 2.
- Any new behaviour beyond dropping the ingest filter.

### Repo decisions impact
`No` — local feature concern. The rename aligns this feature's substrate with FUB's `/v1/people` terminology but does not introduce a new repo-wide architectural rule.

### Size estimate
~35 files touched (entity/repo/core-service rename) + the new `PersonKind` enum + mapping helper + JSONata-scope wiring + ~15 additional files for the admin read-feed surface (controller, 9 DTOs, read-repo, exception package, admin-ui paths and SPA references, related tests). Mostly mechanical; ~2 days with proper testing including the manual stage=Agent behaviour check.

---

## Phase 2 — Domain events table + diff machinery
Status: `DONE` — all 5 sub-phases shipped (`a9dc258` 2a scaffold → `41044ba` 2b refactor → `26e8103` 2c person events + concurrency stress proof → `f3a8036` 2d call/note append events → `9b73759` 2e replay-harness asserts + `NoteEmissionService` extraction). Collapse claim verified at three levels: unit (`PersonDiffComputerTest`), concurrency on real Postgres (`PersonUpsertConcurrencyStressTest`), end-to-end (`ReplayHarnessTest`). 589 tests pass. One reasoned plan deviation — a `NoteEmissionService` was extracted in 2e to fix a Spring proxy self-invocation bug the harness caught; see [`phase-2-implementation.md`](./phase-2-implementation.md). See [`phase-2-plan.md`](./phase-2-plan.md) for the locked commit-level plan.

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
`emit(eventKind, sourceSystem, sourceEventId, entityType, entityId, payload)` — INSERT the events row inside the caller's transaction (atomic with the state change that produced it), then register an after-commit hook (`TransactionSynchronizationManager.registerSynchronization(...)`) that invokes `DomainEventDispatcher.dispatch(...)` once the transaction commits. **Dispatch must not run inline inside the write transaction** — that would extend lock-hold over the `persons` row across listener work and, in Phase 4, drag workflow planning into the upsert tx. After-commit dispatch keeps emission and consumption decoupled while keeping the event row durable from the moment the state change commits.

**5. `DomainEventDispatcher`** — interface + `InMemoryDomainEventDispatcher`
Single method `dispatch(DomainEvent)`. Holds `List<DomainEventListener>`. No listeners registered in Phase 2. Modeled on the existing `WebhookDispatcher` pattern (no Spring `ApplicationEventPublisher` introduced). Invoked only from the after-commit hook in (4) — never directly from inside a write transaction.

**5a. Per-person serialization of upserts** (correctness prerequisite for the collapse invariant):
- The current `PersonUpsertService.upsertFubPerson` does `findBy → save` with no row lock. Webhooks process on a 2–4 thread async pool ([`WebhookAsyncConfig.java:17`](../../../src/main/java/com/fuba/automation_engine/config/WebhookAsyncConfig.java)), so a FUB burst of 3–4 `peopleUpdated` for the same person can run truly in parallel — every worker reads the same pre-burst state, every worker sees a non-empty diff, every worker emits. The headline collapse claim ("3 webhooks → 1 event") silently fails without a lock.
- Add a `findBySourceSystemAndSourcePersonIdForUpdate(...)` method on `PersonRepository` annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Use it in `upsertFubPerson` **at both call sites**: the primary finder AND the `DataIntegrityViolationException` recovery re-read. Different persons still process fully in parallel (row-level lock, not table-level).
- For brand-new persons (no row to lock on yet) the existing `DataIntegrityViolationException` recovery path becomes correct only when the recovery re-read also uses the locking finder — otherwise concurrent losers of the unique-constraint race re-read without serializing and each emit independently. **Both call sites must use `findBy…ForUpdate`** for the collapse invariant to hold for brand-new persons.

**6. Emission in `PersonUpsertService`** (two event_kinds, decision Q1=B):
- Inside the existing `@Transactional`, holding the pessimistic lock from (5a), between save and return:
  - If `existingOptional.isEmpty()` (brand-new row): emit `person.created` with payload `{ current: <full snapshot> }`. Set `previousState = null`.
  - Else: compute diff via `PersonDiffComputer` against `existing.getPersonDetails()` (already in hand — no extra read). If `changedFields` non-empty: emit `person.state_changed` with payload `{ changed_fields, previous, current }` (only changed fields in `previous`/`current`). Set `previousState = oldDetails`.
  - If diff empty (echo / no-op upsert): no event emitted. `previousState` left alone.
- The lock from (5a) is what makes this correct under burst concurrency: the second worker's `findBy...ForUpdate` blocks until the first worker commits, then reads the already-updated state and finds no diff.

**7. `call.created` emission from extracted `CallUpsertService`**
Sub-phase 2b extracts `WebhookEventProcessorService.persistCallFacts` into a new `CallUpsertService` (in `service/call/`) marked `@Transactional`. Sub-phase 2d injects `DomainEventEmitter` into that service and emits `call.created` after `processedCallRepository.save(entity)`. `source_event_id` is the **webhook** id, not the FUB call id (that's already in `entity_id`). The extraction is surgical — only `persistCallFacts` moves; retry/decision-engine/task-creation stays in `WebhookEventProcessorService`.

**8. Note webhook handling** (decision Q3=B):
- `FubWebhookParser` learns `notesCreated`, `notesUpdated`, `notesDeleted` → maps to `NormalizedDomain.NOTE` + `NormalizedAction.CREATED/UPDATED/DELETED` (the CHECK constraint loosening lands in Pre-Phase-2's V21 migration).
- `WebhookEventProcessorService.process()` gets a `case NOTE -> processNoteDomainEvent(event)` branch.
- `processNoteDomainEvent` is a new **`@Transactional`** private method that emits `note.created` / `note.updated` / `note.deleted` with the webhook payload as the event payload. The `@Transactional` annotation is what makes the emitter's `MANDATORY`-propagation guard pass; without it, the first notes webhook crashes with `IllegalTransactionStateException`.
- **No `notes` table** in Phase 2. Note body content is not fetched from `/v1/notes/{id}`. Workflows that need note content can add a fetch path later. **No `NoteUpsertService`** — there's no state to own, so a service would be empty ceremony.

**9. Replay harness extensions:**
- New `ReplayFixture.Expected` fields:
  - `expectedStateChangeEventsForPerson` (Map<String, Integer>) — `sourcePersonId → **exact** event count`. Exact-count is the only assertion strong enough to catch a broken collapse (a `min: 1` silently passes when the bug produces 3).
  - `expectedCreatedEventsForPerson` (Map<String, Integer>) — `sourcePersonId → **exact** event count`.
  - `minAppendEvents` (Map<String, Integer>) — `event_kind → min count`, e.g. `{"call.created": 1, "note.created": 2}`. `min` is correct here because no uniqueness claim applies to append events.
- Update existing fixtures to assert collapse:
  - `person-20235-fub-burst-2026-05-12`: 3 `peopleUpdated` webhooks → assert `expectedStateChangeEventsForPerson: {"20235": 1}` (exact-1 proves collapse from 3 → 1).
  - `person-20231-fub-burst-2026-05-12`: 4 `peopleUpdated` → assert `expectedStateChangeEventsForPerson: {"20231": 1}`.
  - `person-20123-echo-cascade-2026-05-08`: still produces phantom events under Phase 2 alone (Phase 3 closes); assert `expectedStateChangeEventsForPerson: {"20123": 2}` (1 real + 1 phantom). Phase 3's harness change flips this to exact-1.
- Add one synthesized fixture for note webhooks (no real-DB note webhook in our extracted set yet).

**10. Phase 2 invariants for the harness:**
- For every webhook arrival where local state ends up unchanged → 0 events emitted (collapse).
- For every `peopleCreated` → exactly 1 `person.created` event.
- For every `peopleUpdated` with meaningful diff → exactly 1 `person.state_changed`.
- For every `callsCreated` → 1 `call.created`.
- For every `notesCreated/Updated/Deleted` → 1 corresponding event.
- **Collapse holds under burst concurrency**: when N webhooks for the same person arrive within the same scheduling window of the 2–4 thread async pool, exactly 1 `person.state_changed` event is emitted (proven by the pessimistic lock in deliverable 5a, not by timing luck).
- Engine echoes **still produce phantom `person.state_changed` events** in Phase 2 alone (Phase 3 closes this — the cascade fixtures will assert this is true today and switch the assertion in Phase 3).

**11. Deferred from Phase 2 — durable outbox poller** (revisit at Phase 4):
- A `dispatched` flag on `events` + a scheduled poller that picks up un-dispatched rows after a crash is NOT built in Phase 2.
- Justification: there are no event consumers until Phase 4 (the dispatcher's listener list is empty); the app is not deployed anywhere, so the crash-between-commit-and-dispatch window carries no operational risk today; the poller is cleanly additive (no rewrites of emission code to add it later).
- Decision point: at Phase 4, once `WorkflowTriggerRouter` actually subscribes to the dispatcher, evaluate whether the in-memory dispatcher's loss-on-crash semantics are tolerable for a real consumer. Build the poller then, or accept the gap.

### Verification
- All 528+ tests green.
- All replay fixtures pass (5 existing + 1 new note fixture).
- Person 20235 FUB-burst fixture: 3 webhooks → 1 `person.state_changed` event (collapse proven).
- Person 20123 echo-cascade fixture: still produces a phantom `person.state_changed` event from the echo (Phase 3 will flip this assertion).
- Diff-computer unit tests for each per-field strategy: scalar change, scalar no-op, tag reorder (no-op), tag add, phone reorder (no-op), phone add, phone removal.
- **Concurrency stress test for the collapse invariant**: a deterministic test (separate from the timing-based replay harness) that fires N parallel `upsertFubPerson` calls for the same `sourcePersonId` against a real Postgres (Testcontainers) and asserts the events table contains exactly 1 `person.state_changed` row. Use `CountDownLatch` to release threads simultaneously so the pessimistic lock is genuinely exercised. Without this test, the collapse claim depends on scheduling luck in the replay harness.
- **Dispatch-after-commit unit test**: assert that when `DomainEventEmitter.emit` is called inside a transaction that subsequently *rolls back*, `DomainEventDispatcher.dispatch` is never invoked. And when the transaction commits, dispatch fires exactly once, after the events row is visible to a fresh transaction.

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
- **Wrap engine-write step types** — the **four** FUB-mutating steps that produce a `peopleUpdated` echo: `fub_reassign`, `fub_move_to_pond`, `fub_create_note`, **`fub_add_tag`**. (`fub_create_task` creates a separate task entity and does not echo as a person event, so is excluded.) The list was verified against `service/workflow/steps/Fub*WorkflowStep.java` at plan-lock time.
  - Before FUB call: capture current local Person state for the affected fields as a **prior-snapshot** value; update local state to intended new value; record in tracker
  - Call FUB
  - On FUB failure: **restore the captured prior snapshot of the affected fields** (not "undo our delta" — see `plan.md` §5 for why the distinction matters for accumulating fields like `tags`); mark tracker entry failed; propagate error to step machinery (step fails as today)
  - On FUB success: leave local + tracker in place; echo webhook will diff to empty
- **Diff annotation in Phase 2's emitter:** when `person.state_changed` would be emitted, consult the tracker — if a recent record matches the diff's fields and entity, annotate `payload.source = "ENGINE"`; emit the event anyway (filtering is the workflow's job per the opt-in default)
- **Tracker metrics exposed as structured logs at INFO**: on every engine-write `record` call, on every emission-time `findMatching` call (hit vs miss), and on every eviction. Phase 4's TTL tuning depends on having this data; building it now is cheaper than retrofitting under load.

### Verification
- Replay 05-08 echo cascade: engine reassign → local state updated → echo webhook → diff = empty → no event emitted (verified by harness assertions)
- Replay 05-12 reassign-to-same-user case (person 20235): three back-to-back reassigns produce one tracker record sequence; second and third see local already at target and emit no event
- Failure path: synthesize a FUB-PUT failure in the harness; verify local state reverts and step fails

### Exit criteria
- Echo webhooks (matched by tracker) produce `source = ENGINE` annotation OR no event (depending on diff)
- FUB write failures revert local state cleanly (prior-snapshot restore semantics)
- Tracker metrics logged for observability (hit rate, eviction rate, failed-write rate)
- **No user-visible behaviour change in `agent_followup_enforcement`**. The existing workflow still runs on the old webhook-shaped trigger and does not consume events, so the wrapping has zero effect on bad-run rate until Phase 4 lands. This phase is pure infrastructure for Phase 4 to consume — the bad-run-rate win arrives at Phase 4. A future reviewer asking "why did Phase 3 land if nothing improved?" should find this answer in the exit criteria.

### Repo decisions impact
Probably `No`. The local-state-first write pattern is feature-internal; if it generalises to a project-wide rule for "engine writes always update local first," then promote.

---

## Phase 4 — Trigger schema migration + expression scope
Status: `NOT STARTED`

**Goal:** Workflows subscribe to domain events. New trigger schema in effect. `agent_followup_enforcement` re-authored against the new shape. Bad-run rate actually drops here.

This is the user-facing phase. Everything before it is plumbing.

### Deliverables
- **New trigger schema** in workflow JSON: `{ "on": "<event_kind>", "filter": "<JSONata expression>" }`
- **New `DomainEventTriggerType`** (`service/workflow/trigger/DomainEventTriggerType.java`) implementing `WorkflowTriggerType`, registered alongside the retiring `FubWebhookTriggerType`. Reads `config.on` (event kind) and `config.filter` (JSONata predicate). The two trigger types coexist only briefly — the hard cut deletes the webhook-shaped path at the end of Phase 4 (see exit criteria).
- **Workflow JSON validator** updated: accepts the new `{on, filter}` shape on FUB triggers; rejects the old `peopleUpdated`-typed trigger with a clear migration error message; **warns (not refuses)** when a `person.state_changed` trigger filter does not reference `change.source` — the `excludeEngineEchoes` opt-in default (per `plan.md`) means a forgetful workflow author re-introduces issue #23 silently; the warning is the friction at the right moment. A workflow that genuinely wants to act on engine writes silences the warning by explicitly including `change.source = 'ENGINE'`.
- **`WorkflowTriggerRouter` gains a `route(DomainEvent)` overload** registered as a `DomainEventListener` on `DomainEventDispatcher` (Spring auto-wires the constructor-injected listener list — see Phase 2 dispatcher defaults). The new method:
  - Receives a `DomainEvent`
  - Looks up workflows whose trigger type is `DomainEventTriggerType` AND whose `on` field matches `event.eventKind`
  - Evaluates each workflow's filter expression against the new scope
  - On match, calls `WorkflowExecutionManager.plan` with `domain_event_id` and the proximate `webhook_event_id`
- **Old `route(NormalizedWebhookEvent)` path retires.** At the end of Phase 4, the webhook-shaped path and `FubWebhookTriggerType` are deleted in the hard cut (no production workflow remains on the old shape — `agent_followup_enforcement` is migrated as part of this phase). This is concretely 2 new classes (`DomainEventTriggerType` + listener wiring on the router) and 2 deletions; the deliverable list reflects that, not "router refactored" as a one-line tweak.
- **`workflow_runs.domain_event_id`** column (new) + `workflow_runs.suppressed_by_run_id` column (new, used in Phase 5)
- **Expression scope refactor:**
  - `event.*` now refers to the domain event, not the webhook
  - `change.*` sugar over `event.payload` for `person.state_changed` events
  - `current.*` sugar over `event.payload.current` for both `person.created` and `person.state_changed`
  - `webhook.*` exposes the proximate raw webhook payload (for steps that need source-system fields)
  - `person.*` available in trigger filter scope too (closes #17)
  - `WorkflowStepExecutionService.buildRunContext` updated accordingly
- **Re-author `agent_followup_enforcement`** workflow JSON:
  - Trigger becomes `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' AND change.assignedUserId.changed AND change.source != 'ENGINE'" }` (the `person.kind = 'LEAD'` predicate was already added during the Pre-Phase-2 rename pass; Phase 4 keeps the lead filter and adds the change-based predicates)
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
    ON workflow_runs (workflow_key, source_system, source_person_id)
    WHERE status IN ('PENDING','RUNNING','BLOCKED');
  ```
  `source_system` is included to match the `events` table's day-1 multi-CRM substrate (`plan.md` §"The `events` table"). Without it, person id `100` from FUB and person id `100` from a future second CRM would collide on the same workflow. Costs nothing now; correctness-by-construction for the schema already committed to.
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
