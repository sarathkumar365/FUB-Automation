# Wave 4c — Operator Controls (Cancel-Only) Implementation Plan

## Goal
Deliver backend-only operator cancel control for workflow runs while UI and retry controls remain deferred.

## Scope
- Add `POST /admin/workflow-runs/{runId}/cancel`.
- Add workflow run status `CANCELED`.
- Enforce worker safety invariant: canceled runs are not claimed/executed.

## Decisions
- Cancel-only in this phase; run retry and step retry deferred.
- Cancel allowed only from `PENDING` status.
- Already canceled runs return success (idempotent behavior).
- Non-cancelable statuses return conflict.

## Implementation Outline
1. Add cancel service in workflow layer to:
   - validate run id
   - read run and status
   - set run status/reason (`CANCELED_BY_OPERATOR`)
   - mark run steps `PENDING`/`WAITING_DEPENDENCY` as `SKIPPED`
2. Add cancel endpoint in `AdminWorkflowRunController`.
3. Add claim-level guard in JDBC claim repository by joining `workflow_runs` and requiring `runs.status = PENDING` for:
   - due-step claim path
   - stale-processing recovery path
4. Add execution guard in `WorkflowStepExecutionService` to skip claimed steps when run is not pending.
5. Guard run-failure write paths so canceled runs are not transitioned back to `FAILED`.

## Test Plan
- Controller tests for cancel endpoint (`200`, `400`, `404`, `409`, idempotent success).
- Service tests for cancel mutation behavior.
- PostgreSQL integration tests verifying claim/recovery gates exclude canceled runs.
- Workflow execution service tests verifying non-pending guard and canceled-run failure-override protection.
- Run targeted suites + full `./mvnw test`.
