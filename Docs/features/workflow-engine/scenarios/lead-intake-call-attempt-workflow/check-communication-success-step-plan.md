# Check Communication Success Step — Plan

## Summary
Add a **new workflow step type** `check_communication_success` (no replacement of existing steps).
The step remains a single workflow node in UI, and supports channel-based internal orchestration.
This phase implements `channel=call` only, while keeping the design extensible for future channels.

## Implementation Changes

### 1) New step contract (add-only)
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

### 2) Java architecture (single node, internal orchestration)
- Keep one workflow step class for `check_communication_success`.
- Internally, use channel strategy orchestration:
  - `CommunicationSuccessChecker`
  - `channel()`
  - `check(leadId, lookbackMinutes, runContext) -> CommunicationCheckResult`
- Add registry/factory resolving checker by `channel`.
- `check_communication_success` step responsibilities:
  - validate config
  - resolve checker by channel
  - invoke checker
  - map to `StepExecutionResult` and standard outputs

### 3) Reuse-first rule source (from existing call processor path)
Reuse current logic/contracts already in platform:
- Decision rules from `CallDecisionEngine` (including no-answer + duration-based logic)
- Threshold from `rules.call-outcome.short-call-threshold-seconds`
- Retry/error handling pattern from `WebhookEventProcessorService.executeWithRetry(...)` and FUB transient/permanent mapping

Only net-new decision mapping for this step:
- no outbound call in window -> `UNKNOWN` (`NO_OUTBOUND_CALL`)
- call exists but `duration` or `outcome` missing -> `UNKNOWN` (`MISSING_CALL_FIELDS`)
- otherwise apply reused call success logic and map to `SUCCESSFUL` / `NOT_SUCCESSFUL`

### 4) New data lookup needed (latest outbound call)
This is the only core capability not already in call-processor flow:
- Add FUB client port method to fetch latest outbound call for a person within `lookbackMinutes`
- Implement in FUB adapter using calls endpoint filtering/sorting
- Return minimal call summary DTO used by the new step
- Preserve existing error mapping semantics:
  - transient (`429`, `5xx`, network) -> transient step failure
  - permanent (`4xx` non-`429`, invalid payload) -> non-transient step failure

## Test Plan

### 1) Step orchestration tests
- Unknown channel -> validation failure
- Registry dispatch resolves `channel=call`
- Checker result maps to expected result code and outputs

### 2) Call checker tests
- No outbound call -> `UNKNOWN`
- Missing fields -> `UNKNOWN`
- No-answer outcome -> `NOT_SUCCESSFUL`
- Connected duration -> `SUCCESSFUL`
- Short/zero duration -> `NOT_SUCCESSFUL`

### 3) FUB adapter tests
- Latest-outbound lookup query/filter correctness (person + outbound + recency + newest)
- Transient vs permanent error mapping behavior

### 4) Step catalog contract test
- `/admin/workflows/step-types` includes `check_communication_success` with expected schema/result codes

## Assumptions and Defaults
- This is an add-only step; existing steps remain unchanged.
- `channel=call` is the only enabled channel in this phase.
- Global threshold source remains `rules.call-outcome.short-call-threshold-seconds`.
- Downstream task step can use `actorUserId` output to assign follow-up task to the caller.
