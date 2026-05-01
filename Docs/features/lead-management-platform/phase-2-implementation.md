# Phase 2 Implementation Log

Status: Completed (Step 1 through Step 7 completed)

## Scope
- Deliver persistent runtime policy control for assignment-SLA behavior (DB + service + admin API).
- Establish reliability-safe update semantics (versioned updates, deterministic conflict behavior).
- Keep this phase infrastructure-only: no due-worker execution or assignment action execution.

## Vertical Implementation Plan (7 Steps)

### Step 1: Lock Phase 2 Contract and Boundaries
- Finalize v1 policy contract in this document before code changes.
- Lock policy fields:
  - `id`
  - `domain`
  - `policyKey`
  - `enabled`
  - `dueAfterMinutes`
  - `version`
  - `status` (`ACTIVE`, `INACTIVE`)
- Lock defaults/validation:
  - default `enabled=true`
  - default `dueAfterMinutes=15`
  - create default `status=INACTIVE`
  - `dueAfterMinutes >= 1`
  - exactly one policy may be `ACTIVE` per (`domain`, `policyKey`) pair
- Lock API contract (including creation/activation):
  - `GET /admin/policies/{domain}/{policyKey}/active` (get active policy)
  - `GET /admin/policies?domain=&policyKey=` (list policies, newest first)
  - `POST /admin/policies` (create policy, default `INACTIVE`)
  - `PUT /admin/policies/{id}` (update with `expectedVersion`)
  - `POST /admin/policies/{id}/activate` (activate with `expectedVersion`)
- Lock validation/API semantics:
  - invalid input -> `400`
  - not found -> `404`
  - stale version -> `409`
  - activation conflict violating single-active-per-scope invariant -> `409`
- Lock out-of-scope for Phase 2:
  - due-worker runtime execution
  - assignment decision execution
  - external provider mutation actions
  - policy audit metadata/history beyond row state

### Step 2: Add Policy Schema, Seed, and Invariants
- Add Flyway migration: `V5__create_automation_policies.sql`.
- Include DB constraints:
  - positive `dueAfterMinutes`
  - version column for optimistic concurrency
  - enforce allowed `status` values (`ACTIVE`, `INACTIVE`)
- Enable multi-policy storage keyed by (`domain`, `policyKey`).
- Enforce a single-active-policy invariant per (`domain`, `policyKey`) scope.
- Seed one deterministic default policy row for assignment SLA:
  - (`ASSIGNMENT`, `FOLLOW_UP_SLA`) -> `ACTIVE`, `enabled=true`, `dueAfterMinutes=15`.

### Step 3: Add Persistence Model and Repository
- Add policy entity and repository behind existing persistence boundaries.
- Support deterministic read of active policy by (`domain`, `policyKey`).
- Support version-aware updates for optimistic concurrency.
- Tests:
  - seeded policy read behavior
  - successful update increments version
  - stale update is rejected

### Step 4: Add Policy Service Boundary
- Add generic `AutomationPolicyService` with:
  - `getActivePolicy(domain, policyKey)`
  - `listPolicies(domain, policyKey)`
  - `createPolicy(command)`
  - `updatePolicy(id, command)`
  - `activatePolicy(id, command)`
- Centralize validation and map outcomes to typed read/mutation service results:
  - read statuses: `SUCCESS`, `INVALID_INPUT`, `NOT_FOUND`
  - mutation statuses: `SUCCESS`, `INVALID_INPUT`, `NOT_FOUND`, `STALE_VERSION`, `ACTIVE_CONFLICT`
- Keep service side effects limited to policy persistence.

> **Post-phase note (added in Phase 3):** Blueprint validation was introduced in Phase 3 and required two additional enum values that are not reflected in the above locked contract:
> - `ReadStatus.POLICY_INVALID` — returned by `getActivePolicy` when an active policy exists but its stored blueprint fails validation. HTTP mapping: `422 Unprocessable Entity`. This allows detection of corrupted or stale blueprints without silently returning invalid data.
> - `MutationStatus.INVALID_POLICY_BLUEPRINT` — returned by `createPolicy`, `updatePolicy`, and `activatePolicy` when the supplied blueprint fails `PolicyBlueprintValidator` checks. HTTP mapping: `422 Unprocessable Entity`.
>
> These values extend (not replace) the original enum contracts above. The controller maps both to `422` distinct from the `400` path used for `INVALID_INPUT`.

### Step 5: Add Admin Policy API
- Add admin endpoints:
  - `GET /admin/policies/{domain}/{policyKey}/active`
  - `GET /admin/policies?domain=&policyKey=`
  - `POST /admin/policies`
  - `PUT /admin/policies/{id}` (requires `expectedVersion`)
  - `POST /admin/policies/{id}/activate` (requires `expectedVersion`)
