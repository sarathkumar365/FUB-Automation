# Phase 1 Implementation Log

Status: Completed (Step 1, Step 2, Step 3, Step 4, and Step 5 completed)

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
  - `EventSupportState`: `SUPPORTED`, `STAGED`, `IGNORED`
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
- Step 1 completed: normalized event contract expanded with top-level semantic fields while keeping compatibility payload fields for current call flow.
- Added normalized enums/types:
  - `NormalizedDomain` (`CALL`, `ASSIGNMENT`, `UNKNOWN`)
  - `NormalizedAction` (`CREATED`, `UPDATED`, `ASSIGNED`, `UNKNOWN`)
- Added `WebhookSource.INTERNAL` readiness in source enum (no new active ingress path introduced).
- Updated FUB parser to map:
  - top-level `sourceEventType`, `normalizedDomain`, `normalizedAction`, `providerMeta`
  - compatibility payload dual-write keys (`eventType`, `resourceIds`, `uri`, `headers`, `rawBody`)
- Updated ingress `eventType` extraction precedence:
  - top-level `sourceEventType` first
  - payload `eventType` fallback for compatibility safety
- Added explicit deferred TODO issue in parser:
  - `TODO(step1-followup): finalize sourceLeadId extraction rule by event semantics`
  - issue id: `LMP-STEP1-SOURCE-LEADID-RULE`
- Updated replay dispatch construction and impacted call sites for enriched `NormalizedWebhookEvent` signature.
- Added new dedicated test suites:
  - `NormalizedWebhookEventContractTest`
  - `FubWebhookParserNormalizedContractTest`
  - `WebhookIngressEventTypePrecedenceTest`
  - `WebhookSourceInternalReadinessTest`
- Step 2 completed as non-runtime foundation:
  - Added `EventSupportState` enum (`SUPPORTED`, `STAGED`, `IGNORED`) as canonical state naming.
  - Added resolver contract and static in-code resolver implementation:
    - `WebhookEventSupportResolver`
    - `EventSupportResolution`
    - `StaticWebhookEventSupportResolver`
  - Centralized source-event semantic ownership in resolver mapping table for:
    - `fub:callsCreated`
    - `fub:peopleCreated`
    - `fub:peopleUpdated`
    - fallback to `IGNORED/UNKNOWN/UNKNOWN`
  - Added parser TODO markers clarifying parser semantic mapping is temporary compatibility and should be deprecated after Step 3 resolver wiring.
  - No ingress/dispatch/persistence/admin contract runtime behavior was changed in this step.
- Step 3 completed: catalog resolution is now wired into runtime persistence and dispatch gating.
  - Added Flyway migration:
    - `V4__add_catalog_resolution_fields_to_webhook_events.sql`
    - additive columns: `catalog_state`, `normalized_domain`, `normalized_action`, `source_lead_id`
  - Extended webhook event persistence model:
    - `WebhookEventEntity` now stores catalog state and normalized domain/action plus optional source lead id
  - Updated ingress orchestration:
    - parse normalized event
    - resolve support with `WebhookEventSupportResolver`
    - persist resolved values
    - publish live feed
    - dispatch only when support state is `SUPPORTED`
  - Preserved duplicate detection behavior for `eventId` and `payloadHash`.
  - Extended admin read model and DTO contracts with additive fields:
    - `catalogState`, `normalizedDomain`, `normalizedAction`
  - Updated parser semantic ownership notes:
    - resolver is runtime source of truth
    - parser semantic mapping remains compatibility metadata
  - Updated regression/integration expectations for unsupported events:
    - unsupported events are persisted and non-executing in ingress
    - no processed-call execution side effects for non-supported event types
- Step 4 completed: runtime processing is now routed by normalized domain with safe non-call placeholders.
  - Refactored webhook processor entrypoint to route by `NormalizedDomain`:
    - `CALL` -> existing call processing flow
    - `ASSIGNMENT` -> explicit Phase 1 no-op placeholder
    - `UNKNOWN` -> explicit safe no-op
  - Extracted existing call execution flow into dedicated call-domain processing path while preserving behavior:
    - resource id extraction and iteration
    - processed-call lifecycle transitions
    - retry/backoff behavior
    - decision engine + task creation outcomes
  - Added explicit observability logging for assignment/unknown placeholder routing.
  - Added staged assignment integration guard:
    - `peopleCreated` persists as staged and remains non-executing (no processed-call/FUB side effects)
  - Added dedicated service-level routing tests for call/assignment/unknown domain behavior.
