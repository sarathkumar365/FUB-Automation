# Lead Intake Call-Attempt Workflow — Phase Tracker

## Status
- Phase 1 (workflow-only baseline): `READY` (can be configured now)
- Phase 2 (local data read-model foundation): `COMPLETED` (Phases A+B completed on 2026-04-17)
- Phase 3 (step library expansion on local data): `IN_PROGRESS` (`fub_create_task` shipped on 2026-04-17; `wait_and_check_communication` conversational local-first enhancement with incoming/outgoing support shipped on 2026-04-20)
- Phase 4 (attempt counter primitive): `PLANNED`
- Phase 5 (cross-event state machine + hardening): `PLANNED`

## Notes
- Phase planning pivot (2026-04-17):
- Phase 2 has been reset from step-library implementation to local-data-first foundation planning.
- `check_communication_success` implementation attempt was rolled back and deferred until local read-model/query contract exists.
- `check_conversation_success` was scoped out and removed; no new step is shipped in Phase 2.
- Assignment ingestion now gates lead upsert on FUB person payload `stage == "Lead"`.
- See [plan.md](plan.md) for updated phase details.
- Active implementation plan and execution log:
  - [lead-data-foundation-plan.md](lead-data-foundation-plan.md)
  - [phase-2-implementation.md](phase-2-implementation.md)
- Phase 3 implementation log:
  - [phase-3-implementation.md](phase-3-implementation.md)
- Superseded implementation notes retained for traceability in:
  - [check-communication-success-step-plan.md](check-communication-success-step-plan.md)
- Implementation should follow workflow-engine wave sequencing and repo decision workflow.
