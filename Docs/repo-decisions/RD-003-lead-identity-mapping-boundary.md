# RD-003: Lead Identity Mapping Boundary

## Status
**Superseded / not implemented (2026-06-03).** The dedicated identity-resolver boundary this RD specified (`LeadIdentityResolver`, `internal_lead_ref`, `BLOCKED_IDENTITY`) was **removed from the codebase in migration V8** (`V8__remove_identity_resolver_contract.sql`) and never shipped as a runtime contract. Identity is now handled by the composite `(source_system, source_person_id)` key directly on `persons`/`events`. Retained as a record of the original decision; do not treat as an active contract.

## Context
Cross-source lead workflows require a stable boundary between source identifiers and internal lead identity. Without this boundary, domain handlers become provider-coupled.

## Decision
Identity mapping is a dedicated boundary/service contract.

Rules:
- parser extracts source identity fields only
- parser does not resolve internal identity
- identity mapping service resolves `(sourceSystem, sourceLeadId)` to internal lead reference
- unresolved mappings do not fail ingestion; they become non-executable for dependent actions until resolved

## Impact
- Clear ownership split between normalization and domain execution
- Safer multi-source expansion
- Replayable behavior for events received before mapping exists

## Applies To
- Repo-wide
- Ingestion, assignment/call domains, future lead workflows

## Supersedes / Superseded By
- Supersedes: none
- Superseded by: none
