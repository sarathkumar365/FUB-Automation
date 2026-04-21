# AI Call Agent — Java Integration Phased Plan

Minimal, phased rollout to prove the Java ↔ Python contract end-to-end.
**One new self-rescheduling Java step type** + the smallest Python changes needed
to support it. Everything else from the full V1 plan (durable storage, webhooks,
watchdog, concurrency caps, FUB writes, etc.) stays deferred.

**Goal:** a workflow run can place a real AI call via a single step, and that
step's output contains a structured `CallResult` that downstream steps can
branch on.

## Why one step, not two

The engine's `dueAt` polling loop already supports a step being reinvoked
multiple times — no engine changes needed. Splitting into a separate
"start" and "wait-and-check" (as claims do) only makes sense when the "wait"
half needs to be reusable across multiple origins. AI calls only have one
origin: this step. So we collapse both halves into one step type that
self-reschedules until the call terminates. Downstream steps see a single
clean `CallResult` output — identical ergonomics to any other step.

**Non-goals:**
- No durable storage on Python yet (in-memory dict is fine).
- No webhooks (polling only).
- No watchdog / concurrency cap / retry policy.
- No FUB writes or other side effects.
- No shared DB between Java and Python — HTTP is the only contract.
- No auth / signing between Java and Python (trusted local network assumption).

---

## The contract (agreed before any code)

### Endpoints

| Method | Path              | Purpose                          |
|--------|-------------------|----------------------------------|
| POST   | `/call`           | Place outbound call (idempotent) |
| GET    | `/calls/{sid}`    | Fetch current state / result     |

### `POST /call` request

```json
{
  "call_key": "run_abc:step_123",
  "to": "+15551234567",
  "context": {
    "lead_name": "...",
    "property_interest": "...",
    "agent_name": "...",
    "brokerage_name": "..."
  }
}
```

- `call_key` is Java-generated, deterministic per workflow step invocation.
- If `call_key` already exists → return the existing `{call_sid, status}`
  (no second dial).
- **`context` uses a loose schema.** Python accepts any dict and only pulls
  the keys its prompt template references. Adding a new field = one-line
  prompt template change on Python + JSONata update on Java, no coordinated
  deploy. The contract doc lists the *currently used* fields as guidance,
  not a strict schema. Lock down to a strict schema once the prompt
  stabilizes (post-V1).

### `POST /call` response

```json
{ "call_sid": "CAxxx", "status": "in_progress" }
```

### `GET /calls/{sid}` response

Either in-progress:
```json
{ "call_sid": "CAxxx", "status": "in_progress" }
```

Or terminal (full `CallResult`):
```json
{
  "schema_version": "1",
  "call_sid": "CAxxx",
  "call_key": "run_abc:step_123",
  "status": "completed | failed | no_answer | voicemail | busy | timeout",
  "started_at": "...",
  "ended_at": "...",
  "duration_seconds": 90,
  "ended_by": "ai | user | timeout | error",
  "connection": { "answered": true, "is_voicemail": false, "twilio_status": "completed" },
  "conversation": {
    "identity_confirmed": true,
    "interested": "yes | no | maybe | unknown",
    "timeline": "...",
    "budget": "...",
    "callback_requested": true,
    "callback_time_preference": "...",
    "notes": "..."
  },
  "transcript": [ { "role": "assistant|user", "ts": "...", "text": "..." } ],
  "recording_url": null,
  "ai_meta": {
    "model": "gpt-4o-realtime-preview",
    "hit_guardrails": false,
    "tokens_in": 0,
    "tokens_out": 0,
    "estimated_cost_usd": 0.0
  },
  "error": null
}
```

Java treats `status` as the primary routing field.

---

## Phase 0 — Pin the contract

**What:** write the contract above into a shared markdown doc in the Python repo
(`ai-call-service/docs/CONTRACT.md`). Both sides read from it.

**Exit criteria:**
- Contract doc committed on Python side.
- Java and Python both agree on field names, enum values, idempotency semantics.

**Out of scope:** OpenAPI, protobuf, codegen. Markdown is enough.

---

## Phase 1 — Python: state + idempotency + polling endpoint

**What:**
1. In-memory call registry: `dict[call_key → CallState]` and `dict[call_sid → call_key]`.
2. `POST /call` updated:
   - Accept `call_key` + `context` in body (context replaces hardcoded `SYSTEM_PROMPT`).
   - On duplicate `call_key`, return existing state — do not redial.
   - Render instructions from `context` (simple f-string / template for now).
3. `GET /calls/{sid}` endpoint returning current state.
4. `/media` handler writes state transitions into the registry
   (`in_progress → completed` on clean close; populate `CallResult` on done).
