# Phase 5 Implementation Log

Status: In progress (Step 1 and Step 2 completed; Step 3 and Step 4 pending)

## Scope
- Complete pending policy step executors so runtime no longer fails with `EXECUTOR_NOT_FOUND`.
- Replace temporary dev-mode placeholders with real Follow Up Boss adapter behavior for communication checks and action execution.
- Explicitly defer hardening and production-readiness work to a later phase.

## Changes
### Step Plan (4 Steps)
1. Lock scope, contracts, and defaults
   - Status: Completed (2026-04-08)
   - Scope: executor completion only.
   - Defer hardening: stale `PROCESSING` watchdog/reaper and broader production hardening remain out of scope.
   - Default behavior:
     - `WAIT_AND_CHECK_COMMUNICATION`: add method contract now; real endpoint lookup deferred.
     - `ON_FAILURE_EXECUTE_ACTION`: dev no-op success with structured logging (no provider mutation).

2. Implement missing executors and wiring
   - Status: Completed (2026-04-08)
   - Add `WaitAndCheckCommunicationStepExecutor`.
     - Validate/parse `sourceLeadId`.
     - Use new communication-check client method.
     - Return `COMM_FOUND` or `COMM_NOT_FOUND`; map invalid input/errors to explicit reason codes.
   - Add `OnCommunicationMissActionStepExecutor`.
     - Read `actionConfig.actionType` from policy snapshot.
     - Handle `REASSIGN` and `MOVE_TO_POND` as no-op success + log.
     - Fail with explicit reason when config/type is invalid or missing.
   - Ensure both executors are registered and discovered by `PolicyStepExecutionService`.

3. Implement real adapter behavior and extend tests for executor coverage and transitions
   - Communication adapter implementation:
     - replace `checkPersonCommunication(...)` placeholder behavior with real FUB endpoint lookup
     - map FUB HTTP/network failures to transient/permanent error categories
   - Action adapter implementation:
     - replace action no-op path with real FUB mutation calls for `REASSIGN` and `MOVE_TO_POND`
     - validate required action target configuration for each action type
     - map FUB HTTP/network failures to explicit executor reason codes
   - Communication executor tests:
     - communication found/not found
     - invalid/missing source lead id
     - transient/permanent error mapping
   - Action executor tests:
     - `REASSIGN` success path with real adapter call
     - `MOVE_TO_POND` success path with real adapter call
     - invalid/missing action config and target config failure
   - Dispatcher/worker flow tests:
     - claim -> communication execution
     - communication-not-found -> action execution
     - action terminal transition final run outcome

4. Validate and document
   - Run targeted policy suites for new executors and existing due-worker/dispatcher paths.
   - Update this log with implementation notes and test evidence.
   - Update `phases.md` with Phase 5 progress after completion increments.

### Step 1 implementation notes (2026-04-08)
- Scope/defaults locked for Phase 5 dev-mode execution:
  - executor completion only
  - hardening and rollout/governance deferred
  - communication-check remains contract-ready with placeholder adapter behavior
  - action execution remains no-provider-mutation in this phase
- Added communication-check port contract:
  - `FollowUpBossClient.checkPersonCommunication(long personId)`
- Added service model for contract response:
  - `PersonCommunicationCheckResult(personId, communicationFound)`
- Implemented placeholder adapter behavior in `FubFollowUpBossClient`:
  - structured log + deterministic `communicationFound=false`
  - no endpoint call and no mutation side effects
- Added TODO anchor for Step 2 real integration in `FubFollowUpBossClient`.
- Updated test doubles implementing `FollowUpBossClient` to satisfy new contract method.

### Step 2 implementation notes (2026-04-08)
- Added `WaitAndCheckCommunicationStepExecutor`:
  - supports `WAIT_AND_CHECK_COMMUNICATION`
  - validates `sourceLeadId` and parses person id
  - calls `FollowUpBossClient.checkPersonCommunication(...)` with inline retry using `fub.retry.maxAttempts`
  - maps outcomes to `COMM_FOUND` / `COMM_NOT_FOUND`
  - maps transient/permanent/unexpected errors to explicit reason codes
- Added `OnCommunicationMissActionStepExecutor`:
  - supports `ON_FAILURE_EXECUTE_ACTION`
  - reads `actionConfig.actionType` from policy snapshot in execution context
  - allows only `REASSIGN` and `MOVE_TO_POND`
  - executes dev-mode no-op action (structured log + `ACTION_SUCCESS`)
  - returns explicit failure reason for missing/invalid/unsupported action config
- Extended `PolicyStepExecutionContext` with `policyBlueprintSnapshot` and wired it from `PolicyStepExecutionService` run context.
- Removed stale TODO in `PolicyStepExecutionService` missing-executor branch after adding executors.
- Added tests:
  - `WaitAndCheckCommunicationStepExecutorTest`
  - `OnCommunicationMissActionStepExecutorTest`
  - expanded `PolicyStepExecutionServiceTest` transition coverage for communication/action execution paths

## Validation
- Planned command set:
  - `./mvnw test -Dtest=WaitAndCheckCommunicationStepExecutorTest,OnCommunicationMissActionStepExecutorTest,PolicyStepExecutionServiceTest,PolicyExecutionDueWorkerTest,PolicyExecutionDueWorkerActivationTest`
- Success criteria:
  - New executor tests pass.
  - Existing worker/dispatcher tests remain green.
  - No `EXECUTOR_NOT_FOUND` path for supported policy step types.
- Step 1 validation run:
  - `./mvnw test -Dtest=FubFollowUpBossClientTest`
  - Result: pass (`10` tests, `0` failures, `0` errors)
  - Build compile check: `testCompile` passed for full test sources after interface signature change.
- Step 2 validation run:
  - `./mvnw test -Dtest=WaitAndCheckCommunicationStepExecutorTest,OnCommunicationMissActionStepExecutorTest,PolicyStepExecutionServiceTest,PolicyExecutionDueWorkerTest,PolicyExecutionDueWorkerActivationTest`
  - Result: pass (`31` tests, `0` failures, `0` errors)

## Notes for Next Agent
- Locked assumptions for this phase:
  - Step 3 now includes replacing both communication and action placeholders with real adapter behavior.
  - Keep adapter calls behind `FollowUpBossClient` port and preserve layered boundaries (`controller -> service -> port -> adapter`).
  - Hardening tasks from `Docs/known-issues.md` stay deferred.
