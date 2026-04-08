# RFC-003: Lead Identity Mapping Boundary (V1)

## Status
Deferred for current implementation (identity resolver contract removed in current phase)

## Repo-Level Decision Reference
- `RD-003-lead-identity-mapping-boundary.md`
- Related: `RD-001-normalized-lead-event-contract.md`

## Purpose
Define the boundary contract that maps source lead identity (`sourceSystem`, `sourceLeadId`) to internal lead identity for domain logic.

Current implementation note:
- This boundary is not active in the current phase.
- Runtime planning/execution uses `sourceLeadId` directly.
- `internalLeadRef` and `BLOCKED_IDENTITY` runtime semantics were removed from active contracts.

## Decisions Locked
- Mapping boundary exists as a service/port boundary (not parser responsibility)
- Parser only extracts source identity fields; it does not resolve internal identity
- V1 supports `internal` and `fub` as source systems

## Boundary Interface Contract (Deferred)
The originally proposed interface for this RFC is deferred and not part of the active runtime contract in this phase.

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

## V1 Behavior Rules (Deferred)
- For active runtime behavior, `sourceLeadId` is used directly for assignment-domain execution.
- No identity-resolution gate is applied in current planning flow.

Mapping not found behavior (deferred):
- The non-executable identity-mapping fallback path is not active in current implementation.

Temporary fallback in V1:
- Deferred with identity boundary reintroduction in a later phase.

## Non-Goals in V1
- Multi-key fuzzy identity matching
- Cross-source merge heuristics
- Identity conflict resolution tooling
