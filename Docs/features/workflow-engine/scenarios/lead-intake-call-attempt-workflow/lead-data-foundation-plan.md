# Lead Data Foundation — Phased Plan

## Status
- Active. Supersedes the Phase 2 content in [plan.md](plan.md) and reframes it as a data-foundation effort rather than a step-only effort.
- Goal: complete the local data foundation by wiring up the currently-dormant `leads` table and enriching existing call persistence, so future step work can be built on reliable local facts.
- Implementation progress (2026-04-17):
  - Phase A: complete
  - Phase B: complete
  - Phase C: dropped from this phase (no step delivery)
- Validation (2026-04-17): `./mvnw -q test` -> `305 tests, 0 failures, 0 errors, 0 skipped`.

## Problem Statement
The data required to answer "did the lead and the agent talk?" exists in pieces across the system but is never joined:
- [`leads`](../../../../../src/main/resources/db/migration/V14__create_leads_table.sql) table exists but has no entity, repository, or ingestion wiring.
- [`processed_calls`](../../../../../src/main/resources/db/migration/V2__create_processed_calls.sql) persists processing status but drops the FUB `CallDetails` facts (person linkage, duration, outcome, direction, timestamps).
- [`webhook_events`](../../../../../src/main/resources/db/migration/V1__create_webhook_events.sql) has a `source_lead_id` column that [`FubWebhookParser`](../../../../../src/main/java/com/fuba/automation_engine/service/webhook/parse/FubWebhookParser.java) currently hardcodes to `NULL` for assignment events.
- [`FollowUpBossClient#checkPersonCommunication`](../../../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java) only reads `person.contacted > 0` — a blunt counter, not evidence of an actual agent conversation.

Without a durable join between a lead and the calls associated with it, communication checks cannot be made reliably from local data.

## Locked Decisions
| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **`leads` is the only identity entity.** No separate `persons` table. | `persons` is a FUB-internal concept; our platform should not leak it. |
| 2 | **`source_lead_id` = raw FUB `personId`** (e.g., `"19355"`). Global identity is the composite `(source_system, source_lead_id)`. | Matches existing V14 schema; `peopleCreated.resourceIds[0]` == `personId` (confirmed against live FUB payload on 2026-04-17). |
| 3 | **No new `lead_calls` table.** Extend existing `processed_calls` with the fields we need. | Minimum effort. Reuse the table that already holds one row per call. A dedicated `lead_*` content table can come later if SMS/email join the picture. |
| 4 | **No new workflow step in this phase.** `wait_and_check_communication` remains unchanged, and `check_conversation_success` is dropped. | Keeps Phase 2 focused on data foundation only. |
| 5 | **Forward-only.** No backfill of existing `processed_calls` rows. | User confirmed; historical rows are ledger-only. |
| 6 | **Lead rows are created on `peopleCreated` / `peopleUpdated` only, and only when person payload has `stage == "Lead"`.** | Prevents non-lead people records from entering the `leads` table. |
| 7 | **Calls without a matching lead row are still persisted.** Emit warning log only. | Preserves call ledger auditability despite event-order gaps. |

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
     - Lead classification gate: treat payload as lead only when `stage` equals `"Lead"`.
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
   - If the matching `leads` row is absent: log a warning (`lead-missing-on-call`) and continue persisting the call facts. Lead-missing does not block call ledger writes.
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

## Phase C — Dropped from this plan
No step-creation work is delivered in this phase. `check_conversation_success` was explored and then intentionally removed; step work moves to a later phase with explicit scope.

---

## Phase D (deferred / optional) — Attempt Counter Primitive
A mechanism to bound workflow retry loops (e.g., "call up to 3 times, then move to pond"). Small persistent counter keyed by `(workflow_run_id, step_id, source_lead_id)` + a branch guard step. **Do not build yet.** Revisit once a real workflow graph requires bounded retries.

---

## Cross-Cutting Notes

### Identity
- `source_system = "FUB"` constant for now. If a second source system ever appears, it becomes a runtime value on the webhook ingress path.
- `source_lead_id` is stored as string even though FUB `personId` is numeric — future-proof for non-numeric source IDs.

### Failure modes
- Call arrives, no matching lead row: log + persist call row with `source_lead_id` anyway (audit).
- Duplicate webhook (same `call_id` or same person event): upsert/idempotency already in place; new columns are computed from the latest payload each time.

### What's explicitly NOT in scope
- SMS/email communication tables.
- Creating `check_conversation_success` (or any new workflow step).
- Refactoring `WaitAndCheckCommunicationWorkflowStep`.
- Attempt counter.
- Backfilling historical `processed_calls`.
- Removing the `checkPersonCommunication` FUB client method.

## Relation to Existing Docs
- [plan.md](plan.md) — high-level scenario plan. Phase 2 content there is effectively realized by Phases A+B of this doc.
- [phases.md](phases.md) — running phase tracker. Phase 2 completion here maps to A+B only.
- [research.md](research.md) — background research; unchanged.
- [check-communication-success-step-plan.md](check-communication-success-step-plan.md) — superseded exploration retained for traceability; not implemented in this phase.
