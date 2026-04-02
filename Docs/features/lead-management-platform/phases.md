# Phases

## Sprint 0 (RFC Lock Gate)
Status: Completed
Artifacts:
- `rfc-001-normalized-lead-event-contract.md`
- `rfc-002-event-catalog-and-routing.md`
- `rfc-003-lead-identity-mapping-boundary.md`

Gate: Phase 1 implementation must not start unless Sprint 0 remains approved.

## Phase 1
Status: Completed (Step 1, Step 2, Step 3, Step 4, and Step 5 completed)
Log: `phase-1-implementation.md`

## Phase 2
Status: Completed (Step 1 through Step 7 completed)
Log: `phase-2-implementation.md`

## Phase 3
Status: Completed (Step 1 through Step 12 completed)
Log: `phase-3-implementation.md`
Planned scope alignment (assignment SLA example):
- Persist pending checks for assignment events with first checkpoint step `WAIT_AND_CHECK_CLAIM` due in 5 minutes (configurable).
- Persist follow-up checkpoint intent for step `WAIT_AND_CHECK_COMMUNICATION` at +10 minutes after claimed check pass (configurable).
- Materialize runtime steps from selected policy blueprint stored in `automation_policies`.
- Keep unresolved identity/policy cases observable and non-executable.
- Runtime persistence substrate now in place:
  - `policy_execution_runs`
  - `policy_execution_steps`
  - blueprint-only timing contract (`due_after_minutes` removed)
  - assignment trigger wiring via `WebhookEventProcessorService`
  - assignment event catalog entries now `SUPPORTED` (`peopleCreated`, `peopleUpdated`)
  - duplicate semantics finalized with existing-run return (`DUPLICATE_IGNORED` + stable runId)
  - operator read APIs available under `/admin/policy-executions` (list/detail)

## Phase 4
Status: Planned
Log: `phase-4-implementation.md`
Planned scope alignment (assignment SLA example):
- Execute `WAIT_AND_CHECK_CLAIM` via due worker.
- Execute `WAIT_AND_CHECK_COMMUNICATION` when claim check passes.
- If no communication is detected, execute `ON_FAILURE_EXECUTE_ACTION` using policy-selected action: reassign or move to pond.

## Phase 5
Status: Planned
Log: `phase-5-implementation.md`
