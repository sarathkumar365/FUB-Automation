# RD-003: Lead Identity Mapping Boundary

## Status
Accepted

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
