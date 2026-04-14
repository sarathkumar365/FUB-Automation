# Workflow Engine Rebuild — Phases

## Phase Status
- Phase 1: `COMPLETED`
- Phase 2: `IN_PROGRESS` (stabilization in working tree)
- Phase 3: `COMPLETED` (Wave 3 Phases 1-4 completed in working tree)
- Phase 4: `NOT_STARTED`

## Phase Definitions

### Phase 1 — Wave 1 Foundations
- Core workflow persistence, planner, executor, validator, and worker skeleton.
- Initial step catalog exposure endpoint.
- Baseline smoke/validator coverage.
- Status: Completed.
- Implementation notes: [phase-1-implementation.md](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine/phase-1-implementation.md)

### Phase 2 — Wave 2 Stabilization
- Run context + template resolution + resolved-config audit path.
- Parity step set expansion and shared FUB helper extraction.
- Worker default safety hardening and workflow admin controller test coverage.
- Status: In progress.
- Implementation notes: [phase-2-implementation.md](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine/phase-2-implementation.md)

### Phase 3 — Trigger Routing
- Introduce workflow trigger routing from webhook processor path.
- Define and enforce trigger contract + matching behavior.
- Wave 3 tracking: [wave-3-dynamic-trigger-routing-retry.md](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine/wave-3-dynamic-trigger-routing-retry.md)
- Current progress:
  - Wave 3 Phase 1 (retry primitive) completed and validated.
  - Wave 3 Phase 2 (trigger plugin infrastructure + router) completed and validated.
  - Wave 3 Phase 3 (MVP step library: `fub_add_tag`, `http_request`, `slack_notify`) completed and validated.
  - Wave 3 Phase 4 (end-to-end proof scenarios + docs gate) completed and validated.
- Implementation notes: [phase-3-implementation.md](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine/phase-3-implementation.md)

### Phase 4 — Broader Step Library + UI
- Expand step library and workflow builder UI integration.
