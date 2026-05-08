# AI Call Java Integration — Phases

## Status
- Pass 1: `COMPLETED` (2026-04-21)
- Pass 2: `COMPLETED` (2026-04-21)
- Pass 3: `COMPLETED` (2026-04-21)
- Pass 4: `COMPLETED` (2026-04-21)

## Notes
- Pass 1 introduces workflow-engine primitives only; no ai-call step type yet.
- Pass 2 introduces typed call-service adapter, contract DTOs, and base URL/timeout config.
- Pass 3 adds `ai_call` step execution logic and unit coverage for polling/timeout/failure branches.
- Pass 4 closes integration by validating catalog visibility and end-to-end worker reschedule/terminal flows.

## Post-pass hardening (2026-04-21)

- **Terminal payload mapping**: `AiCallServiceHttpClientAdapter` now deserializes the
  status response straight to `Map<String, Object>`, keeping `status` and `call_sid`
  in `terminalPayload` so downstream JSONata (e.g. `{{ steps.ai1.outputs.call_sid }}`)
  resolves correctly.

## Known follow-ups (tracked, not blocking Phase 4)

- **Absorb single-blip transient `POST /call` failures** (deferred). Phase 3
  currently mandates `NO_RETRY` for place-call; any 5xx or network blip on the
  first dial terminates the run with `AI_CALL_PLACE_FAILED`. A follow-up could
  wire a small bounded retry policy (e.g. 3 attempts, 500→2000ms backoff,
  `retryOnTransient=true`) via the engine's existing retry machinery. Requires
  an explicit spec amendment to Phase 3's "default retry policy: NO_RETRY" line.
  `AiCallWorkflowStep.defaultRetryPolicy` + `startCall` transient branch.

- **Age-bound poll transient reschedule loop** (deferred). `pollCall`'s transient
  `GET /calls/{sid}` branch reschedules unconditionally; a persistently-flaky
  service can hold a run PENDING past `MAX_CALL_AGE` without emitting the
  synthetic timeout. Fix is to route both the in-progress and transient-poll
  branches through a shared `rescheduleOrTimeoutPoll(callSid, callKey, startedAt)`
  helper that enforces the 5m budget in both paths. Requires amending Phase 3's
  "polling transient failures reschedule" line to clarify the age ceiling.
  `AiCallWorkflowStep.pollCall`.

- **Cancel endpoint on Java-side timeout** (review issue #3). When `AiCallWorkflowStep`
  emits the synthetic `timeout` payload at age > 5m, Python keeps the call alive in
  its in-memory registry. Extend the contract with `POST /calls/{sid}/cancel` (or
  `DELETE /calls/{sid}`) and have the step invoke it before returning the timeout
  payload. Touches `ai-call-service/docs/CONTRACT.md`, `AiCallServiceClient`, and
  `AiCallWorkflowStep.pollCall` timeout branch.

- **Poll/timeout math is tight** (review issue #4). Current cadence
  (`POLL_INTERVAL=120s`, `MAX_CALL_AGE=5m`) only yields two real status checks
  before the synthetic timeout fires; calls completing between 4m and 5m get
  misreported as timeouts. Either raise `MAX_CALL_AGE` to 6–7 minutes or shorten
  `POLL_INTERVAL` to 60–90 seconds. `AiCallWorkflowStep` constants.

- **Rename `STEP_STATE_INVALID` → `PLACE_CALL_RESPONSE_INVALID`** (review issue #5).
  The current code is emitted when `POST /call` returns an empty `call_sid`, i.e.
  it is a response-validation failure, not a step-state failure. Misleading to
  operators reading failure codes. `AiCallWorkflowStep`.

- **Make `context` optional in `configSchema`** (review issue #6). Currently the
  schema marks `context` as required, forcing authors to pass `{}` even when they
  have nothing to send. Defaulting to `{}` matches the loose-schema contract on
  the Python side. `AiCallWorkflowStep.configSchema`.
