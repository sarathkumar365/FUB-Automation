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

## Supersedes / Superseded By
- Supersedes: none
- Superseded by: none
