# Plan

> ⚠️ **Historical (2026-06-03).** This feature built a **policy engine** that was later **removed** (Flyway V12) and replaced by the workflow engine + domain-events feature; `leads` became `persons` (V21). The docs here are kept as build history. The Sprint-0 RFCs were promoted to repo decisions and the RFC files removed — see `Docs/repo-decisions/RD-001/RD-002/RD-003`.

## Primary Plan Source
- `lead-management-platform-plan.md`

## Sprint 0 RFC Pack (promoted to repo-decisions)
- `Docs/repo-decisions/RD-001-normalized-lead-event-contract.md` (field schema folded in)
- `Docs/repo-decisions/RD-002-event-catalog-state-and-routing-model.md`
- `Docs/repo-decisions/RD-003-lead-identity-mapping-boundary.md` (superseded — boundary removed in V8)

## RFC Lock Checklist
- [x] Normalized event contract locked (required/optional fields, nullability, compatibility mapping)
- [x] Event catalog states and routing behavior locked
- [x] Lead identity mapping boundary contract locked (later deferred/removed from active runtime contract)
- [x] Batch 1 scope locked as assignment-domain-first
- [x] V1 source systems locked to `internal` and `fub`
- [x] V1 event contract versioning locked to no explicit version field

## Implementation Gate
Phase 1 implementation can start only after all Sprint 0 RFC checklist items are complete and consistent.
