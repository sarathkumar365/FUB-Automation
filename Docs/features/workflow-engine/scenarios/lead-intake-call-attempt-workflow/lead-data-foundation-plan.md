# Lead Data Foundation — Phased Plan

## Status
- Active. Supersedes the Phase 2 content in [plan.md](plan.md) and reframes it as a data-foundation effort rather than a step-only effort.
- Goal: enable a new workflow step that can decide "has this lead been successfully contacted by an agent?" from local data, by wiring up the currently-dormant `leads` table and enriching existing call persistence — with minimum engineering effort.
- Implementation progress (2026-04-17):
  - Phase A: complete
  - Phase B: complete
  - Phase C: pending

## Problem Statement
The data required to answer "did the lead and the agent talk?" exists in pieces across the system but is never joined:
- [`leads`](../../../../../src/main/resources/db/migration/V14__create_leads_table.sql) table exists but has no entity, repository, or ingestion wiring.
- [`processed_calls`](../../../../../src/main/resources/db/migration/V2__create_processed_calls.sql) persists processing status but drops the FUB `CallDetails` facts (person linkage, duration, outcome, direction, timestamps).
- [`webhook_events`](../../../../../src/main/resources/db/migration/V1__create_webhook_events.sql) has a `source_lead_id` column that [`FubWebhookParser`](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/parse/FubWebhookParser.java) currently hardcodes to `NULL` for assignment events.
- [`FollowUpBossClient#checkPersonCommunication`](../../../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java) only reads `person.contacted > 0` — a blunt counter, not evidence of an actual agent conversation.

Without a durable join between a lead and the calls associated with it, any "communication success" step has to fall back to external FUB reads on every tick.

## Locked Decisions
| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **`leads` is the only identity entity.** No separate `persons` table. | `persons` is a FUB-internal concept; our platform should not leak it. |
| 2 | **`source_lead_id` = raw FUB `personId`** (e.g., `"19355"`). Global identity is the composite `(source_system, source_lead_id)`. | Matches existing V14 schema; `peopleCreated.resourceIds[0]` == `personId` (confirmed against live FUB payload on 2026-04-17). |
| 3 | **No new `lead_calls` table.** Extend existing `processed_calls` with the fields we need. | Minimum effort. Reuse the table that already holds one row per call. A dedicated `lead_*` content table can come later if SMS/email join the picture. |
| 4 | **New step is a new step.** Do **not** refactor [`WaitAndCheckCommunicationWorkflowStep`](../../../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/WaitAndCheckCommunicationWorkflowStep.java). Leave it; add a pointer comment. | Keeps scope tight. Existing step is already production-wired. |
| 5 | **Contact-success rule = connected outbound OR inbound callback** within a lookback window. Configurable at **code level only**, not DB. | Avoids premature policy abstraction; toggleable via constants/bean. |
| 6 | **Forward-only.** No backfill of existing `processed_calls` rows. | User confirmed; historical rows are ledger-only. |
| 7 | **Lead rows are created on `peopleCreated` / `peopleUpdated` only.** Calls for an unknown lead fail loud (log + skip). | FUB event ordering places `peopleCreated` before `callsCreated`. Silent stubs would hide ordering bugs. |