- Added new dedicated Step 2 test suites:
  - `WebhookEventSupportResolverTest`
  - `EventSupportStateContractTest`
- Step 5 completed: Phase 1 observability exposure and closure artifacts validated.
  - Performed Step 5 gap check across admin read path (`controller -> service -> repository projection`) and confirmed `catalogState`, `normalizedDomain`, and `normalizedAction` are exposed in list/detail contracts.
  - Confirmed response compatibility posture remains additive (no breaking contract changes required).
  - Finalized Phase 1 closure artifacts:
    - updated this file with Step 5 completion + validation results
    - updated `phases.md` Phase 1 status to `Completed`

## Validation
- Executed new dedicated and impacted suites:
  - `./mvnw test -Dtest=NormalizedWebhookEventContractTest,FubWebhookParserNormalizedContractTest,WebhookIngressEventTypePrecedenceTest,WebhookSourceInternalReadinessTest,FubWebhookParserTest,WebhookIngressServiceTest,WebhookIngressFlowTest,WebhookProcessingFlowTest`
  - Result: pass (31 tests, 0 failures, 0 errors)
- Executed full backend suite:
  - `./mvnw test`
  - Result: pass (107 tests run, 0 failures, 0 errors, 2 skipped)
- Executed Step 2 dedicated and compatibility guard suites:
  - `./mvnw test -Dtest=WebhookEventSupportResolverTest,EventSupportStateContractTest,WebhookIngressServiceTest,WebhookIngressFlowTest,WebhookProcessingFlowTest`
  - Result: pass (24 tests, 0 failures, 0 errors)
- Re-executed full backend suite after Step 2:
  - `./mvnw test`
  - Result: pass (112 tests run, 0 failures, 0 errors, 2 skipped)
- Executed Step 3 targeted suites:
  - `./mvnw test -Dtest=WebhookIngressServiceTest,WebhookIngressEventTypePrecedenceTest,AdminWebhookServiceTest,AdminWebhookControllerTest,JdbcWebhookFeedReadRepositoryTest`
  - Result: pass (31 tests, 0 failures, 0 errors)
- Executed newly added/updated integration guard for non-supported dispatch behavior:
  - `./mvnw test -Dtest=WebhookProcessingFlowTest`
  - Result: pass (13 tests, 0 failures, 0 errors)
- Re-executed full backend suite after Step 3:
  - `./mvnw test`
  - Result: pass (113 tests run, 0 failures, 0 errors, 2 skipped)
- Executed Step 4 targeted suites:
  - `./mvnw test -Dtest=WebhookEventProcessorServiceTest,WebhookProcessingFlowTest,WebhookIngressServiceTest`
  - Result: pass (23 tests, 0 failures, 0 errors)
- Re-executed full backend suite after Step 4:
  - `./mvnw test`
  - Result: pass (117 tests run, 0 failures, 0 errors, 2 skipped)
- Executed Step 5 targeted suites:
  - `./mvnw test -Dtest=AdminWebhookServiceTest,AdminWebhookControllerTest,JdbcWebhookFeedReadRepositoryTest,AdminWebhooksFlowTest,AdminWebhooksPostgresRegressionTest`
  - Result: pass (27 tests run, 0 failures, 0 errors, 1 skipped)
- Re-executed full backend suite for Phase 1 closure:
  - `./mvnw test`
  - Result: pass (117 tests run, 0 failures, 0 errors, 2 skipped)

## Notes for Next Agent
- Before coding, re-read all Sprint 0 RFC files and ensure no drift with `lead-management-platform-plan.md`.
- Follow layered boundary rule for every slice: `controller -> service -> port -> adapter -> repository/rules`.
- Keep Phase 1 strictly foundation-level; defer assignment action execution, delayed worker, and policy control-plane persistence to later phases.
- Step 3 should wire resolver outcomes into ingress/persistence/dispatch gating; Step 2 intentionally did not add runtime routing behavior.
- Phase 1 is closed; next execution track starts at Phase 2 (`phase-2-implementation.md`).
