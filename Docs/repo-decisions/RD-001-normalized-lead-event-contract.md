# RD-001: Normalized Lead Event Contract

## Status
Accepted

## Context
The platform must support internal lead inflow and multiple external providers without leaking provider-specific payload shapes into core orchestration and domain logic.

## Decision
Adopt a source-agnostic normalized lead event contract as the repo-wide baseline for ingestion/routing.

Locked V1 decisions:
- source systems: `internal`, `fub`
- no explicit contract version field in V1
- provider transport details are kept in provider metadata fields, not domain core fields
- support-state decisions (`SUPPORTED`, `STAGED`, `IGNORED`) are handled by event catalog policy (RD-002)

## Impact
- Parser and ingress contracts align on one event shape.
- Domain handlers consume normalized semantics instead of provider event names.
- Future providers can be added with adapter/parser mapping, without redefining core contracts.

## Applies To
- Repo-wide
- Ingestion, normalization, orchestration, domain routing
- Features using webhook/event-driven automation

## Normalized event field schema

Source of truth: `service/webhook/model/NormalizedWebhookEvent.java` (+ `NormalizedDomain`, `NormalizedAction`). Fields (migrated here from the former RFC-001 so the contract has a non-feature-scoped home; terminology updated for the later Lead→Person rename):

| Field | Req? | Notes |
|---|---|---|
| `sourceSystem` | required | enum: `internal`, `fub` |
| `sourceEventType` | required | provider event type, e.g. `peopleUpdated`, `callsCreated`, `notesCreated` |
| `receivedAt` | required | ingestion timestamp |
| `normalizedDomain` | required | enum: `PERSON`, `CALL`, `NOTE`, `UNKNOWN` (was `assignment`/`call`/`unknown` pre-rename) |
| `normalizedAction` | required | `created`/`updated`/`deleted`/`unknown` |
| `payload` | required | normalized payload for domain handlers |
| `eventId` | optional | stable id for dedupe when available |
| `occurredAt` | optional | source event timestamp |
| `sourcePersonId` | optional | source-system person id (was `sourceLeadId`); required only for flows that depend on person identity |
| `providerMeta` | optional | transport extras (`headers`, `uri`, raw `resourceIds`) |
| `payloadHash` | optional | fallback dedupe hash |

Nullability: optional fields are omitted/null consistently by the parser; domain handlers must not fail solely on missing optional fields.

## Supersedes / Superseded By
- Supersedes: feature-folder `rfc-001-normalized-lead-event-contract.md` (folded in here, 2026-06-03)
- Superseded by: none