5. Basic outcome extraction: after call ends, run a single OpenAI chat completion
   on the transcript to fill `conversation.*` fields. Stubbed structure if extraction
   fails — don't block the `CallResult`.

**Exit criteria:**
- `POST /call` twice with the same `call_key` only dials once.
- `GET /calls/{sid}` returns in-progress while call is live, full `CallResult` after.
- Context from the POST body actually shows up in the AI's greeting (use lead name).
- Curl-only test passes end-to-end (no Java involved yet).

**Out of scope:** persistence across restarts, TTL/cleanup, auth.

---

## Phase 2 — Java: `AiCallWorkflowStep` (self-rescheduling)

**Summary:**
Implement Phase 2 by adding an engine-level `RESCHEDULE` outcome and a new
`ai_call` workflow step that integrates with the finalized Python contract
(`POST /call`, `GET /calls/{sid}`), while preserving terminal `CallResult`
semantics (`schema_version="1"`), idempotency, and existing workflow APIs.
Polling cadence is fixed to `nextDueAt = now + 120s`.

**Implementation changes:**
1. Engine reschedule capability:
   - Extend step execution result model to support a non-terminal reschedule
     action carrying:
     - `nextDueAt`
     - optional state patch
   - Update execution flow so `RESCHEDULE`:
     - updates the same step row to `PENDING`
     - sets `dueAt = nextDueAt`
     - persists state patch
     - skips transition application until terminal completion

2. Step-local state persistence (new DB column):
   - Add Flyway migration to `workflow_run_steps` for a dedicated JSON column
     (e.g., `step_state`).
   - Map this field in `WorkflowRunStepEntity`.
   - Keep `outputs` strictly for terminal business output, not in-progress
     scratch state.

3. New call-service integration step:
   - Add `AiCallWorkflowStep` with config schema:
     - required `to` (resolved string)
     - required `context` (resolved object; loose keys)
   - Step runtime behavior:
     - First invocation (no `callSid` in `step_state`):
       - build deterministic `call_key = runId:stepId`
       - call `POST /call`
       - store `{callSid, startedAt}` in `step_state`
       - return `RESCHEDULE(now + 120s)`
     - Subsequent invocation:
       - call `GET /calls/{callSid}`
       - `in_progress` and elapsed <= 5 minutes: `RESCHEDULE(now + 120s)`
       - elapsed > 5 minutes: complete with timeout terminal payload
         (`status="timeout"`, `error.code/message`)
       - terminal response from Python: complete and write full payload to
         `outputs`
   - Failure behavior:
     - first-invocation transport/5xx -> terminal fail-fast
     - polling transport transient failures -> reschedule

4. Dedicated call-service adapter:
   - Add a typed port/adapter + DTOs for call-service contract shapes.
   - Add environment-backed config for base URL and HTTP timeouts.
   - Do not add new HTTP endpoints.

5. Catalog and operator visibility:
   - Register `ai_call` so it appears in `/admin/workflows/step-types` with
     correct schema/result codes.
   - Keep run detail behavior unchanged except new internal state persistence
     path.

**Delivery passes:**
1. Pass 1: engine reschedule primitive + `step_state` migration + core execution tests.
2. Pass 2: call-service adapter/DTO/config + adapter tests.
3. Pass 3: `AiCallWorkflowStep` logic + unit tests (first call, poll, terminal, timeout).
4. Pass 4: integration wiring (catalog + worker reschedule loop) + regression suites.

**Pass tracking:**
- Pass 1: `COMPLETED` (2026-04-21)
  - Added engine `RESCHEDULE` outcome handling in `WorkflowStepExecutionService`.
  - Added `workflow_run_steps.step_state` (Flyway `V16__add_step_state_to_workflow_run_steps.sql` + entity mapping).
  - Added test coverage for reschedule behavior and step-state persistence.
- Pass 2: `COMPLETED` (2026-04-21)
  - Added typed call-service port (`AiCallServiceClient`) and HTTP adapter (`AiCallServiceHttpClientAdapter`).
  - Added call-service DTOs for `POST /call` and `GET /calls/{sid}` payload mapping.
  - Added `ai-call-service.*` config properties for base URL and timeouts.
  - Added adapter test coverage for success, terminal/in-progress mapping, HTTP error mapping, and network failure behavior.
