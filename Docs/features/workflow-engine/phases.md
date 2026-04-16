# Workflow Engine Rebuild — Phases

## Wave Status
- Wave 1: `COMPLETED`
- Wave 2: `COMPLETED` (stabilization scope delivered; status backfilled after Waves 3/4)
- Wave 3: `COMPLETED` (Wave 3 Phases 1-4 completed in working tree)
- Wave 4: `COMPLETED` (4a completed; 4c cancel controls delivered; 4b/retry deferred)

## Wave Definitions

### Wave 1 — Foundations
- Core workflow persistence, planner, executor, validator, and worker skeleton.
- Initial step catalog exposure endpoint.
- Baseline smoke/validator coverage.
- Status: Completed.
- Implementation notes: [waves/wave-1/phase-1-implementation.md](waves/wave-1/phase-1-implementation.md)

### Wave 2 — Stabilization
- Run context + template resolution + resolved-config audit path.
- Parity step set expansion and shared FUB helper extraction.
- Worker default safety hardening and workflow admin controller test coverage.
- Status: Completed.
- Implementation notes: [waves/wave-2/phase-2-implementation.md](waves/wave-2/phase-2-implementation.md)

### Wave 3 — Dynamic Trigger Routing + Retry Primitive
- Introduce workflow trigger routing from webhook processor path.
- Define and enforce trigger contract + matching behavior.
- Retry primitive with capped exponential backoff.
- Wave 3 plan: [waves/wave-3/wave-3-plan.md](waves/wave-3/wave-3-plan.md)
- Wave 3 closing plan: [waves/wave-3/closing-plan.md](waves/wave-3/closing-plan.md)
- Sub-phase progress:
  - Wave 3 Phase 1 (retry primitive) completed and validated.
  - Wave 3 Phase 2 (trigger plugin infrastructure + router) completed and validated.
  - Wave 3 Phase 3 (MVP step library: `fub_add_tag`, `http_request`, `slack_notify`) completed and validated.
  - Wave 3 Phase 4 (end-to-end proof scenarios + docs gate) completed and validated.
- Implementation notes: [waves/wave-3/phase-3-implementation.md](waves/wave-3/phase-3-implementation.md)

### Wave 4 — Admin API + Builder UI + Operator Controls
- 4a: Admin REST API for full workflow lifecycle + read-only run inspection. Plan: [waves/wave-4/phase-4a-implementation-plan.md](waves/wave-4/phase-4a-implementation-plan.md)
- 4a implementation notes: [waves/wave-4/phase-4a-implementation.md](waves/wave-4/phase-4a-implementation.md)
- 4a progress:
  - Phase 1 (foundations) completed and validated.
  - Phase 2 (workflow CRUD/lifecycle) completed and validated.
  - Phase 3 (validation endpoint + run inspection APIs) completed and validated.
  - Phase 4 (closing fixes: version semantics + wave-gate integration) completed and validated.
  - Wave 4a status: closed.
- 4c: Operator cancel controls (run-level cancel) delivered.
  - Plan: [waves/wave-4/phase-4c-implementation-plan.md](waves/wave-4/phase-4c-implementation-plan.md)
  - Implementation: [waves/wave-4/phase-4c-implementation.md](waves/wave-4/phase-4c-implementation.md)
- 4b (deferred): Workflow builder UI module.
- Retry controls (deferred): run-level retry and step-level retry endpoints.
- Status: Completed for current rebuild scope (Wave 4a + 4c cancel delivered; 4b/retry deferred backlog scope).
