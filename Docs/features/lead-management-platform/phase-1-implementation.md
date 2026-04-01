# Phase 1 Implementation Log

Status: Not started

## Preconditions (must be true before code changes)
- Sprint 0 RFC pack is approved:
  - `rfc-001-normalized-lead-event-contract.md`
  - `rfc-002-event-catalog-and-routing.md`
  - `rfc-003-lead-identity-mapping-boundary.md`
- No open contract ambiguities remain for:
  - normalized event required/optional fields
  - catalog state behavior
  - identity mapping resolution behavior

## Scope
- Execute Phase 1 through five small vertical slices that establish normalized contracts, catalog-state routing, and observability without introducing assignment execution.
- Keep all API and runtime behavior backward-compatible and additive for existing webhook/admin consumers.
- Include `internal` source readiness in contracts/catalog types only; do not add a new active ingress path in this phase.

## Vertical Implementation Plan (5 Steps)

### Step 1: Normalize Event Contract and Source Readiness
- Expand normalized webhook event model with RFC-001 semantics:
  - source system
  - source event type
  - normalized domain/action
  - optional source lead id
  - optional occurred timestamp
  - provider metadata
  - payload hash
  - received timestamp
- Add `INTERNAL` source enum readiness while preserving existing FUB ingress behavior.
- Update FUB parser mapping so provider transport data remains metadata-only and optional fields are null/omitted consistently.
- Preserve compatibility for current call-flow consumers during contract expansion.
- Tests:
  - parser mapping for `callsCreated`, `peopleCreated`, `peopleUpdated`
  - optional-field absence does not fail parsing
  - existing parser tests remain green

### Step 2: Add Catalog State Domain Model and Resolver
- Introduce explicit types:
  - `CatalogState`: `SUPPORTED`, `STAGED`, `IGNORED`
  - normalized domain and normalized action enums/types
- Add deterministic resolver boundary keyed by `(sourceSystem, sourceEventType)`.
- Phase 1 catalog map:
  - `fub:callsCreated -> SUPPORTED, call, created`
  - `fub:peopleCreated -> STAGED, assignment, created`
  - `fub:peopleUpdated -> STAGED, assignment, updated`
  - default -> `IGNORED, unknown, unknown`
- Add `internal` source fallback entries in non-executing posture.
- Tests:
  - explicit mapping cases
  - unknown/default fallback behavior
  - deterministic resolver behavior

### Step 3: Persist Catalog Resolution and Keep Ingress Compatibility
- Add Flyway migration extending `webhook_events` with additive columns:
  - `catalog_state`
  - `normalized_domain`
  - `normalized_action`
  - `source_lead_id` (nullable)
- Update entity/repository/feed read-model projection for new columns.
- Refactor ingress orchestration order:
  - parse normalized event
  - resolve catalog
  - persist resolved fields
  - dispatch only if state is `SUPPORTED`
- Keep duplicate detection behavior unchanged.
- Keep response contract backward-compatible and additive.
- Tests:
  - ingress unit tests for SUPPORTED/STAGED/IGNORED dispatch differences
  - duplicate handling unchanged
  - integration coverage for staged/ignored persistence without execution

### Step 4: Split Runtime Processing by Domain with Safe Placeholders
- Refactor processing entrypoint from event-type hardcoding to domain-aware routing.
- Extract existing call logic behind call-domain processor and invoke only for `SUPPORTED + call`.
- Add assignment-domain placeholder target (no-op in Phase 1; observability only).
- Ensure non-supported states do not execute domain actions.
- Preserve existing call decision/task creation behavior unchanged.
- Tests:
  - call flow regression tests unchanged
  - assignment-staged events do not create processed-call rows/actions
  - ignored/unknown events are persisted and non-executing

### Step 5: Expose Observability Fields and Finalize Phase Artifacts
- Extend admin feed/detail DTOs with additive fields:
  - `catalogState`
  - `normalizedDomain`
  - `normalizedAction`
- Ensure controller/admin responses include new fields without breaking existing filters.
- Update Phase 1 artifacts immediately after each completed slice:
  - this file (`phase-1-implementation.md`)
  - `phases.md` status/progress
- Run validation gate:
  - execute newly added tests
  - execute existing backend suite (`./mvnw test`)
  - require overall test success threshold >85%
- Tests:
  - admin service/controller coverage for new additive fields
  - admin list/detail regression coverage retained

## Changes
- Planning only: detailed 5-step vertical implementation plan added for Phase 1 execution.

## Validation
- Planning-only update; no runtime code changes executed in this step.

## Notes for Next Agent
- Before coding, re-read all Sprint 0 RFC files and ensure no drift with `lead-management-platform-plan.md`.
- Follow layered boundary rule for every slice: `controller -> service -> port -> adapter -> repository/rules`.
- Keep Phase 1 strictly foundation-level; defer assignment action execution, delayed worker, and policy control-plane persistence to later phases.
