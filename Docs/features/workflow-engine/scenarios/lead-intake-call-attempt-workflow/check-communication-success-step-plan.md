# Check Communication Success Step — Plan

## Summary
Replace the call-only step plan with one **single workflow step type** that is channel-extensible from day one.
This phase implements the **call channel now**, while preserving a stable workflow-node contract for future channels (`sms`, `email`, etc.).

## Implementation Changes

### 1) Single step contract (UI/API stable)
- New step type: `check_communication_success`
- Config schema (v1):
  - `channel` (required enum): `call` (future: `sms`, `email`)
  - `lookbackMinutes` (required integer, `>= 1`)
- Declared result codes:
  - `SUCCESSFUL`
  - `NOT_SUCCESSFUL`
  - `UNKNOWN`
- Output payload for downstream steps:
  - `channel`
  - `artifactId` (for call: `callId`)
  - `actorUserId` (for call: `callUserId`)
  - `duration` (call channel)
  - `outcome` (call channel)
  - `classificationReason`

### 2) Java architecture (extensible by channel)
- Introduce strategy interface and channel-specific implementations:
  - `CommunicationSuccessChecker`
  - `channel()`
  - `check(leadId, lookbackMinutes, runContext) -> CommunicationCheckResult`
- Add checker registry/factory resolving checker by `channel`.
- Keep `check_communication_success` workflow step as orchestration:
  - validate config
  - resolve checker by channel
  - invoke checker
  - map checker result to `StepExecutionResult` and standard outputs

### 3) Call channel implementation (v1)
- Implement `CallCommunicationSuccessChecker`.
- Decision contract:
  - scope: latest **outbound** call only, within `lookbackMinutes`
  - no outbound call in window -> `UNKNOWN` (`NO_OUTBOUND_CALL`)
  - call exists but `duration` or `outcome` missing -> `UNKNOWN` (`MISSING_CALL_FIELDS`)
  - `outcome == "no answer"` (case-insensitive) -> `NOT_SUCCESSFUL`
  - `duration > rules.call-outcome.short-call-threshold-seconds` -> `SUCCESSFUL`
  - otherwise -> `NOT_SUCCESSFUL`

### 4) FUB integration additions
- Extend FUB client port with one method to fetch latest outbound call for person within lookback window.
- Implement adapter method using calls list endpoint/filtering and map to minimal call-summary DTO.
- Preserve error mapping semantics:
  - transient errors (`429`, `5xx`, network) -> transient step failure
  - permanent errors (`4xx` non-`429`, invalid payload) -> non-transient step failure

## Test Plan

### 1) Step orchestration tests
- Unknown channel -> config/validation failure
- Registry dispatch selects correct checker for `channel=call`
- Checker result maps to expected `StepExecutionResult` code and outputs

### 2) Call checker tests
- No outbound call -> `UNKNOWN`
- Missing fields -> `UNKNOWN`
- No-answer outcome -> `NOT_SUCCESSFUL`
- Connected duration -> `SUCCESSFUL`
- Short/zero duration -> `NOT_SUCCESSFUL`

### 3) FUB adapter tests
- Query/filter correctness for person + outbound + recency + newest selection
- Error classification validation (transient vs permanent)

### 4) Step catalog contract test
- `/admin/workflows/step-types` includes `check_communication_success` with expected schema and result codes

## Assumptions and Defaults
- This remains **one workflow node** in UI.
- `channel=call` is the only enabled channel in this phase.
- Global threshold source remains `rules.call-outcome.short-call-threshold-seconds`.
- Downstream task step can use `actorUserId` output to assign follow-up task to the caller.
