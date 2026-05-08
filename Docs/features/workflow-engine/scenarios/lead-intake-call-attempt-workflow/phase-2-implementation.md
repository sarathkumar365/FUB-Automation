# Lead Intake Call-Attempt Workflow — Phase 2 Implementation

## Status
- Completed (updated 2026-04-17).
- Scope follows [lead-data-foundation-plan.md](lead-data-foundation-plan.md).
- Completed slices: Phase A and Phase B.
- Phase 2 is complete.

## Completed on 2026-04-17

### Phase A — Wake up `leads` table
- Added lead persistence model and boundary:
  - `LeadEntity`
  - `LeadStatus`
  - `LeadRepository`
- Added `LeadUpsertService` and wired assignment-domain handling so `peopleCreated`/`peopleUpdated` upsert into `leads`.
- Added lead classification guard for assignment ingestion: only upsert when fetched FUB person payload has `stage == "Lead"`.
- Extended parser extraction path so assignment events resolve and persist `source_lead_id` from resource IDs.
- Added/updated tests to validate lead upsert behavior and end-to-end assignment ingestion.

### Phase B — Extend `processed_calls` for lead linkage + call facts
- Added DB migration `V15__extend_processed_calls_with_call_facts.sql`:
  - `source_lead_id`
  - `source_user_id`
  - `is_incoming`
  - `duration_seconds`
  - `outcome`
  - `call_started_at`
  - index: `idx_processed_calls_lead_started`
- Extended call DTO mapping (`FubCallResponseDto`, `CallDetails`, `FubFollowUpBossClient`) to include `isIncoming` and `created`.
- Updated call processing persistence in `WebhookEventProcessorService` to store call facts on `processed_calls`.
- Added lead-missing warning (`lead-missing-on-call`) when call facts arrive before a corresponding lead row is present.
- Added migration regression and integration coverage for new columns/index and persisted call facts.

### Phase C — Removed from this phase
- `check_conversation_success` step work was intentionally dropped.
- No new workflow step was delivered as part of Phase 2.

## Validation Run
- Command: `./mvnw -q test`
- Result: `305 tests, 0 failures, 0 errors, 0 skipped` (pass).

## Next Step (Phase 3)
- Continue with step library expansion from stabilized A/B data foundation.
- Candidate step: `fub_create_task`.
- Include structured task payload mapping for attempt metadata.
