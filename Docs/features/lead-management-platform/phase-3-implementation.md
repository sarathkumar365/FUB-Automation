# Phase 3 Implementation Plan

Status: In progress (Step 1 and Step 2 completed)

## What This Phase Is
Phase 3 is the runtime planning and persistence phase for assignment SLA automation.

This phase must:
- convert assignment events into durable execution records
- select and snapshot the active policy from `automation_policies` at trigger time
- prepare worker-readable pending steps in DB

This phase must not:
- execute due checks
- call reassignment or move-to-pond actions

## How Phase 3 + 4 Will Look Together
Combined behavior:
1. Assignment event arrives.
2. Policy is resolved and snapshotted.
3. Phase 3 persists run + step records.
4. Phase 4 worker polls pending due steps from `policy_execution_steps`.
5. Worker loads context from `policy_execution_runs`.
6. Worker executes step logic in order and writes outcomes.

Step model used by both phases:
1. `WAIT_AND_CHECK_CLAIM`
2. `WAIT_AND_CHECK_COMMUNICATION`
3. `ON_FAILURE_EXECUTE_ACTION`

Phase split:
- Phase 3: plan + persist
- Phase 4: execute + finalize

## Phase 3 Flow
1. Assignment-domain event is received.
2. `AutomationPolicyService` resolves the active assignment policy from `automation_policies`.
3. Policy step blueprint is read from the selected `automation_policies` row (single source of truth).
4. Identity mapping is resolved for executable flow eligibility.
5. `PolicyExecutionManager` creates immutable run snapshot data.
6. Persist `policy_execution_runs` row with run status.
7. Materialize step rows in `policy_execution_steps` from blueprint:
   - step 1 `WAIT_AND_CHECK_CLAIM` -> `PENDING`, `due_at = +5m` (configurable)
   - step 2 `WAIT_AND_CHECK_COMMUNICATION` -> `WAITING_DEPENDENCY`
   - action step metadata (`ON_FAILURE_EXECUTE_ACTION`) stored for Phase 4
8. If identity/policy is invalid, persist blocked run outcome (`BLOCKED_IDENTITY` or `BLOCKED_POLICY`) and do not create executable pending step.
9. If duplicate trigger is detected, return `DUPLICATE_IGNORED` via idempotency logic.

## Chronological Implementation Steps
1. Define policy step model for v1:
   - `WAIT_AND_CHECK_CLAIM`
   - `WAIT_AND_CHECK_COMMUNICATION`
   - `ON_FAILURE_EXECUTE_ACTION`
2. Define policy blueprint schema stored in `automation_policies` (ordered step definitions + action config).
3. Extend `automation_policies` schema to support multi-step blueprint (recommended JSONB blueprint column).
4. Lock run/step statuses and reason codes for planning outcomes.
5. Add DB migration for runtime execution persistence:
   - `policy_execution_runs`
   - `policy_execution_steps`
   - optional `policy_execution_step_history` (if included in this phase)
6. Add entities and repositories for run/step persistence.
7. Implement `PolicyExecutionManager` to:
   - resolve policy
   - read step blueprint from selected `automation_policies` row
   - resolve identity
   - snapshot policy values
   - materialize and persist run + steps at ingestion time
8. Integrate assignment branch in `WebhookEventProcessorService` to call `PolicyExecutionManager` instead of no-op logging.
9. Implement idempotency and duplicate suppression on run creation.
10. Add minimal read/query support for ops visibility of run/step status and due times.
11. Add tests:
   - run + pending claim step creation
   - communication step waiting dependency shape
   - blocked identity path
   - blocked policy path
   - duplicate suppression path
   - snapshot immutability
12. Update phase artifacts after completion:
   - this file
   - `phases.md` status
   - handoff note for Phase 4 worker contract

## Validation
- Step 1 validation completed:
  - targeted tests:
    - `PolicyBlueprintValidatorTest`
    - `PolicyStepTransitionContractTest`
    - `AutomationPolicyServiceTest`
    - `AdminPolicyControllerTest`
    - `AutomationPolicyMigrationPostgresRegressionTest` (skipped without Docker as expected)
  - full backend suite:
    - `./mvnw test`
    - result: pass
- Step 2 validation completed:
  - targeted tests:
    - `PolicyExecutionRuntimeRepositoryTest`
    - `AutomationPolicyRuntimeSchemaMigrationTest`
    - `PolicyExecutionMaterializationContractTest`
  - full backend suite:
    - `./mvnw test`
    - result: pass

## Changes
- Step 1 completed: policy blueprint contract + bootstrap behavior implemented.
- `automation_policies` now carries blueprint definition payload (`blueprint` JSON).
- Added contract artifacts:
  - `PolicyStepType`
  - `PolicyStepResultCode`
  - `PolicyTerminalOutcome`
  - `PolicyStepTransitionContract`
  - `PolicyBlueprintValidator`
- `AutomationPolicyService` now validates blueprint on create/update/activate and reports policy-invalid active lookup deterministically.
- Admin policy DTO/controller/service responses now carry blueprint payload.
- Added Flyway migration `V6__add_policy_blueprint_and_remove_seed.sql`:
  - add `blueprint` column
  - remove previous default seeded policy row for bootstrap-by-admin flow.
- Added/updated tests for contract validation, transition mapping, controller behavior, service behavior, and activation concurrency flow with blueprint-aware fixtures.
- Step 2 completed: runtime persistence substrate + contract cleanup implemented.
- Added Flyway migration `V7__create_policy_execution_runtime_and_drop_due_after_minutes.sql`:
  - create `policy_execution_runs`
  - create `policy_execution_steps`
  - drop `automation_policies.due_after_minutes`
- Policy API/model cleanup completed:
  - removed `dueAfterMinutes` from entity, service commands/views, DTOs, and controller mapping
  - policy timing is now sourced from blueprint step delays only
- Added runtime persistence contracts:
  - entities: `PolicyExecutionRunEntity`, `PolicyExecutionStepEntity`
  - enums: `PolicyExecutionRunStatus`, `PolicyExecutionStepStatus`
  - repositories: `PolicyExecutionRunRepository`, `PolicyExecutionStepRepository`
- Locked initial runtime materialization contract for Phase 4 handoff:
  - step 1 `WAIT_AND_CHECK_CLAIM` -> `PENDING`
  - step 2 `WAIT_AND_CHECK_COMMUNICATION` -> `WAITING_DEPENDENCY`
  - step 3 `ON_FAILURE_EXECUTE_ACTION` -> `WAITING_DEPENDENCY`
  - contract utility: `PolicyExecutionMaterializationContract`
- Added Step 2 runtime tests:
  - migration/schema assertions (`AutomationPolicyRuntimeSchemaMigrationTest`)
  - repository constraints/queries (`PolicyExecutionRuntimeRepositoryTest`)
  - initial materialization contract (`PolicyExecutionMaterializationContractTest`)

## Phase 4 Handoff Contract
- Worker source of truth: `policy_execution_steps` due pending rows.
- Worker context source: `policy_execution_runs` snapshot data.
- Phase 4 must execute steps sequentially and persist deterministic state transitions.
