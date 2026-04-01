# RFC-001: Normalized Lead Event Contract (V1)

## Status
Approved for Sprint 0

## Repo-Level Decision Reference
- `RD-001-normalized-lead-event-contract.md`
- Related: `RD-002-event-catalog-state-and-routing-model.md`

## Purpose
Define a source-agnostic, lead-domain normalized event contract for V1 so parser/routing/worker/policy layers share one stable schema.

## Decisions Locked
- V1 source systems: `internal`, `fub`
- No explicit contract version field in V1
- Provider-specific transport fields remain in `providerMeta`, never in domain core fields
- Event support state (`SUPPORTED`/`STAGED`/`IGNORED`) is defined by catalog policy in RFC-002, not by this contract.

## Canonical Contract (V1)

Normalized event object:
- `eventId` (string, required): stable unique event identifier for dedupe when available
- `sourceSystem` (string enum, required): one of `internal`, `fub`
- `sourceEventType` (string, required): provider/source event type, e.g. `peopleUpdated`, `callsCreated`
- `occurredAt` (string datetime, optional): source event timestamp if provided
- `receivedAt` (string datetime, required): ingestion timestamp
- `sourceLeadId` (string, optional): source-system lead/person identifier as string
- `normalizedDomain` (string enum, required): one of `assignment`, `call`, `unknown`
- `normalizedAction` (string, required): domain action label, e.g. `created`, `updated`, `assigned`, `unknown`
- `payload` (object, required): normalized payload used by domain handlers
- `providerMeta` (object, optional): provider transport extras (`headers`, `uri`, raw id arrays)
- `payloadHash` (string, optional): hash of raw payload for fallback dedupe

## Required vs Optional Rules
Required:
- `sourceSystem`, `sourceEventType`, `receivedAt`, `normalizedDomain`, `normalizedAction`, `payload`

Optional:
- `eventId`, `occurredAt`, `sourceLeadId`, `providerMeta`, `payloadHash`

Nullability behavior:
- Missing optional fields MUST be omitted or set to null consistently by parser
- Domain handlers MUST NOT fail solely due to missing optional fields
- `sourceLeadId` may be absent for many event types in V1; it becomes required only for downstream flows that explicitly depend on lead identity (for example, assignment action execution).

## Semantic Routing vs Support State
- `normalizedDomain` and `normalizedAction` represent source-agnostic semantic meaning used for routing intent.
- Whether a given event is actually executed in runtime is determined by event catalog state (`SUPPORTED`, `STAGED`, `IGNORED`) as defined in `rfc-002-event-catalog-and-routing.md`.

## Source-System Rules
- Unknown source values are rejected at source resolution layer (existing behavior)
- Supported V1 values:
  - `fub` from `/webhooks/fub`
  - `internal` reserved for future internal inflow entrypoint

## Backward Compatibility Mapping (Current FUB Payloads)

From current FUB payload shape:
- `eventId` -> `eventId`
- `event` -> `sourceEventType`
- `resourceIds[0]` (if call/person id based on event) -> `sourceLeadId` when it represents person/lead
- `resourceIds` -> `providerMeta.resourceIds`
- `uri` -> `providerMeta.uri`
- selected headers -> `providerMeta.headers`
- `payloadHash` remains unchanged

Domain mapping in V1:
- `callsCreated` -> `normalizedDomain=call`, `normalizedAction=created`
- `peopleCreated` -> `normalizedDomain=assignment`, `normalizedAction=created`
- `peopleUpdated` -> `normalizedDomain=assignment`, `normalizedAction=updated`
- everything else -> `normalizedDomain=unknown`, `normalizedAction=unknown`

## Compatibility Requirement for Phase 1
- Existing call flow must continue working from normalized `payload` + mapped metadata.
- Parser must not hard-fail purely because `resourceIds` is absent for non-call events.

## Open for V2 (Not in V1)
- Explicit event contract version field
- Extended source enum
- Rich typed domain payload DTO replacement for raw JsonNode
