# Repository Decisions

This folder is the repo-wide decision registry for architectural and process decisions that apply beyond a single feature.

## Mandatory Read Order (before implementation)
1. This file (`Docs/repo-decisions/README.md`)
2. All `Accepted` decision files relevant to touched modules
3. Target feature docs under `Docs/features/<feature-slug>/`

## Active Decisions
- `RD-001-normalized-lead-event-contract.md` — Status: Accepted
- `RD-002-event-catalog-state-and-routing-model.md` — Status: Accepted
- `RD-003-lead-identity-mapping-boundary.md` — Status: Accepted

## Decision Lifecycle
- Status values: `Proposed`, `Accepted`, `Superseded`, `Deprecated`
- Implementation-critical behavior must reference `Accepted` decisions.
- If a feature RFC introduces a repo-wide decision, promote it to this folder in the same phase.
