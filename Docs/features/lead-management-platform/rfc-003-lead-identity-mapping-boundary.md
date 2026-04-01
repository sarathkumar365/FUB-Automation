# RFC-003: Lead Identity Mapping Boundary (V1)

## Status
Approved for Sprint 0

## Repo-Level Decision Reference
- `RD-003-lead-identity-mapping-boundary.md`
- Related: `RD-001-normalized-lead-event-contract.md`

## Purpose
Define the boundary contract that maps source lead identity (`sourceSystem`, `sourceLeadId`) to internal lead identity for domain logic.

## Decisions Locked
- Mapping boundary exists as a service/port boundary (not parser responsibility)
- Parser only extracts source identity fields; it does not resolve internal identity
- V1 supports `internal` and `fub` as source systems

## Boundary Interface Contract
Input:
- `sourceSystem` (required)
- `sourceLeadId` (required for external sources)

Output:
- `resolved`: boolean
- `internalLeadRef`: string or null
- `resolutionType`: enum-like label
  - `EXACT_MATCH`
  - `SOURCE_IS_INTERNAL`
  - `NOT_FOUND`
  - `INVALID_INPUT`

## Ownership Split
- Normalization layer:
  - Extracts and passes `sourceSystem` + `sourceLeadId`
  - Never performs identity lookup
- Identity mapping boundary/service:
  - Resolves internal lead reference
  - Returns deterministic resolution result
- Downstream domain handlers:
  - Consume resolution output
  - Apply business behavior for unresolved leads

## V1 Behavior Rules
- For `sourceSystem=internal`:
  - `internalLeadRef` may be directly provided by source payload path
  - `resolutionType=SOURCE_IS_INTERNAL`
- For `sourceSystem=fub`:
  - `sourceLeadId` is required for assignment-domain execution
  - missing/blank id => `INVALID_INPUT`

Mapping not found behavior:
- Do not hard-fail ingestion
- Mark as non-executable for assignment action path
- Persist observability context and reason

Temporary fallback in V1:
- If mapping not found, event remains observable and can be replayed later once mapping exists
- No automatic reassignment action may run without resolved internal lead identity

## Non-Goals in V1
- Multi-key fuzzy identity matching
- Cross-source merge heuristics
- Identity conflict resolution tooling
