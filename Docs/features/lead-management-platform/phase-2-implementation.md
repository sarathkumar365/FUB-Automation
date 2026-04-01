# Phase 2 Implementation Log

Status: In progress (Step 1 completed)

## Scope
- Deliver persistent runtime policy control for assignment-SLA behavior (DB + service + admin API).
- Establish reliability-safe update semantics (versioned updates, deterministic conflict behavior).
- Keep this phase infrastructure-only: no due-worker execution or assignment action execution.

## Vertical Implementation Plan (7 Steps)

### Step 1: Lock Phase 2 Contract and Boundaries
- Finalize v1 policy contract in this document before code changes.
- Lock policy fields:
  - `id`
  - `enabled`
  - `dueAfterMinutes`
  - `version`
  - `status` (`ACTIVE`, `INACTIVE`)
- Lock defaults/validation:
  - default `enabled=true`
  - default `dueAfterMinutes=15`
  - `dueAfterMinutes >= 1`
  - exactly one policy may be `ACTIVE` at a time
- Lock API contract (including creation/activation):
  - `GET /admin/policies/assignment-sla/active` (get active policy)
  - `GET /admin/policies/assignment-sla` (list policies, newest first)
  - `POST /admin/policies/assignment-sla` (create policy, default `INACTIVE`)
  - `PUT /admin/policies/assignment-sla/{id}` (update with `expectedVersion`)
  - `POST /admin/policies/assignment-sla/{id}/activate` (activate with `expectedVersion`)
- Lock validation/API semantics:
  - invalid input -> `400`
  - not found -> `404`
  - stale version -> `409`
  - activation conflict violating single-active invariant -> `409`
- Lock out-of-scope for Phase 2:
  - due-worker runtime execution
  - assignment decision execution
  - external provider mutation actions
  - policy audit metadata/history beyond row state

### Step 2: Add Policy Schema, Seed, and Invariants
- Add Flyway migration: `V5__create_assignment_sla_policy.sql`.
- Include DB constraints:
  - positive `dueAfterMinutes`
  - version column for optimistic concurrency
  - enforce allowed `status` values (`ACTIVE`, `INACTIVE`)
- Enable multi-policy storage with a single-active-policy invariant.
- Seed one deterministic default policy row.

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
  - `GET /admin/policies/assignment-sla/active`
  - `GET /admin/policies/assignment-sla`
  - `POST /admin/policies/assignment-sla`
  - `PUT /admin/policies/assignment-sla/{id}` (requires `expectedVersion`)
  - `POST /admin/policies/assignment-sla/{id}/activate` (requires `expectedVersion`)
- Add request/response DTOs exposing:
  - policy fields
  - `version`
  - `status`
- Keep response contract additive and consistent with existing admin controller conventions.

### Step 6: Add Reliability and Governance Guards
- Ensure update path is atomic and cannot overwrite newer concurrent updates silently.
- Ensure activation path preserves the single-active invariant.
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
- Locked Phase 2 v1 policy contract to minimal multi-policy fields:
  - `id`, `enabled`, `dueAfterMinutes`, `version`, `status`
- Locked defaults/validation:
  - `enabled=true`, `dueAfterMinutes=15`, `dueAfterMinutes >= 1`
  - single-active-policy invariant
- Locked API contract:
  - `GET /admin/policies/assignment-sla/active`
  - `GET /admin/policies/assignment-sla`
  - `POST /admin/policies/assignment-sla`
  - `PUT /admin/policies/assignment-sla/{id}`
  - `POST /admin/policies/assignment-sla/{id}/activate`
- Locked error semantics:
  - `400` invalid input
  - `404` not found
  - `409` stale version / activation conflict

## Validation
- Docs consistency check completed:
  - Phase 2 contract in this file now aligns with policy-storage direction in `lead-management-platform-plan.md`.
  - No runtime tests required for Step 1 (docs-only gate).

## Notes for Next Agent
- Step 2 is next: start DB migration for multi-policy storage and enforce single-active invariant.
- Keep all Phase 2 changes additive and backward-compatible.
- Defer decision execution and due-worker behavior to Phase 4.
