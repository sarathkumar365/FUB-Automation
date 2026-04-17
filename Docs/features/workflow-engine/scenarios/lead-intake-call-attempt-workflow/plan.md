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

### Phase 2 — Local Data Read-Model Foundation (decision-complete)
- Active implementation record: [lead-data-foundation-plan.md](lead-data-foundation-plan.md).
- Current execution status (2026-04-17): Phase A and Phase B completed; Phase C pending (see [phase-2-implementation.md](phase-2-implementation.md)).
- Persist assignment/lead snapshot updates from supported assignment webhooks into local lead state.
- Extend `processed_calls` to persist call facts needed for communication classification (person/lead linkage, direction, duration, outcome, call/event timestamps, enrichment metadata).
- Introduce runtime lead-state wiring for the existing `leads` table (entity/repository/service upsert path keyed by `source_system + source_lead_id`).
- Define local query contract: latest outbound call evidence by lead/person within lookback.
- Refactor `wait_and_check_communication` to evaluate local evidence first.
- If local evidence is insufficient to decide, fallback to existing `FollowUpBossClient#checkPersonCommunication(...)`.
- Keep `wait_and_check_claim` on the current external read path in this phase.
- Explicit constraint: do not add new external Follow Up Boss endpoint dependencies in this phase.
- Outcome: communication decisions are local-first, with controlled fallback only when local evidence is incomplete.

#### Phase 2 default rules
- Default lookback window: `30 minutes`.
- Communication success rule: `connected outbound only`.
- Direction/evidence missing locally: `fallback to existing FUB communication check`.
- Maintain existing step result code contract: `COMM_FOUND` / `COMM_NOT_FOUND`.

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

## Phase 2 Integration Flow (how pieces connect)
1. Ingest webhook and persist normalized event in `webhook_events`.
2. For `callsCreated`, keep existing call-detail fetch path and persist normalized call facts into extended `processed_calls`.
3. For `peopleCreated`/`peopleUpdated`, upsert `leads` snapshot row from `resourceIds`-derived identity and latest source payload details.
4. During `wait_and_check_communication`, query local latest outbound evidence within lookback.
5. If local evidence is complete, evaluate locally; otherwise call existing FUB communication read.
6. Return existing workflow result codes without graph/schema changes.

## Phase 2 Validation Expectations
- Migration + mapping tests for new `processed_calls` fact columns.
- Runtime tests proving call facts are persisted during call processing.
- Lead upsert tests for assignment events into `leads`.
- Local query tests for latest outbound + lookback filtering.
- Step execution tests for both paths:
  - local evidence sufficient -> no fallback call
  - local evidence insufficient -> fallback call used
- Integration regression to confirm existing workflow and admin step catalog behavior remains intact.

## Acceptance Target (final)
- System handles full lifecycle: intake -> attempts -> task/pool fallback -> callback restore -> engaged/failed-search outcomes with deterministic state transitions.
