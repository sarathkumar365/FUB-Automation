# AI Call Java Integration — Research

## Purpose
Track research and execution notes for Java integration with `ai-call-service` contract.

## Source of truth
- `Docs/product-discovery/Call Agent/AI Call Agent - Java Integration Phased Plan.md`
- `/Users/sarathkumar/Projects/2Creative/ai-call-service/docs/CONTRACT.md`
- `/Users/sarathkumar/Projects/2Creative/ai-call-service/main.py`
- `/Users/sarathkumar/Projects/2Creative/ai-call-service/tests/test_phase1_contract.py`

## Key implementation decisions
- Keep `POST /call` and `GET /calls/{sid}` contract unchanged.
- Add engine-managed `RESCHEDULE` instead of step self-mutating DB rows.
- Persist step-local polling state in dedicated `workflow_run_steps.step_state` JSONB.
- Keep `outputs` reserved for terminal business output.
- Poll cadence fixed at `now + 120s`; timeout threshold remains 5 minutes.

## Out of scope (current phase)
- Auth/signing, status callbacks, Python persistence, webhook push, call-service endpoint expansion.