## Current-State Snapshot (factual)
| Concern | State Today |
|---|---|
| `leads` table | Exists ([V14](../../../../../src/main/resources/db/migration/V14__create_leads_table.sql)). Orphan — no entity/repo/service. |
| Lead ingestion | None. `peopleCreated`/`peopleUpdated` route to workflows only ([`WebhookEventProcessorService:141`](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java#L141)). |
| `webhook_events.source_lead_id` | Column present, populated as `NULL` by parser. |
| Call persistence | `processed_calls` stores status, rule, task_id, retry, raw_payload. Drops `personId`, `duration`, `outcome`, `userId`. |
| `CallDetails` DTO | Exposes `id, personId, duration, userId, outcome`. Missing `isIncoming` and timestamps. |
| FUB client call fetch | [`FubFollowUpBossClient#getCallById`](../../../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java#L57) maps only the five fields above. |
| Lead ↔ call join | Not possible in SQL; `personId` is ephemeral runtime state. |
| Existing comm-check step | [`WaitAndCheckCommunicationWorkflowStep`](../../../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/WaitAndCheckCommunicationWorkflowStep.java) uses `checkPersonCommunication` (FUB read every tick). |
| Attempt counter | None. `workflow_run_steps.retry_count` exists but isn't used for business attempts. |

## Target Shape
```
              leads   (hub, keyed by source_system + source_lead_id)
                │
                │   (join on source_lead_id)
                ▼
         processed_calls   (extended with lead linkage + call facts)
```
Same pattern extends later to SMS/email by giving each channel its own table keyed by `source_lead_id`. Out of scope here.

---

## Phase A — Wake up the `leads` table

### Intent
Make every FUB person/lead event leave a durable row in `leads` keyed by `(source_system, source_lead_id)`. No workflow behaviour changes.

### Work items
1. **Entity + repository**
   - `LeadEntity` mapped to existing `leads` schema (V14). Fields: `id`, `sourceSystem`, `sourceLeadId`, `status`, `leadDetails` (JSONB), timestamps.
   - `LeadRepository extends JpaRepository<LeadEntity, Long>` with `findBySourceSystemAndSourceLeadId(...)`.
2. **Upsert service**
   - `LeadUpsertService.upsertFromAssignmentEvent(normalizedEvent, fetchedPerson)`:
     - Key: `(source_system="FUB", source_lead_id=resourceIds[0])`.
     - `lead_details` JSONB = minimal snapshot (name, stage, assignedUserId, claimed, tags, phones, emails) from the person payload.
     - Idempotent; updates `last_synced_at` and mutable fields; leaves `created_at` alone.
3. **Wire into webhook processing**
   - In [`WebhookEventProcessorService.processAssignmentDomainEvent`](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java#L141), after workflow routing, call `LeadUpsertService` for `peopleCreated` / `peopleUpdated`.
   - Fetch person payload via existing FUB client path (add `getPersonById` usage if not already present for this case).
4. **Parser fix**
   - In [`FubWebhookParser:73`](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/parse/FubWebhookParser.java#L73), populate `source_lead_id` from `resourceIds[0]` for assignment-domain events. Remove the TODO.

### Migrations
- None. V14 schema is sufficient.

### Validation
- Repository slice test: upsert twice → one row, `updated_at` advances, `created_at` stable.
- Service test: assignment event payload → expected `lead_details` JSON shape.
- Integration test: ingest real `peopleCreated` payload → lead row present, `webhook_events.source_lead_id` populated.

### Exit criteria
- Every `peopleCreated`/`peopleUpdated` webhook results in a `leads` row.
- `webhook_events.source_lead_id` no longer NULL for assignment events.
- No change observable in workflow execution.

---

## Phase B — Extend `processed_calls` with lead linkage and call facts

### Intent
Persist the FUB `CallDetails` fields needed to answer "did a real conversation happen?" and make every call row joinable to `leads` by `source_lead_id`.

### Work items
1. **Migration V15 — add columns to `processed_calls`**
   - `source_lead_id VARCHAR(255)` — raw FUB `personId` as string, matches `leads.source_lead_id`.
   - `is_incoming BOOLEAN` — from FUB call detail `isIncoming`.
   - `duration_seconds INTEGER` — from `CallDetails.duration`.
   - `outcome VARCHAR(64)` — from `CallDetails.outcome`.
   - `call_started_at TIMESTAMPTZ` — from FUB `created` on the call detail.
   - Index: `idx_processed_calls_lead_started` on `(source_lead_id, call_started_at DESC)`.
   - All columns nullable (backfill-free; historical rows stay untouched).
2. **Extend `CallDetails` DTO**
   - Add `isIncoming` and `createdAt` (Instant) to the record.
   - Extend the FUB JSON mapping in [`FubFollowUpBossClient#getCallById`](../../../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java#L57) to read `isIncoming` and `created`.
3. **Persist on call processing**
   - In [`WebhookEventProcessorService.processCallDomainEvent` (~line 179)](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java#L179), when persisting the `ProcessedCallEntity`, populate the new fields from `CallDetails`. `source_lead_id = String.valueOf(callDetails.personId())`.
   - If the matching `leads` row is absent: log a warning (`lead-missing-on-call`) and continue persisting the call facts. Lead-missing does not block call ledger writes. (This contradicts Decision #7's "fail loud" for step evaluation; here we still write the row for audit, but the step in Phase C will ignore orphan calls.)
4. **No processing-ledger semantics change**
   - `status`, `rule_applied`, `task_id`, `retry_count`, `raw_payload` behave exactly as today.

### Validation
- Migration test: columns present, nullable, index created.
- Mapping test: `CallDetails` JSON sample → `isIncoming` and `createdAt` populated.
- Integration test: `callsCreated` webhook → new fields populated on the resulting `processed_calls` row; existing ledger behaviour unchanged.

### Exit criteria
- Every new `processed_calls` row carries lead linkage + call facts.
- A SQL join `leads JOIN processed_calls ON source_lead_id` returns meaningful rows.

---

## Phase C — New workflow step: `check_communication_success`

### Intent
Introduce a new, **local-only** workflow step that answers "has this lead and an agent actually talked?" by querying `processed_calls`, with no FUB fallback. CORRECTION - NEED A FALLBACK.

### Work items
1. **Query component**
   - `LocalCallEvidenceRepository` (or method on `ProcessedCallRepository`):
     - `findEvidence(sourceLeadId, lookback)` → latest inbound timestamp, latest connected outbound (duration ≥ threshold), counts.
2. **Step implementation**
   - New class `CheckCommunicationSuccessWorkflowStep implements WorkflowStepType`.
   - Registered in [`WorkflowStepRegistry`](../../../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepRegistry.java).
   - Inputs: `sourceLeadId` (resolved from `RunContext`), optional `lookbackMinutes` override.
   - Rule (inline, code-level constants):
     ```
     static final Duration DEFAULT_LOOKBACK = Duration.ofMinutes(30);
     static final int MIN_CONNECTED_OUTBOUND_SECONDS = 30;
     // success = (outbound && duration >= threshold) OR inbound within lookback
     ```
   - Result codes: `COMM_FOUND`, `COMM_NOT_FOUND` (match existing contract).
   - No FUB calls. If no `processed_calls` rows exist for the lead in the lookback: `COMM_NOT_FOUND`.
3. **Comment on the existing step**
   - Drop a `// NOTE:` comment on [`WaitAndCheckCommunicationWorkflowStep`](../../../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/WaitAndCheckCommunicationWorkflowStep.java) pointing to `CheckCommunicationSuccessWorkflowStep` as the preferred local-data pattern, flagging a future refactor. Do not change behaviour.
4. **Admin catalog**
   - Register step in the admin step catalog so it's selectable in workflow graphs. Match metadata shape used by existing entries.

### Validation
- Unit tests on the rule: outbound-connected ✓, outbound-not-connected (duration below threshold) ✗, inbound ✓, no rows ✗, rows outside lookback ✗.
- Step execution test with seeded `processed_calls` rows.
- Zero external HTTP calls asserted in the step's test path.

### Exit criteria
- New step is invocable from a workflow graph.
- Decision derives entirely from local DB.
- Existing `WaitAndCheckCommunicationWorkflowStep` untouched.

---

## Phase D (deferred / optional) — Attempt Counter Primitive
A mechanism to bound workflow retry loops (e.g., "call up to 3 times, then move to pond"). Small persistent counter keyed by `(workflow_run_id, step_id, source_lead_id)` + a branch guard step. **Do not build yet.** Revisit once a real workflow graph requires bounded retries.

---

## Cross-Cutting Notes

### Identity
- `source_system = "FUB"` constant for now. If a second source system ever appears, it becomes a runtime value on the webhook ingress path.
- `source_lead_id` is stored as string even though FUB `personId` is numeric — future-proof for non-numeric source IDs.

### Failure modes
- Call arrives, no matching lead row: log + persist call row with `source_lead_id` anyway (audit), step in Phase C treats as `COMM_NOT_FOUND`.
- Duplicate webhook (same `call_id` or same person event): upsert/idempotency already in place; new columns are computed from the latest payload each time.

### What's explicitly NOT in scope
- SMS/email communication tables.
- Refactoring `WaitAndCheckCommunicationWorkflowStep`.
- Attempt counter.
- Backfilling historical `processed_calls`.
- Removing the `checkPersonCommunication` FUB client method.

## Relation to Existing Docs
- [plan.md](plan.md) — high-level scenario plan. Phase 2 content there is effectively realized by Phases A+B+C of this doc. Once this plan is executed, update `plan.md`'s Phase 2 section to reference this doc as the implementation of record.
- [phases.md](phases.md) — running phase tracker. Add entries A/B/C as they land.
- [research.md](research.md) — background research; unchanged.
- [check-communication-success-step-plan.md](check-communication-success-step-plan.md) — previously superseded plan. This doc reinstates the step, but now backed by local data instead of new FUB reads.

## Open Implementation-Time Questions
1. When a `peopleUpdated` event fires but the person payload contents are unchanged, should `last_synced_at` still advance? (Leaning yes — audit of sync cadence.)
2. FUB call detail `outcome` vocabulary: which values count as "connected" vs "no answer"? Pin the list before wiring the rule in Phase C.
3. Should the step accept a per-instance `lookbackMinutes` input, or only use the code-level default? (Leaning: accept override, default to the constant.)
