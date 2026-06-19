# Repository Decisions

This folder is the repo-wide decision registry for architectural and process decisions that apply beyond a single feature.

## Mandatory Read Order (before implementation)
1. This file (`Docs/repo-decisions/README.md`)
2. All `Accepted` decision files relevant to touched modules
3. Target feature docs under `Docs/features/<feature-slug>/`

## Active Decisions
- `RD-001-normalized-lead-event-contract.md` — Status: Accepted
- `RD-002-event-catalog-state-and-routing-model.md` — Status: Accepted
- `RD-003-lead-identity-mapping-boundary.md` — Status: **Superseded / not implemented** (identity-resolver boundary removed in V8; see the RD)
- `RD-004-admin-auth-uses-jwt-bearer.md` — Status: Accepted
- `RD-005-product-name-throughline.md` — Status: Provisional (pending trademark/domain clearance)
- `RD-006-engine-echo-exclusion-safe-by-default.md` — Status: Accepted (enforcement in domain-events Phase 4d)
- `RD-008-profile-enrichment-inferred-kind.md` — Status: Accepted (person-profile-enrichment feature)
- `RD-009-loop-primitive-foreman-cloned-rows.md` — Status: Accepted (implementation planned, loop-primitive feature)
- `RD-010-step-type-categories.md` — Status: Accepted (implementation = loop-primitive Phase 0)
- `RD-011-ui-schema-ownership.md` — Status: Accepted (ui/ only; implementation = ui-architecture-conformance Phase 2)

## Decision Lifecycle
- Status values: `Proposed`, `Accepted`, `Superseded`, `Deprecated`
- Implementation-critical behavior must reference `Accepted` decisions.
- If a feature RFC introduces a repo-wide decision, promote it to this folder in the same phase.
