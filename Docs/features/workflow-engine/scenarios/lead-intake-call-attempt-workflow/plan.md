# Lead Intake Call-Attempt Workflow — Plan

## Scope
Define a phased path from current workflow-only capability to the requested end-state flow.

## Phase Plan
### Phase 1 — Workflow-Only Baseline (available now)
- Trigger on `ASSIGNMENT + CREATED`.
- Wait and check claim.
- If claimed: complete.
- If not claimed: move to pond.
- Outcome: production-usable minimal baseline.

### Phase 2 — Step Library Expansion
- Add `fub_create_task` workflow step.
- Add structured task payload mapping (lead info + attempt metadata placeholders).
- Outcome: task generation path available in workflow graph.

### Phase 3 — Attempt Tracking Primitive
- Introduce business-level attempt counter persistence keyed by lead/workflow context.
- Add branch/guard semantics for max-attempt threshold.
- Outcome: bounded call-attempt loop semantics (up to N attempts).

### Phase 4 — Cross-Event Lead State Machine
- Add lead-state store and transition model.
- Router pre-check/state lookup before planning runs.
- Correlate callback events to prior owner and pool state.
- Outcome: callback restore and lifecycle continuity across events.

### Phase 5 — Hardening and Operator Controls
- Add idempotent transition writes and concurrency controls.
- Add observability/audit fields for state transitions.
- Add operator-safe replay/recovery for state machine edges.

## Acceptance Target (final)
- System handles full lifecycle: intake -> attempts -> task/pool fallback -> callback restore -> engaged/failed-search outcomes with deterministic state transitions.
