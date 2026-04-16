# Phase 5 Implementation Log

Status: Completed (Step 1 through Step 4 completed)

## Scope
- Complete pending policy step executors so runtime no longer fails with `EXECUTOR_NOT_FOUND`.
- Replace temporary communication placeholder with real Follow Up Boss adapter behavior.
- Keep action execution structure-only and fail explicitly until action-target semantics are finalized.
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

3. Implement communication adapter behavior and extend tests for executor coverage and transitions
   - Status: Completed (2026-04-08)
   - Communication adapter implementation:
     - replace `checkPersonCommunication(...)` placeholder behavior by reusing `getPersonById(...)`
     - evaluate communication from People payload `contacted` (`contacted > 0` => communication found)
     - reuse existing People HTTP/network error mapping via `getPersonById(...)`
   - Action executor interim implementation:
     - replace action no-op success with deterministic explicit failure (`ACTION_TARGET_UNCONFIGURED`)
     - keep `REASSIGN` and `MOVE_TO_POND` parsing/validation
     - add TODO anchor for future provider mutation wiring once target semantics are finalized
   - Communication executor tests:
     - communication found/not found
     - invalid/missing source lead id
     - transient/permanent error mapping
   - Action executor tests:
     - `REASSIGN` explicit interim failure path
     - `MOVE_TO_POND` explicit interim failure path
     - invalid/missing action config failure
   - Dispatcher/worker flow tests:
     - claim -> communication execution
     - communication-not-found -> action execution
     - dispatcher transition failure persisted deterministically while targets are undecided

4. Validate and document
   - Status: Completed (2026-04-08)
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
  - initial implementation used dev-mode no-op action (structured log + `ACTION_SUCCESS`)
  - returns explicit failure reason for missing/invalid/unsupported action config
- Extended `PolicyStepExecutionContext` with `policyBlueprintSnapshot` and wired it from `PolicyStepExecutionService` run context.
- Removed stale TODO in `PolicyStepExecutionService` missing-executor branch after adding executors.
- Added tests:
  - `WaitAndCheckCommunicationStepExecutorTest`
  - `OnCommunicationMissActionStepExecutorTest`
  - expanded `PolicyStepExecutionServiceTest` transition coverage for communication/action execution paths

### Step 3 implementation notes (2026-04-08)
- Updated People contract/mapping for communication truth source:
  - `FubPersonResponseDto` now includes `contacted`
  - `PersonDetails` now includes `contacted`
- Implemented real communication check by reusing `getPersonById(...)` in `FubFollowUpBossClient`:
  - `checkPersonCommunication(personId)` now derives `communicationFound` from `contacted > 0`
  - no duplicate HTTP/error-mapping path was introduced
- Updated `OnCommunicationMissActionStepExecutor` interim behavior:
  - supported action types (`REASSIGN`, `MOVE_TO_POND`) now return explicit failure
    `ACTION_TARGET_UNCONFIGURED`
  - added TODO marker for future target-aware provider mutation wiring
- Updated tests:
  - `FubFollowUpBossClientTest` verifies contacted mapping and communication check true/false outcomes
  - `OnCommunicationMissActionStepExecutorTest` now asserts explicit interim failure for supported action types
  - `PolicyStepExecutionServiceTest` now asserts action-step failure propagates run failure with
    `ACTION_TARGET_UNCONFIGURED`
  - adjusted `PersonDetails` constructor usage across tests for new `contacted` field

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
- Step 3 validation runs:
  - `./mvnw test -Dtest=PolicyStepExecutionServiceTest,OnCommunicationMissActionStepExecutorTest,FubFollowUpBossClientTest,WaitAndCheckCommunicationStepExecutorTest,WaitAndCheckClaimStepExecutorTest`
  - Result: pass (`40` tests, `0` failures, `0` errors)
- Step 4 validation runs:
  - `./mvnw test -Dtest=WaitAndCheckCommunicationStepExecutorTest,OnCommunicationMissActionStepExecutorTest,PolicyStepExecutionServiceTest,PolicyExecutionDueWorkerTest,PolicyExecutionDueWorkerActivationTest,FubFollowUpBossClientTest`
  - Result: pass (`43` tests, `0` failures, `0` errors)
  - `./mvnw test`
  - Result: pass (`246` tests, `0` failures, `0` errors, `8` skipped)

### Step 4 implementation notes (2026-04-08)
- Re-ran Phase 5 validation in two gates:
  - Gate A (targeted): communication/action executors, step execution transitions, due-worker paths, and FUB adapter coverage.
  - Gate B (broad): full regression via `./mvnw test`.
- Verified locked Phase 5 semantics remain intact:
  - `WAIT_AND_CHECK_COMMUNICATION` resolves from People `contacted`.
  - `ON_FAILURE_EXECUTE_ACTION` deterministically fails with `ACTION_TARGET_UNCONFIGURED` until target semantics are finalized.
  - No missing-executor gap exists for supported policy step types.

## Next Phase Handoff
- Remaining deferred items for follow-up phase:
  - Finalize action target semantics (`targetUserId` / `targetPondId`) and wire concrete provider mutation behind `FollowUpBossClient`.
  - Execute production hardening items deferred from this phase (for example stale `PROCESSING` watchdog/reaper and related reliability controls).

## Notes for Next Agent
- Locked assumptions for this phase:
  - Communication check now uses People payload `contacted` via `getPersonById(...)`.
  - Action target semantics are intentionally undecided; action executor fails explicitly with `ACTION_TARGET_UNCONFIGURED` until finalized.
  - Keep adapter calls behind `FollowUpBossClient` port and preserve layered boundaries (`controller -> service -> port -> adapter`).
  - Hardening tasks from `Docs/engineering-reference/known-issues.md` stay deferred.

## Superseded Notes
- Phase 7 supersedes the action-target deferral in this log:
  - action targets are now required in policy blueprint
  - executor path is wired and returns `ACTION_SUCCESS` in log-only adapter mode for dev
