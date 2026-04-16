# Lead Intake Call-Attempt Workflow — Research

## Objective
Capture and assess the requested complex lead workflow under current workflow-engine capabilities.

## Requested Flow (from discussion)
1. Lead intake from FUB and initial owner assignment.
2. Agent call decision path.
3. Retry call-attempt loop up to 10 attempts.
4. On non-response/exhaustion, create follow-up task under agent with metadata.
5. Post-task decision: keep with agent vs move to pool.
6. Pool handling for redistribution.
7. Incoming callback logic: restore to same agent and remove from pool.
8. Pickup scenario: mark engaged, stop retries.
9. Failed-search fallback for future campaigns.

## Current Workflow-Engine Capability Fit
### Supported now (workflow-only)
- Triggered workflow runs for FUB assignment events.
- Delayed checks via `wait_and_check_claim` / `delay`.
- Branching by result codes and terminal outcomes.
- Move lead to pond via `fub_move_to_pond`.

### Not fully supported now
- Native step for outbound call execution + attempt tracking loop as business state.
- Native workflow step for FUB task creation with assignment/metadata payload.
- Cross-event state-machine behavior (callback restore to same prior owner across separate runs/events).
- Trigger authoring via workflow create/update API (trigger still DB-seeded today).
- Reliable lead-stage trigger filtering from parsed trigger payload.

## Platform Limitations Categorized
1. Missing workflow step capabilities
- No first-class step for outbound call attempts.
- No first-class workflow step for FUB task creation.

2. Stateful orchestration limits
- No built-in lead-level state machine spanning multiple webhook events/runs.
- No native ownership-memory/correlation primitive for callback restoration.

3. Business-loop semantics
- Engine retry handles transient execution errors, not business-level "attempt #1..10" progression semantics.

4. Trigger/filtering contract limits
- Trigger is not persisted by create/update API yet.
- Lead stage is not exposed as a stable parsed trigger field for filtering.

## Discussion Notes (design direction)
- Proposed direction accepted in discussion: add lead-state pre-check in/around router.
- This is effectively a cross-event state-machine pattern and is coupled with concurrency/idempotency controls.
- New steps can be added incrementally, but cross-event correctness needs durable state + transition governance.
