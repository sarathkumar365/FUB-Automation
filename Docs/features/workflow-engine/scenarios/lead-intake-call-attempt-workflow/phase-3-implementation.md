# Lead Intake Call-Attempt Workflow — Phase 3 Implementation

## Status
- Active (updated 2026-04-17).
- Delivered slice: `fub_create_task` workflow step (step-library increment only; active workflow graph unchanged).
- Delivered slice: `wait_and_check_communication` conversational local-first classification (code/tests/docs only; persisted DB workflow graphs intentionally unchanged).

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

## Completed on 2026-04-20

### `wait_and_check_communication` conversational enhancement (local-first)
- Updated `wait_and_check_communication` step contract to:
  - `CONVERSATIONAL`
  - `CONNECTED_NON_CONVERSATIONAL`
  - `COMM_NOT_FOUND`
- Added optional step config `lookbackMinutes` (default `30`, minimum `1`).
- Implemented local-first evaluation from `processed_calls` incoming/outgoing evidence in lookback:
  - `duration_seconds > short-call-threshold` -> `CONVERSATIONAL`
  - `0 < duration_seconds <= short-call-threshold` -> `CONNECTED_NON_CONVERSATIONAL`
  - `duration_seconds == 0` or explicit non-connected outcome (`no answer` etc.) -> `COMM_NOT_FOUND`
  - Incoming and outgoing calls share the same threshold classification behavior.
- Implemented conservative fallback mapping to existing FUB communication read:
  - `communicationFound=true` -> `CONNECTED_NON_CONVERSATIONAL`
  - `false/null` -> `COMM_NOT_FOUND`
- Added repository query support for latest local evidence lookup (direction-agnostic):
  - `ProcessedCallRepository#findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(...)`

### Test updates and additions
- Added step unit coverage:
  - `WaitAndCheckCommunicationWorkflowStepTest`
  - local conversational/non-conversational/not-found classifications (incoming + outgoing)
  - fallback mapping (`true`, `false`, `null`)
  - invalid lead id and transient/permanent failure contracts
  - lookback default/minimum behavior
- Added repository integration coverage:
  - `ProcessedCallRepositoryTest`
  - incoming/outgoing eligibility, lookback filtering, lead isolation, and newest-first ordering
- Updated workflow parity coverage:
  - `WorkflowParityTest` graph transitions now use
    - `CONVERSATIONAL` -> terminal `COMPLIANT_CLOSED`
    - `CONNECTED_NON_CONVERSATIONAL` -> `do_reassign`
    - `COMM_NOT_FOUND` -> `do_reassign`
  - parity scenarios now include incoming evidence and assert local-first behavior (no fallback FUB communication check when local evidence is sufficient)
- Updated step catalog assertion coverage:
  - `AdminWorkflowControllerTest#shouldReturnStepTypeCatalog`
  - verifies `wait_and_check_communication` is present with declared result codes:
    `CONVERSATIONAL`, `CONNECTED_NON_CONVERSATIONAL`, `COMM_NOT_FOUND`

### Scope constraints kept
- No DB migration.
- No persisted workflow graph (`automation_workflows.graph`) updates.
- No changes to `wait_and_check_claim`.
