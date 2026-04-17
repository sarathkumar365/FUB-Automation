# Lead Intake Call-Attempt Workflow — Phase 2 Implementation

## Status
- Active (updated 2026-04-17).
- Scope follows [lead-data-foundation-plan.md](lead-data-foundation-plan.md).
- Completed slices: Phase A and Phase B.
- Remaining slice: Phase C.

## Completed on 2026-04-17

### Phase A — Wake up `leads` table
- Added lead persistence model and boundary:
  - `LeadEntity`
  - `LeadStatus`
  - `LeadRepository`
- Added `LeadUpsertService` and wired assignment-domain handling so `peopleCreated`/`peopleUpdated` upsert into `leads`.
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

## Validation Run
- Command: `./mvnw -q test`
- Result: `296` tests run, `0` failures, `0` errors, `0` skipped.

## Next Step (Phase C)
- Implement `check_communication_success` as a local-data step over `processed_calls` evidence.
- Register the step in workflow step registry + admin catalog.
- Keep existing `WaitAndCheckCommunicationWorkflowStep` behavior unchanged; add pointer note only.
