# Plan

## Primary Plan Source
- `lead-management-platform-plan.md`

## Sprint 0 RFC Pack (must be locked before code)
- `rfc-001-normalized-lead-event-contract.md`
- `rfc-002-event-catalog-and-routing.md`
- `rfc-003-lead-identity-mapping-boundary.md`

## RFC Lock Checklist
- [x] Normalized event contract locked (required/optional fields, nullability, compatibility mapping)
- [x] Event catalog states and routing behavior locked
- [x] Lead identity mapping boundary contract locked (later deferred/removed from active runtime contract)
- [x] Batch 1 scope locked as assignment-domain-first
- [x] V1 source systems locked to `internal` and `fub`
- [x] V1 event contract versioning locked to no explicit version field

## Implementation Gate
Phase 1 implementation can start only after all Sprint 0 RFC checklist items are complete and consistent.
