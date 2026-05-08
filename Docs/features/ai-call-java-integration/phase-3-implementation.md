# AI Call Java Integration — Pass 3 Implementation

## Date
2026-04-21

## Scope completed
- Added new workflow step type:
  - `AiCallWorkflowStep` (`id=ai_call`)
  - config schema requires `to` and `context`
  - declared result codes: `completed`, `failed`, `timeout`
  - default retry policy: `NO_RETRY`
- Extended execution context:
  - `StepExecutionContext` now carries `stepState`
  - `WorkflowStepExecutionService` now passes persisted step state into step execution
- Implemented ai-call runtime behavior:
  - first invocation places call with deterministic `call_key = runId:stepId`
  - stores `callSid`, `callKey`, `startedAt` in step state and reschedules for `now + 120s`
  - polling invocation uses `GET /calls/{sid}`
  - in-progress call <= 5 minutes reschedules for `now + 120s`
  - in-progress call > 5 minutes completes with synthetic timeout payload (`schema_version="1"`)
  - terminal call status (`completed` / `failed` / `timeout`) completes with terminal payload output
  - polling transient failures reschedule; non-transient failures fail terminally
- Added unit tests:
  - `AiCallWorkflowStepTest` covering first call, in-progress poll, terminal success, timeout path, transient poll failure, first-call failure, invalid step state, invalid terminal status
  - `WorkflowRetryDispatchTest` coverage to verify `stepState` reaches `StepExecutionContext`

## Validation run
- Targeted tests:
  - `./mvnw -Dtest=AiCallWorkflowStepTest,WorkflowRetryDispatchTest,WorkflowStepExecutionServiceTest test`
  - Result: pass
- Full backend suite:
  - `./mvnw test`
  - Result: pass (`334 tests, 0 failures, 0 errors`)

## Remaining work
- Pass 4 integration wiring and catalog/regression closure.
