# AI Call Java Integration — Pass 1 Implementation

## Date
2026-04-21

## Scope completed
- Added `RESCHEDULE` support in `StepExecutionResult` and `WorkflowStepExecutionService`.
- Added `workflow_run_steps.step_state` JSONB via Flyway migration `V16__add_step_state_to_workflow_run_steps.sql`.
- Mapped `step_state` in `WorkflowRunStepEntity`.
- Added/updated tests for reschedule behavior and step-state persistence.

## Behavior now available
- A step can return a non-terminal reschedule outcome with `nextDueAt` and state patch.
- Engine persists state patch into `step_state`, sets status back to `PENDING`, and updates `dueAt`.
- No transition is applied while rescheduling.

## Remaining work
- Build ai-call service adapter (Pass 2).
- Implement `AiCallWorkflowStep` (Pass 3).
- Wire integration and run full regression gates (Pass 4).
