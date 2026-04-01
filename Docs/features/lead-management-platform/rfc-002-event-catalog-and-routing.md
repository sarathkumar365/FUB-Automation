# RFC-002: Event Catalog and Routing (V1)

## Status
Approved for Sprint 0

## Repo-Level Decision Reference
- `RD-002-event-catalog-state-and-routing-model.md`
- Related: `RD-001-normalized-lead-event-contract.md`

## Purpose
Define event catalog states and routing behavior so event support expands safely without accidental execution.

## Decisions Locked
- Batch 1 implementation scope: assignment-domain-first
- Non-Batch-1 events are cataloged but not executed
- Routing occurs after normalization and catalog resolution

## Event Catalog Model
Each catalog entry has:
- `sourceSystem` (required)
- `sourceEventType` (required)
- `normalizedDomain` (required)
- `normalizedAction` (required)
- `state` (required): `SUPPORTED`, `STAGED`, `IGNORED`
- `notes` (optional)

## Catalog States and Runtime Behavior
- `SUPPORTED`
  - Event is normalized and routed to domain handler
  - Ingress response indicates accepted for processing
- `STAGED`
  - Event is normalized and persisted/observable
  - No domain execution
  - Ingress response indicates staged/not yet executed
- `IGNORED`
  - Event is normalized for observability and dedupe context
  - No domain execution
  - Ingress response indicates ignored

## Routing Contract
Input: normalized event from parser

Resolution order:
1. Resolve catalog entry by `sourceSystem + sourceEventType`
2. Apply state behavior
3. If `SUPPORTED`, route by `normalizedDomain`

Domain handler mapping in V1:
- `call` -> existing call processor (unchanged behavior)
- `assignment` -> placeholder route target in Phase 1; full domain execution starts in later phase
- `unknown` -> no execution

## Batch 1 Catalog Entries (V1)
- `fub:callsCreated` -> `SUPPORTED` (existing)
- `fub:peopleCreated` -> `STAGED` in Phase 1, later promoted to `SUPPORTED`
- `fub:peopleUpdated` -> `STAGED` in Phase 1, later promoted to `SUPPORTED`
- other `fub:*` -> `IGNORED` default unless explicitly added

## Failure/Edge Rules
- Missing catalog entry defaults to `IGNORED`
- `SUPPORTED` with unresolved domain handler must fail-safe to no execution and log explicit error
- Catalog decisions must be deterministic and test-covered

## Observability Requirements
Log at minimum:
- `sourceSystem`, `sourceEventType`, `catalogState`, `normalizedDomain`, `eventId`

Expose in admin payload/history:
- resolved `catalogState`
- routed domain (if any)
