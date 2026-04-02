# Phase 4 Implementation Log

Status: Not started

## Scope
- Execute assignment SLA due checks prepared in Phase 3:
  - execute step `WAIT_AND_CHECK_CLAIM`
  - execute step `WAIT_AND_CHECK_COMMUNICATION` when claim step passes
- Apply policy-driven action when SLA unmet:
  - execute step `ON_FAILURE_EXECUTE_ACTION` with configured action:
    - reassign lead, or
    - move lead to pond
- Persist outcomes and failure reasons with replay-safe behavior

## Changes
-

## Validation
-

## Notes for Next Agent
- This phase is where business decisions and adapter actions run.
- Ensure idempotent action execution and explicit observability for each decision point.
- Keep step execution deterministic and sequential according to policy snapshot order.
- Worker input source must be persisted Phase 3 runtime records (`policy_execution_step` pending rows with due times), not policy definition rows.
