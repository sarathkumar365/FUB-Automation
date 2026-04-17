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

### Phase 2 — Local Data Read-Model Foundation
- Persist assignment/lead snapshot updates into local lead state from supported assignment webhooks.
- Persist call facts required for communication classification into local store (for example: lead/person id, direction, duration, outcome, occurred-at).
- Define and implement a local query contract for "latest outbound call by lead + lookback".
- Explicit constraint: do not add new external Follow Up Boss read API dependencies in this phase.
- Outcome: communication/tasking decisions can be sourced from local persisted data.

### Phase 3 — Step Library Expansion on Local Data
- Add `check_communication_success` workflow step using Phase 2 local query contract.
- Add `fub_create_task` workflow step.
- Add structured task payload mapping (lead info + attempt metadata placeholders).
- Outcome: task generation and communication-classification path available in workflow graph without new read-time FUB fetches.

### Phase 4 — Attempt Tracking Primitive
- Introduce business-level attempt counter persistence keyed by lead/workflow context.
- Add branch/guard semantics for max-attempt threshold.
- Outcome: bounded call-attempt loop semantics (up to N attempts).

### Phase 5 — Cross-Event Lead State Machine + Hardening
- Add lead-state store transition model and router pre-check/state lookup.
- Correlate callback events to prior owner and pool state.
- Add idempotent transition writes, concurrency controls, observability/audit fields, and operator-safe replay/recovery.
- Outcome: callback restore and lifecycle continuity across events with deterministic transitions and operator controls.

## Acceptance Target (final)
- System handles full lifecycle: intake -> attempts -> task/pool fallback -> callback restore -> engaged/failed-search outcomes with deterministic state transitions.
