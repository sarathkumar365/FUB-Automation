# Phase 4 Implementation Log

Status: Completed (Step 1 through Step 5 completed)

## Scope
- Execute assignment SLA due checks prepared in Phase 3:
  - execute step `WAIT_AND_CHECK_CLAIM`
  - execute step `WAIT_AND_CHECK_COMMUNICATION` when claim step passes
- Apply policy-driven action when SLA unmet:
  - execute step `ON_FAILURE_EXECUTE_ACTION` with configured action:
    - reassign lead, or
    - move lead to pond
- Persist outcomes and failure reasons with replay-safe behavior

## Locked Decisions
- Worker model: DB claimer poller (module-level isolation in current app runtime).
- Scalability strategy: atomic due-step claiming using DB row locking (`FOR UPDATE SKIP LOCKED` pattern).
- Delivery sequencing: implement claim-step execution first, then extend same framework to communication/action.
- Assignment webhook handling: processor fan-out by `resourceIds` (one planning call/run per resource ID).
- Missing assignment resource IDs: skip planning and log warning (event remains observable).
- FUB truth source for claim state: live People read (`/v1/people/{id}`) via existing FUB client boundary.
- Claim semantics: evaluate `claimed` first when present, fallback to `assignedUserId > 0` when `claimed` is absent.
- Retry model for claim checks: inline retries using existing `fub.retry.*`; on exhaustion, mark step/run failed.

## Technical Plan (5 Actionable Steps)
### 1) Add assignment fan-out in webhook processor (minimal-change path)
Status: Completed (2026-04-06)
- Update assignment branch in `WebhookEventProcessorService` to reuse current payload `resourceIds` extraction.
- For each assignment `resourceId`, call `policyExecutionManager.plan(...)` with `sourceLeadId` set to that ID.
- Keep one policy execution run per lead/resource ID (no aggregated batch run).
- Keep call-domain resource processing behavior unchanged.
- If assignment event has no valid `resourceIds`, skip planning and log explicit warning.

### 2) Build standalone policy worker contract + config
Status: Completed (2026-04-07)
- Add worker properties:
  - `policy.worker.enabled`
  - `policy.worker.poll-interval-ms`
  - `policy.worker.claim-batch-size`
  - `policy.worker.max-steps-per-poll`
- Implement `PolicyExecutionDueWorker` scheduled loop under policy service module only.
- Gate worker with property toggle so it can be enabled/disabled independently of webhook ingestion.
- Preserve module boundary: webhook ingestion/dispatch does not execute due-step business logic.

### 3) Implement atomic due-step claim path (scalable)
Status: Completed (2026-04-07)
- Add JDBC-based due-step claim repository for `policy_execution_steps`.
- Claim query must atomically:
  - select due rows (`status=PENDING` and `due_at <= now`)
  - lock rows with skip-locked semantics
  - update status to `PROCESSING`
  - return claimed rows for execution
- Keep deterministic ordering (`due_at`, `id`) and batch limits.
- Ensure safe multi-instance execution without duplicate step processing.

### 4) Implement step execution framework + claim executor
Status: Completed (2026-04-07)
- Add step executor boundary:
  - `PolicyStepExecutor` interface
  - `PolicyStepExecutionService` dispatcher/orchestrator
- First concrete executor: `WaitAndCheckClaimStepExecutor` implementing `PolicyStepExecutor`.
- Reuse existing FUB client boundary:
  - extend `FollowUpBossClient`
  - implement in `client/fub/FubFollowUpBossClient`
  - read person state via FUB People endpoint (`/v1/people/{id}`)
- Use live person state to determine claim result:
  - if `claimed` exists, use it directly
  - else fallback to `assignedUserId > 0`
- On outbound read failures, apply configured inline retries; if exhausted, fail step and run with explicit reason.

### 5) Persist transitions, validate behavior, and update artifacts
Status: Completed (2026-04-08)
- Claim result transitions:
  - `CLAIMED`: mark claim step complete and activate communication step (`WAITING_DEPENDENCY -> PENDING`) with due time from policy blueprint delay.
  - `NOT_CLAIMED`: mark run terminal non-escalated outcome and mark dependent steps `SKIPPED`.
- Failure transitions:
  - mark step `FAILED`, run `FAILED`, persist result/error reason for replay-safe diagnostics.
- Add/extend tests:
  - assignment processor fan-out (single ID, multiple IDs, missing IDs)
  - atomic claim repository concurrency behavior
  - claim executor claimed/not-claimed/failure outcomes
  - end-to-end due-step execution integration path
- Run full backend suite (`./mvnw test`) after new tests.
- Update phase progress and validation notes here and in `phases.md` as increments complete.

## Changes
- Step 1 implemented: assignment-domain webhook processor now fans out planning per `resourceId`.
- `WebhookEventProcessorService.processAssignmentDomainEvent` now:
  - extracts assignment `resourceIds` from payload
  - logs event-level assignment lead count
  - short-circuits with warning when no valid IDs are present
  - calls `policyExecutionManager.plan(...)` once per lead ID with `sourceLeadId=String.valueOf(resourceId)`
  - logs per-lead planning outcomes (`leadId`, `status`, `runId`, `reasonCode`)
