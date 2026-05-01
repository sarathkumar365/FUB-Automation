# Wait & Check Communication Conversational Enhancement Plan (Code + Tests Only)

## Summary
Implement a single-pass change for `wait_and_check_communication` only:
- Add conversational classification with 3 outcomes:
  - `CONVERSATIONAL`
  - `CONNECTED_NON_CONVERSATIONAL`
  - `COMM_NOT_FOUND`
- Evaluate local call evidence first, fallback to FUB only when local evidence is insufficient.
- Do **not** update workflow graphs in DB as part of this work.

## Implementation Changes

### Step contract + behavior
- Update `wait_and_check_communication` declared result codes to the 3-code contract above.
- Add optional `lookbackMinutes` config (default `30`, minimum `1`).
- Keep existing lead-id parse/validation and transient/permanent error handling pattern.

### Local-first communication evaluation
- Query latest outbound local call(s) for `source_lead_id` within lookback window.
- Classify with existing threshold property (`rules.call-outcome.short-call-threshold-seconds`):
  - `duration_seconds > threshold` -> `CONVERSATIONAL`
  - `0 < duration_seconds <= threshold` -> `CONNECTED_NON_CONVERSATIONAL`
  - explicit `outcome == "no answer"` or non-connected evidence -> `COMM_NOT_FOUND`
- If no usable local evidence, call existing FUB communication check.

### Fallback mapping (conservative)
- FUB `communicationFound=true` -> `CONNECTED_NON_CONVERSATIONAL`
- FUB false/null -> `COMM_NOT_FOUND`

### Scope constraints
- No DB migration.
- No updates to persisted workflow definitions (`automation_workflows.graph`).
- No change to `wait_and_check_claim`.

## Test Plan

### New/updated step tests
- Local conversational -> `CONVERSATIONAL` and no FUB call.
- Local connected non-conversational -> `CONNECTED_NON_CONVERSATIONAL` and no FUB call.
- Local miss/no-answer -> `COMM_NOT_FOUND`.
- Local insufficient -> fallback path used.
- Fallback true -> `CONNECTED_NON_CONVERSATIONAL`; fallback false/null -> `COMM_NOT_FOUND`.
- Invalid lead id + transient/permanent exceptions keep expected failure contracts.

### Workflow/contract tests
- Update all tests that currently assert `COMM_FOUND`/`COMM_NOT_FOUND` for this step to new 3-code contract.
- Update graph-validator-oriented tests that use this step's declared result codes.
- Update parity/integration tests seeded in code to use new transition keys so test suites stay green.

## Assumptions
- Runtime risk accepted: active DB workflows with old transition keys may fail until manually updated later.
- Development-phase compatibility break for this step's result code contract is acceptable.
- "Conversational-only success" intent is preserved semantically in tests; runtime workflow graph wiring in DB is intentionally out of scope for this pass.