- Pass 3: `COMPLETED` (2026-04-21)
  - Added `AiCallWorkflowStep` (`id=ai_call`) with required `to` + `context` config schema.
  - Added runtime polling semantics: initial `POST /call`, persisted `step_state` (`callSid`, `callKey`, `startedAt`), fixed `+120s` self-reschedule loop.
  - Added terminal handling for service terminal statuses (`completed` / `failed`) plus caller-side timeout path (`timeout`) with synthetic terminal payload (`schema_version="1"`).
  - Added explicit failure behavior: first-call fail-fast on client exception, transient poll failure reschedule, non-transient poll failure terminal fail.
  - Extended `StepExecutionContext` + execution wiring to pass persisted `step_state` into step executors.
  - Added unit coverage: `AiCallWorkflowStepTest` and `WorkflowRetryDispatchTest` context propagation assertion.
- Pass 4: `COMPLETED` (2026-04-21)
  - Extended admin step catalog assertions to include `ai_call` metadata (`required` keys, declared result codes, retry defaults).
  - Added integration test coverage (`AiCallWorkflowIntegrationTest`) validating worker-loop behavior:
    - same-step reschedule execution (`+120s` cadence)
    - deterministic `call_key = runId:stepId`
    - persisted `step_state` continuity across polls
    - terminal payload transition and downstream step expression consumption
    - timeout and non-transient poll-failure paths
  - Ran full backend regression suite with Docker/Testcontainers enabled and confirmed green.

**Test plan:**
- Unit: reschedule handling updates step row (`PENDING`, `dueAt`, `step_state`)
  without transition.
- Unit: `AiCallWorkflowStep` payload mapping, idempotent key usage, 120s cadence,
  timeout behavior.
- Integration: due-worker reclaims same step after due time and eventually
  completes with terminal output.
- API: `/admin/workflows/step-types` includes `ai_call`.
- Regression: run new tests plus existing backend suite; report blockers if
  environment-dependent.

**Assumptions and defaults:**
- Poll interval for `ai_call` is always 120 seconds.
- Timeout threshold is 5 minutes from persisted `startedAt`.
- Scope excludes auth, status-callback/webhook push, and Python-side persistence changes.
- Documentation workflow is mandatory:
  - repo-wide decisions in `Docs/repo-decisions/`
  - feature docs in `Docs/features/<feature-slug>/` updated per pass.

**Exit criteria:**
- Step runs in a workflow, phone actually rings on first invocation.
- Step self-reschedules while call is live (visible in logs as repeated
  invocations with the same `call_sid`).
- Step completes with full `CallResult` as output when call ends.
- Downstream steps can read `steps.ai_call.output.status`,
  `steps.ai_call.output.conversation.interested`, etc. via JSONata.
- Retrying the step (same `runId:stepId`) does NOT dial twice.
- Timeout path works if call never terminates (> 5 min in `in_progress`).

**Out of scope:** exponential backoff on polls, circuit breaker, metrics,
per-call-age poll tuning.

---

## Phase 3 — End-to-end workflow test

**What:**
1. Compose a minimal test workflow: `ai_call` → `log_step` (or equivalent).
2. Kick it off with real lead context (name, property, agent).
3. Answer the phone, have a short conversation.
4. Verify:
   - Phone rings with personalized greeting (context flowed Java → Python → AI).
   - Step self-reschedules during the call (check logs).
   - Final step sees structured `CallResult` in its input via JSONata.

**Exit criteria:**
- Full round trip works at least twice in a row (proves idempotency + cleanup).
- Transcript and outcome fields visible in Java-side logs.
- Downstream step reads `steps.ai_call.output.conversation.interested`
  correctly.

**Out of scope:** load test, real FUB lead data, production workflow wiring.

---

## Deferred (explicitly not in this plan)

Everything below belongs to the full V1 plan, not this integration spike:

- Durable state on Python (Postgres with `ai_call` schema, Alembic migrations).
- Webhook push from Python → Java as a latency optimization.
- Watchdog, concurrency cap, retry/backoff policy.
- `end_call` tool for AI-initiated termination.
- Layered prompt (identity/goal/guardrails/style split).
- Barge-in handling, voicemail detection refinement.
- Auth between Java and Python (HMAC / mTLS).
- Observability: metrics, tracing, cost tracking dashboards.
- Shared-instance Postgres setup (separate schemas per service).
- Production workflow templates, real FUB integration.

---

## Success definition

After Phase 4, we've proven:
1. Java can invoke a Python service as a workflow step.
2. The two-step async pattern (start + wait-and-check) works for phone calls
   the same way it works for claims.
3. Context flows cleanly Java → Python → AI.
4. Structured outcomes flow cleanly Python → Java → downstream steps.
5. Idempotency protects against double-dialing on retry.

That's enough architectural validation to justify building out the rest of V1.
