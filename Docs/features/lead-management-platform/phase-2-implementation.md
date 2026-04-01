# Phase 2 Implementation Log

Status: Planned

## Scope
- Deliver persistent runtime policy control for assignment-SLA behavior (DB + service + admin API).
- Establish reliability-safe update semantics (versioned updates, deterministic conflict behavior).
- Keep this phase infrastructure-only: no due-worker execution or assignment action execution.

## Vertical Implementation Plan (7 Steps)

### Step 1: Lock Phase 2 Contract and Boundaries
- Finalize v1 policy contract in this document before code changes.
- Lock policy fields:
  - `enabled`
  - `dueAfterMinutes`
  - `maxAttempts`
  - `updatedBy`
  - `createdAt`
  - `updatedAt`
  - `version`
- Lock validation/API semantics:
  - invalid input -> `400`
  - stale version -> `409`
- Lock out-of-scope for Phase 2:
  - due-worker runtime execution
  - assignment decision execution
  - external provider mutation actions

### Step 2: Add Policy Schema, Seed, and Invariants
- Add Flyway migration: `V5__create_assignment_sla_policy.sql`.
- Include DB constraints:
  - positive `dueAfterMinutes`
  - positive `maxAttempts`
  - non-null audit timestamps
  - version column for optimistic concurrency
- Seed one deterministic default policy row so runtime reads are always resolvable.
- Enforce single-active-policy invariant for v1.

### Step 3: Add Persistence Model and Repository
- Add policy entity and repository behind existing persistence boundaries.
- Support deterministic read of active policy.
- Support version-aware updates for optimistic concurrency.
- Tests:
  - seeded policy read behavior
  - successful update increments version
  - stale update is rejected

### Step 4: Add Policy Service Boundary
- Add `AssignmentSlaPolicyService` with:
  - `getActivePolicy()`
  - `updatePolicy(command)`
- Centralize validation and map outcomes to typed service results:
  - `UPDATED`
  - `INVALID_INPUT`
  - `STALE_VERSION`
- Keep service side effects limited to policy persistence.

### Step 5: Add Admin Policy API
- Add admin endpoints:
  - `GET /admin/policies/assignment-sla`
  - `PUT /admin/policies/assignment-sla` (requires `expectedVersion`)
- Add request/response DTOs exposing:
  - policy fields
  - `version`
  - audit timestamps
- Keep response contract additive and consistent with existing admin controller conventions.

### Step 6: Add Reliability and Governance Guards
- Ensure update path is atomic and cannot overwrite newer concurrent updates silently.
- Ensure successful writes always update `updatedBy` and `updatedAt`.
- Tests:
  - concurrent/stale update behavior
  - conflict-path status mapping

### Step 7: Validation Gate and Phase Artifact Updates
- Run targeted suites for migration/repository/service/controller changes.
- Run full backend suite (`./mvnw test`).
- Record validation evidence in this file immediately after each completed slice.
- Update `phases.md` status as slices complete.

## Changes
- None yet. Phase is planned and ready for Step 1 implementation.

## Validation
- Not run yet for Phase 2.

## Notes for Next Agent
- Execute Step 1 first as a documentation contract lock gate before starting migration/service/API work.
- Keep all Phase 2 changes additive and backward-compatible.
- Defer decision execution and due-worker behavior to Phase 4.