- Add request/response DTOs exposing:
  - `domain`
  - `policyKey`
  - policy fields
  - `version`
  - `status`
- Keep response contract additive and consistent with existing admin controller conventions.

### Step 6: Add Reliability and Governance Guards
- Ensure update path is atomic and cannot overwrite newer concurrent updates silently.
- Ensure activation path preserves the single-active-per-scope invariant.
- Tests:
  - concurrent/stale update behavior
  - conflict-path status mapping including activation conflicts

### Step 7: Validation Gate and Phase Artifact Updates
- Run targeted suites for migration/repository/service/controller changes.
- Run full backend suite (`./mvnw test`).
- Record validation evidence in this file immediately after each completed slice.
- Update `phases.md` status as slices complete.

## Changes
- Step 1 completed as docs-only contract lock.
- Locked Phase 2 v1 policy contract to generic multi-policy fields:
  - `id`, `domain`, `policyKey`, `enabled`, `dueAfterMinutes`, `version`, `status`
- Locked defaults/validation:
  - `enabled=true`, `dueAfterMinutes=15`, create default `status=INACTIVE`, `dueAfterMinutes >= 1`
  - single-active-policy invariant per (`domain`, `policyKey`)
- Locked API contract:
  - `GET /admin/policies/{domain}/{policyKey}/active`
  - `GET /admin/policies?domain=&policyKey=`
  - `POST /admin/policies`
  - `PUT /admin/policies/{id}`
  - `POST /admin/policies/{id}/activate`
- Locked error semantics:
  - `400` invalid input
  - `404` not found
  - `409` stale version / activation conflict
- Step 2 completed: persistence foundation implemented for generic policy storage.
  - Added migration `V5__create_automation_policies.sql` with:
    - `automation_policies` table
    - scoped active invariant per (`domain`, `policy_key`) using partial unique index
    - default seed row for (`ASSIGNMENT`, `FOLLOW_UP_SLA`)
  - Added persistence types:
    - `AutomationPolicyEntity`
    - `PolicyStatus`
    - `AutomationPolicyRepository`
  - Added repository/invariant test coverage:
    - `AutomationPolicyRepositoryTest`
- Step 3 completed: generic policy service boundary implemented.
  - Added service:
    - `AutomationPolicyService`
  - Added service command/result/view models:
    - `PolicyView`
    - `CreatePolicyCommand`
    - `UpdatePolicyCommand`
    - `ActivatePolicyCommand`
    - `MutationStatus` / `MutationResult`
  - Added service behavior:
    - domain/policyKey normalization (trim + uppercase)
    - validation for domain/policyKey non-blank and `dueAfterMinutes >= 1`
    - `expectedVersion` required for update/activate
    - deterministic mapping for `INVALID_INPUT`, `NOT_FOUND`, `STALE_VERSION`, `ACTIVE_CONFLICT`
    - transactional activation flow that deactivates prior active policy in-scope and activates target policy
  - Added service test coverage:
    - `AutomationPolicyServiceTest`
  - Service hardening refinement:
    - read methods now return explicit read statuses (`SUCCESS`, `INVALID_INPUT`, `NOT_FOUND`)
    - create-path validation now enforces normalized scope length limits before persistence
    - integrity exceptions are now classified by active-scope constraint name so non-scope violations map to invalid input instead of false `ACTIVE_CONFLICT`
- Step 4 completed: service-boundary semantics and status contracts are now locked to the existing generic `AutomationPolicyService`.
  - Locked read API semantics:
    - `getActivePolicy(domain, policyKey)` -> `SUCCESS`/`INVALID_INPUT`/`NOT_FOUND` (extended in Phase 3: `POLICY_INVALID`)
    - `listPolicies(domain, policyKey)` -> `SUCCESS`/`INVALID_INPUT`
  - Locked mutation API semantics:
    - `createPolicy`, `updatePolicy`, `activatePolicy` -> `SUCCESS`/`INVALID_INPUT`/`NOT_FOUND`/`STALE_VERSION`/`ACTIVE_CONFLICT` (extended in Phase 3: `INVALID_POLICY_BLUEPRINT`)
