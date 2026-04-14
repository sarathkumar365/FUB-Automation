# Workflow Engine Rebuild — Plan

## Objective
Close Wave 3 with end-to-end proof (trigger routing + step execution + retry behavior) and keep feature documentation handoff-ready.

## Current Execution Focus (Wave 3)
- Wave 3 Phase 1 (retry primitive): completed in working tree.
- Wave 3 Phase 2 (trigger plugin infrastructure + router): completed in working tree.
- Wave 3 Phase 3 step-library additions (`fub_add_tag`, `slack_notify`, `http_request`): completed in working tree.
- Wave 3 Phase 4 end-to-end proof: completed in working tree.

## Phase 4 Scope Delivered
1. Added end-to-end workflow trigger proof coverage (`WorkflowTriggerEndToEndTest`).
2. Covered required scenarios:
   - matching webhook -> run created -> terminal completion
   - non-matching webhook -> no workflow run
   - transient notify failure -> retry -> success
3. Verified policy and workflow flows remain side-by-side for assignment events.
4. Updated feature docs (`phase-3-implementation.md`, `phases.md`, wave tracker, plan, research).

## Explicit In Scope
- workflow trigger + execution end-to-end proof under `src/test/java/com/fuba/automation_engine/service/workflow/`.
- targeted + full-suite validation runs.
- feature lifecycle docs and status tracking updates.

## Explicit Out of Scope
- Workflow builder UI module.
- Trigger authoring API expansion.
- Provider write-mode changes (`fub_add_tag` remains log-only).
- Commit/push/PR creation.

## Success Criteria
- End-to-end proof tests exist for all Wave 3 Phase 4 scenarios.
- Policy and workflow flows are both verified in assignment event processing tests.
- Build/test runs pass with explicit Docker/Testcontainers skip reporting when Docker is unavailable.
- Feature docs are handoff-ready with updated phase status and validation logs.
