# Lead Intake Call-Attempt Workflow — Phase 3 Implementation

## Status
- Active (updated 2026-04-17).
- Delivered slice: `fub_create_task` workflow step (step-library increment only; active workflow graph unchanged).

## Completed on 2026-04-17

### `fub_create_task` workflow step
- Added `FubCreateTaskWorkflowStep` under workflow step library.
- Reused existing FUB task path:
  - input command model: `CreateTaskCommand`
  - client invocation: `FollowUpBossClient#createTask(...)`
  - transient/permanent error classification aligned with existing FUB step conventions.
- Step config supports:
  - required `name`
  - optional `personId` override (fallback to `sourceLeadId` when omitted)
  - optional `assignedUserId`
  - optional `dueDate`
  - optional `dueDateTime`
- Step success outputs include:
  - `taskId`, `personId`, `assignedUserId`, `name`, `dueDate`, `dueDateTime`

### API/catalog and workflow execution coverage
- Admin step catalog coverage updated to assert `fub_create_task` is present in `/admin/workflows/step-types`.
- Workflow parity suite extended with a dedicated `fub_create_task` execution scenario that asserts:
  - terminal completion on `SUCCESS`
  - expected output payload persistence
  - issued `CreateTaskCommand` values.

### Stub endpoint cleanup
- Removed `TasksController` (`POST /tasks`) stub endpoint from backend runtime.
- Updated architecture deep-dive docs to remove `TasksController` references.

## Validation
- Targeted test run:
  - Command: `./mvnw test -Dtest='FubCreateTaskWorkflowStepTest,AdminWorkflowControllerTest,WorkflowParityTest'`
  - Result (2026-04-17 17:57 -04:00): `BUILD SUCCESS`  
    `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`
  - Included assertions:
    - `FubCreateTaskWorkflowStepTest`
    - `AdminWorkflowControllerTest#shouldReturnStepTypeCatalog`
    - `WorkflowParityTest#shouldExecuteFubCreateTaskAndEmitOutputs`
- Full backend suite:
  - Command: `./mvnw test`
  - Result (2026-04-17 17:57 -04:00): `BUILD SUCCESS`  
    `Tests run: 300, Failures: 0, Errors: 0, Skipped: 0`
