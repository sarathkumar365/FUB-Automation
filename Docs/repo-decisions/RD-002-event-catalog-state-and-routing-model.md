# RD-002: Event Catalog State and Routing Model

## Status
Accepted

## Context
As event coverage grows, runtime behavior must be explicit and safe. Unsupported or staged events must not accidentally execute business actions.

## Decision
Use a source-specific event catalog keyed by `(sourceSystem, sourceEventType)` with state-controlled runtime behavior.

Catalog states:
- `SUPPORTED`: route and execute mapped domain handler
- `STAGED`: normalize/persist/observe but do not execute domain action
- `IGNORED`: normalize/observe minimally, no execution

Routing model:
- Normalize event first
- Resolve catalog state
- Route by normalized domain only when state is `SUPPORTED`

## Impact
- Safe, phased onboarding of events
- Clear operational semantics for what runs vs what is staged
- Reduced risk when adding new providers/event types

## Applies To
- Repo-wide
- Ingress, orchestration, event processing, admin observability

## Supersedes / Superseded By
- Supersedes: none
- Superseded by: none