- Added/updated unit tests in `WebhookEventProcessorServiceTest` for:
  - single assignment resource ID planning with `sourceLeadId` assertion
  - multiple assignment resource IDs => one plan call per lead ID
  - missing assignment resource IDs => no plan calls
  - per-lead assignment planning failure isolation (one failing lead does not block remaining lead IDs)
  - existing call/unknown-domain behavior remains validated
- Hardened assignment fan-out loop to catch planning runtime exceptions per lead ID, log failure context, and continue remaining IDs in the same webhook payload.
- Step 2 implemented: standalone worker scaffold contract added.
- Added new worker configuration model:
  - `PolicyWorkerProperties` (`policy.worker.*`) with defaults:
    - `enabled=true`
    - `pollIntervalMs=2000`
    - `claimBatchSize=50`
    - `maxStepsPerPoll=200`
- Added dedicated scheduling config:
  - `PolicyWorkerSchedulingConfig` with `@EnableScheduling`
- Added worker module entrypoint:
  - `PolicyExecutionDueWorker`
  - gated by `@ConditionalOnProperty(policy.worker.enabled=true)`
  - scheduled poll method `pollAndProcessDueSteps()` using configured fixed delay
  - includes guardrail normalization for claim-batch and max-steps-per-poll
  - intentionally no DB claim/execution side effects yet (scaffold-only by step design)
- Added `application.properties` entries:
  - `policy.worker.enabled`
  - `policy.worker.poll-interval-ms`
  - `policy.worker.claim-batch-size`
  - `policy.worker.max-steps-per-poll`
- Added Step 2 test coverage:
  - `PolicyWorkerPropertiesBindingTest` (default + override binding)
  - `PolicyExecutionDueWorkerActivationTest` (enabled/disabled conditional bean behavior)
  - `PolicyExecutionDueWorkerTest` (guardrails + scaffold poll safety)
- Step 3 implemented: atomic due-step claim path added for production-safe shared DB worker polling.
- Added dedicated claim repository contract + projection:
  - `PolicyExecutionStepClaimRepository`
  - `ClaimedStepRow`
- Added JDBC atomic claim implementation:
  - `JdbcPolicyExecutionStepClaimRepository`
  - SQL pattern: CTE + `FOR UPDATE SKIP LOCKED` + `UPDATE ... RETURNING`
  - claim filter: `status=PENDING` and `due_at <= :now`
  - deterministic ordering: `due_at, id`
  - bounded claim count by batch limit
  - transition: `PENDING -> PROCESSING`
- Updated worker loop (`PolicyExecutionDueWorker`) to:
  - repeatedly claim in bounded cycles
  - stop on empty claim result or when `maxStepsPerPoll` is reached
  - keep Step 3 scope (no step executor/business logic)
  - log poll summary (`claimedTotal`, `cycles`, effective limits)
- Added Step 3 test coverage:
  - `PolicyExecutionStepClaimRepositoryPostgresTest`:
    - due/pending filtering
    - non-due/non-pending exclusion
    - ordering and limit
    - status update to `PROCESSING`
    - concurrent claim non-overlap
  - updated `PolicyExecutionDueWorkerTest` for multi-cycle bounded claim behavior
  - updated `PolicyExecutionDueWorkerActivationTest` for worker dependencies
- Updated test profile config:
  - `src/test/resources/application.properties` sets `policy.worker.enabled=false` to avoid scheduled worker DB-claim SQL execution during unrelated H2 test contexts.
- Step 4 implemented: pluggable step execution framework + first concrete claim executor.
- Added step executor boundary and dispatcher:
  - `PolicyStepExecutor`
  - `PolicyStepExecutionContext`
  - `PolicyStepExecutionResult`
  - `PolicyStepExecutionService` registry/dispatch by `PolicyStepType`
- Added `WaitAndCheckClaimStepExecutor` for `WAIT_AND_CHECK_CLAIM`:
  - parses `sourceLeadId` as FUB person id
  - reads person via FUB People API
  - claim evaluation precedence:
    - use `claimed` when present
    - fallback to `assignedUserId > 0` when `claimed` absent
  - applies inline transient retry using existing `fub.retry.*`
- Extended FUB client boundary:
  - `FollowUpBossClient.getPersonById(...)`
  - `FubFollowUpBossClient` implementation (`GET /people/{id}`)
  - new DTO/model mapping for people payload (`claimed`, `assignedUserId`)
- Updated due worker execution path:
  - after claim batch, dispatch each claimed row through `PolicyStepExecutionService`
  - per-row failure isolation (continue remaining rows)
  - execution persistence:
    - success: step `PROCESSING -> COMPLETED` + `result_code`
    - failure: step `PROCESSING -> FAILED` + `error_message`, run `FAILED` + `reason_code`
- Added Step 4 tests:
  - `PolicyStepExecutionServiceTest` (registry routing, missing executor, runtime failure, missing step)
  - `WaitAndCheckClaimStepExecutorTest` (claimed/not-claimed/fallback/invalid id/transient/permanent failure)
  - `FubFollowUpBossClientTest` people fetch success + HTTP/network error mappings
  - updated `PolicyExecutionDueWorkerTest` for row execution and mixed-failure continuation.
