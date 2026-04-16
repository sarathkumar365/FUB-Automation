# Wave 4c — Operator Controls (Cancel-Only) Implementation

## Status
Implemented (backend cancel controls)

## Delivered
- Added cancel endpoint:
  - `POST /admin/workflow-runs/{runId}/cancel`
- Added run lifecycle state:
  - `WorkflowRunStatus.CANCELED`
- Added cancel orchestration service:
  - `WorkflowRunControlService.cancelRun(runId)`
  - idempotent success for already canceled runs
  - `409` conflict for non-cancelable terminal statuses
  - cancel mutation marks pending/dependency-waiting steps as skipped
- Worker safety invariant enforced at both layers:
  - claim SQL joins to `workflow_runs` and only claims/recovers steps where parent run is `PENDING`
  - step execution guard skips claimed steps when parent run is non-pending
- Added guards to prevent canceled runs from being overwritten to failed status in worker compensation/recovery paths.

## Deferred
- Run retry endpoint.
- Step retry endpoint.
- UI/operator console integration.

## Validation
- Added controller, service, execution-guard, and integration claim-gate tests for cancel behavior.
- `./mvnw test -Dtest='AdminWorkflowRunControllerTest,WorkflowRunControlServiceTest,WorkflowStepExecutionServiceTest,WorkflowRunStepClaimRepositoryPostgresTest,WorkflowAdminApiIntegrationTest'`
  - Result: passed (`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test`
  - Result: passed (`Tests run: 407, Failures: 0, Errors: 0, Skipped: 0`)
