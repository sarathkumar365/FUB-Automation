# Scenario 1: Call Outcome -> Auto Follow-up Task (MEP)

## Objective
Automatically create a Follow Up Boss task when a call outcome indicates follow-up is needed (missed, short, disconnected, or connected depending on rule).

## Why webhook-first
Use Follow Up Boss webhooks so call events are pushed to us in near real time instead of relying only on polling.

Supported call webhook events from FUB docs:
- `callsCreated`
- `callsUpdated`
- `callsDeleted`

## High-level flow
1. Register webhook in FUB for `callsCreated` (and optionally `callsUpdated`).
2. FUB sends event to our webhook endpoint.
3. Verify webhook signature.
4. Extract `callId` from payload.
5. Fetch full call details from FUB `/v1/calls/{id}` if payload is partial.
6. Evaluate call outcome rules.
7. If rule matches, create task via `/v1/tasks`.
8. Store processed call id in DB (idempotency).
9. Return HTTP 2xx quickly to FUB.

## Initial rules for MEP
1. Missed/Not connected:
- Condition: `duration == 0`
- Action: Create callback task
- Example text: `Call back - previous attempt not answered`

2. Short/disconnected:
- Condition: `duration > 0 && duration <= SHORT_CALL_THRESHOLD_SECONDS` (suggest 15 or 30)
- Action: Create follow-up task
- Example text: `Follow up - previous call was very short`

3. Connected:
- Condition: `duration > SHORT_CALL_THRESHOLD_SECONDS`
- Action: Create follow-up task
- Example text: `Follow up - connected call completed`

## APIs used
- Webhooks:
  - `POST /v1/webhooks` (registration)
  - `GET /v1/webhooks` (validation)
- Calls:
  - `GET /v1/calls/{id}`
- Tasks:
  - `POST /v1/tasks`

## Required technical guardrails
1. Idempotency:
- DB table `processed_calls` with unique `call_id`
- If `call_id` already processed, skip

2. Security:
- Verify `FUB-Signature` using configured `X-System-Key`
- Reject invalid signatures

3. Reliability:
- Acknowledge webhook quickly (2xx)
- Process asynchronously (queue/executor)
- Retry task creation on transient failures (429/5xx)

4. Observability:
- Log `callId`, `personId`, `duration`, `ruleMatched`, `taskCreated`
- Do not log API keys or secrets

## Endpoint design in our service (planned)
- `POST /webhooks/fub`
  - Receives FUB webhook payloads
  - Verifies signature
  - Enqueues processing
- `GET /admin/last-processed`
  - Returns latest processed call metadata
- `POST /admin/reprocess/{callId}` (optional)
  - Manual retry hook for support

## Configuration to add
- `FUB_API_KEY`
- `FUB_BASE_URL`
- `FUB_X_SYSTEM`
- `FUB_X_SYSTEM_KEY`
- `SHORT_CALL_THRESHOLD_SECONDS`
- `WEBHOOK_SECRET` (if separated)

## Open decisions before coding
1. Should short-call threshold be 15s or 30s?
2. Do we assign task to `call.userId` always, or fallback to default user?
3. Should `callsUpdated` also trigger re-evaluation?
4. Do we create different task templates for inbound vs outbound?

## Immediate implementation backlog
1. Add DB schema (`processed_calls`).
2. Add webhook controller + signature verification.
3. Add FUB client methods (`getCallById`, `createTask`).
4. Add rule engine and task mapper.
5. Add async processing + retries.
6. Add integration tests with mocked FUB responses.