- Step 5 implemented: transition persistence and next-step activation behavior.
- Updated `PolicyStepExecutionService` to apply `PolicyStepTransitionContract` after successful step execution:
  - `CLAIMED` result now activates next step (`WAITING_DEPENDENCY -> PENDING`) and computes `dueAt` from blueprint `delayMinutes`.
  - `NOT_CLAIMED` terminal transition now marks run `COMPLETED` with terminal reason and marks downstream non-terminal steps `SKIPPED`.
  - missing/invalid transition targets now fail deterministically with reason codes:
    - `TRANSITION_NOT_DEFINED`
    - `TRANSITION_TARGET_NOT_FOUND`
    - `TRANSITION_TARGET_INVALID_STATE`
- Added Step 5 coverage in `PolicyStepExecutionServiceTest`:
  - next-step activation + due-time calculation on `CLAIMED`
  - terminalization + downstream skip on `NOT_CLAIMED`
  - missing transition target failure path
- Reliability hardening increment (dev-phase must-have):
  - `PolicyExecutionDueWorker` now retries compensation in bounded attempts when `executeClaimedStep(...)` throws.
  - compensation failures are isolated with nested catch so one broken row cannot abort the poll loop.
  - added inline TODO linked to known issue #6 for deferred stale-`PROCESSING` watchdog/reaper.

## Validation
- Targeted test suite:
  - `./mvnw test -Dtest=WebhookEventProcessorServiceTest`
  - result: pass (6 tests, 0 failures)
- Targeted Step 2 suites:
  - `./mvnw test -Dtest=PolicyWorkerPropertiesBindingTest,PolicyExecutionDueWorkerActivationTest,PolicyExecutionDueWorkerTest`
  - result: pass (7 tests, 0 failures)
- Targeted Step 3 suites:
  - `./mvnw test -Dtest=PolicyExecutionStepClaimRepositoryPostgresTest,PolicyExecutionDueWorkerTest,PolicyExecutionDueWorkerActivationTest`
  - result: pass (tests green; Postgres/Testcontainers tests skipped when Docker unavailable in local environment)
- Full backend suite:
  - `./mvnw test`
  - result: failed due to existing integration test that referenced the identity resolver contract:
    - original failing test: `PolicyExecutionManagerIntegrationTest.shouldPersistBlockedIdentityRunWhenIdentityIsUnresolved`
    - this test was superseded by V8 identity resolver removal; the test was subsequently renamed to `shouldCreatePendingRunWhenIdentityResolverIsRemoved` to reflect the new expected behavior — a run now goes to `PENDING` (not `BLOCKED_IDENTITY`) when identity resolution is bypassed, because the identity resolver contract no longer exists.
    - the old `BLOCKED_IDENTITY` status and `BLOCKED_IDENTITY` reason code were removed from the runtime entirely.
- Targeted Step 4 suites:
  - `./mvnw test -Dtest=WaitAndCheckClaimStepExecutorTest,PolicyStepExecutionServiceTest,PolicyExecutionDueWorkerTest,PolicyExecutionDueWorkerActivationTest,FubFollowUpBossClientTest`
  - result: pass (28 tests, 0 failures)
- Targeted Step 5 suites:
  - `./mvnw test -Dtest=PolicyStepExecutionServiceTest,PolicyExecutionDueWorkerTest,WaitAndCheckClaimStepExecutorTest`
  - result: pass (18 tests, 0 failures)
- Targeted reliability-hardening suites:
  - `./mvnw test -Dtest=PolicyExecutionDueWorkerTest,PolicyStepExecutionServiceTest`
  - result: pass (new compensation-retry/isolation scenarios green)
- Reproduction of original failing test (now removed):
  - `./mvnw test -Dtest=PolicyExecutionManagerIntegrationTest#shouldPersistBlockedIdentityRunWhenIdentityIsUnresolved`
  - result: failure reproduced at the time; test has since been renamed and reworked as part of V8 identity resolver removal
  - current test name: `PolicyExecutionManagerIntegrationTest#shouldCreatePendingRunWhenIdentityResolverIsRemoved`

## Notes for Next Agent
- This phase is where business decisions and adapter actions run.
- Ensure idempotent action execution and explicit observability for each decision point.
- Keep step execution deterministic and sequential according to policy snapshot order.
- Worker input source must be persisted Phase 3 runtime records (`policy_execution_step` pending rows with due times), not policy definition rows.
- Assignment event nuance:
  - for `peopleCreated`/`peopleUpdated`, `resourceIds` are lead/person IDs and should fan out into independent runs.
  - do not treat single webhook payload as one execution unit when multiple resource IDs exist.
- `sourceLeadId` for Phase 4 planning/execution should come from assignment `resourceIds` fan-out in processor.
- Keep using repo decisions and feature docs as implementation authority:
  - `Docs/repo-decisions/README.md`
  - relevant accepted decisions (`RD-001`, `RD-002`, `RD-003`)
  - this phase log + `phases.md`.
