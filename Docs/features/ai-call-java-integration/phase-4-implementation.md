# AI Call Java Integration — Pass 4 Implementation

## Date
2026-04-21

## Scope completed
- Extended step catalog assertions to include `ai_call` in `/admin/workflows/step-types`:
  - id presence
  - required config keys (`to`, `context`)
  - declared result codes (`completed`, `failed`, `timeout`)
  - default retry policy (`maxAttempts=1`)
- Added `AiCallWorkflowIntegrationTest` to validate runtime wiring with worker-loop execution:
  - Spring Boot + Postgres Testcontainers setup
  - manual `WorkflowExecutionDueWorker` polling (`workflow.worker.enabled=false`)
  - mutable test clock via `@Primary` bean
  - stubbed `AiCallServiceClient` via `@Primary` bean
- Implemented integration scenarios:
  - place -> in_progress -> completed (reschedule cadence + terminal transition)
  - place -> in_progress (>5m) -> timeout terminal payload
  - place -> non-transient poll exception -> step/run fail path
- Verified downstream output mapping via `set_variable` expression:
  - `{{ steps.ai1.outputs.status }}`

## Validation run
- Targeted suite:
  - `./mvnw -Dtest=AiCallWorkflowIntegrationTest,AdminWorkflowControllerTest,WorkflowAdminApiIntegrationTest,AiCallWorkflowStepTest test`
  - Result: pass
- Full suite (with Docker/Testcontainers enabled):
  - `./mvnw test`
  - Result: pass (`337 tests, 0 failures, 0 errors`)

## Notes
- No contract or endpoint changes were introduced.
- Poll interval remains fixed at 120s and timeout threshold remains 5 minutes.