- Step 5 completed: admin policy API surface added on top of `AutomationPolicyService`.
  - Added controller:
    - `AdminPolicyController` with base route `/admin/policies`
  - Added endpoints:
    - `GET /admin/policies/{domain}/{policyKey}/active`
    - `GET /admin/policies?domain=&policyKey=`
    - `POST /admin/policies`
    - `PUT /admin/policies/{id}`
    - `POST /admin/policies/{id}/activate`
  - Added DTOs:
    - requests: `CreatePolicyRequest`, `UpdatePolicyRequest`, `ActivatePolicyRequest`
    - response: `PolicyResponse`
  - Added HTTP status mapping:
    - read: `SUCCESS -> 200`, `INVALID_INPUT -> 400`, `NOT_FOUND -> 404`, `POLICY_INVALID -> 422` (Phase 3 addition)
    - mutation: `SUCCESS -> 201 (create) / 200 (update, activate)`, `INVALID_INPUT -> 400`, `NOT_FOUND -> 404`, `STALE_VERSION -> 409`, `ACTIVE_CONFLICT -> 409`, `INVALID_POLICY_BLUEPRINT -> 422` (Phase 3 addition)
  - Added controller test coverage:
    - `AdminPolicyControllerTest`
- Step 6 completed: reliability and governance guards hardened for policy mutation concurrency.
  - Added repository scoped mutation helper:
    - `deactivateActivePoliciesInScopeExcludingId(domain, policyKey, excludedId, activeStatus, inactiveStatus)`
  - Activation flow hardening in `AutomationPolicyService.activatePolicy`:
    - scoped deactivation now runs as a single repository update operation
    - target activation still uses `saveAndFlush` for deterministic in-transaction constraint evaluation
  - Conflict/status mapping contract preserved:
    - optimistic lock conflicts -> `STALE_VERSION`
    - active scope unique conflicts -> `ACTIVE_CONFLICT`
    - non-scope integrity violations -> `INVALID_INPUT`
  - Added Step 6 reliability tests:
    - `AutomationPolicyRepositoryTest`: scoped deactivation helper behavior
    - `AutomationPolicyServiceTest`: update optimistic-lock conflict mapping; activate non-scope integrity mapping
    - `AdminPolicyActivationConcurrencyFlowTest`: competing stale activation semantics (`200` then `409`) + single-active invariant assertion
    - `AdminPolicyControllerTest`: explicit conflict message assertion for activation conflict path
- Step 7 completed: validation gate and phase artifact updates recorded.

## Validation
- Docs consistency check completed:
  - Phase 2 contract in this file now aligns with policy-storage direction in `lead-management-platform-plan.md`.
  - No runtime tests required for Step 1 (docs-only gate).
- Executed Step 2 targeted suite:
  - `./mvnw test -Dtest=AutomationPolicyRepositoryTest,AutomationPolicyMigrationPostgresRegressionTest`
  - Result: pass (8 tests run, 0 failures, 0 errors, 4 skipped)
  - Note: postgres migration regression test is docker-gated and skipped when Docker is unavailable.
- Re-executed full backend suite after Step 2:
  - `./mvnw test`
  - Result: pass (125 tests run, 0 failures, 0 errors, 6 skipped)
- Executed Step 3 targeted suite:
  - `./mvnw test -Dtest=AutomationPolicyServiceTest,AutomationPolicyRepositoryTest,AutomationPolicyMigrationPostgresRegressionTest`
  - Result: pass (18 tests run, 0 failures, 0 errors, 4 skipped)
  - Note: postgres migration regression tests remain docker-gated and are skipped when Docker is unavailable.
- Re-executed full backend suite after Step 3:
  - `./mvnw test`
  - Result: pass (135 tests run, 0 failures, 0 errors, 6 skipped)
- Re-validated Step 3 hardening update:
  - `./mvnw test -Dtest=AutomationPolicyServiceTest`
  - Result: pass (14 tests run, 0 failures, 0 errors, 0 skipped)
  - `./mvnw test`
  - Result: pass (139 tests run, 0 failures, 0 errors, 6 skipped)
- Executed Step 5 targeted suite:
  - `./mvnw test -Dtest=AdminPolicyControllerTest,AutomationPolicyServiceTest,AutomationPolicyRepositoryTest`
  - Result: pass (34 tests run, 0 failures, 0 errors, 0 skipped)
- Re-executed full backend suite after Step 5:
  - `./mvnw test`
  - Result: pass (155 tests run, 0 failures, 0 errors, 6 skipped)
- Executed Step 6 targeted suite:
  - `./mvnw test -Dtest=AutomationPolicyServiceTest,AutomationPolicyRepositoryTest,AdminPolicyControllerTest,AdminPolicyActivationConcurrencyFlowTest`
  - Result: pass (39 tests run, 0 failures, 0 errors, 0 skipped)
- Re-executed full backend suite after Step 6/7 closure:
  - `./mvnw test`
  - Result: pass (160 tests run, 0 failures, 0 errors, 6 skipped)

## Notes for Next Agent
- Phase 2 is complete; Phase 3 is next.
- Keep Phase 3 implementation aligned to routed assignment event expansion and pending-check creation only.
- Defer due-worker execution and adapter mutation actions to Phase 4.
