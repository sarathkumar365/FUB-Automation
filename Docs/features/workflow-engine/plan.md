# Workflow Engine Rebuild — Plan

## Objective
Stabilize current Wave 2 implementation work without committing yet, so the working tree is code/test/doc ready for a single follow-up commit.

## Phase 2 Stabilization Scope
1. Finalize current Wave 2 code path consistency.
2. Enforce explicit opt-in workflow worker defaults.
3. Add missing controller coverage for workflow admin endpoints.
4. Align feature docs to required `Docs/features/<feature-slug>/` lifecycle structure.

## Explicit In Scope
- `service/workflow/*` consistency checks and test validation.
- `workflow.worker.enabled` default hardening.
- New `AdminWorkflowControllerTest`.
- Feature lifecycle docs and status tracking.

## Explicit Out of Scope
- Trigger router implementation (`WorkflowTriggerRouter`) and webhook routing migration.
- Workflow builder UI module.
- New admin endpoints beyond current create/get/step-types.
- Commit/push/PR creation.

## Success Criteria
- Build/test passes with existing constraints.
- Workflow worker is default-off unless explicitly enabled.
- Workflow controller coverage exists for create/get/step-types behavior.
- Feature docs are handoff-ready with phase status and implementation notes.
