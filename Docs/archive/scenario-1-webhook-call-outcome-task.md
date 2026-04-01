# Scenario 1: Call Outcome -> Auto Follow-up Task (MEP)

## Objective
Automatically create Follow Up Boss tasks from call outcomes with reliable idempotency, retry handling, and secure webhook verification.

## Scope
- Primary trigger mode: webhook-first.
- Event types considered: `callsCreated` (required), `callsUpdated` (optional), `callsDeleted` (ignore safely).
- Decision output: `TASK_CREATED`, `SKIPPED`, or `FAILED`.

## Why webhook-first
- Lower latency than polling.
- Better operator experience (near-real-time task creation).
- Keep a lightweight reconciliation poller later for missed webhook recovery.

## Core implementation invariants
1. Webhook endpoint acknowledges quickly (`2xx/202`) and does not do heavy work inline.
2. Processing is asynchronous and idempotent.
3. One call should never create duplicate tasks.
4. All decision branches are persisted and observable.
5. Secrets are never logged.

## End-to-end flow
1. Register webhook via FUB (`callsCreated` initially).
2. FUB sends event to `POST /webhooks/fub`.
3. Verify signature from raw request body.
4. Parse event type and `resourceIds` (array of call ids).
5. Persist/queue work item.
6. Worker loads full call via `GET /v1/calls/{id}` for each resource id.
7. Rule engine classifies call outcome.
8. If actionable, create task via `POST /v1/tasks`.
9. Persist final status (`TASK_CREATED` / `SKIPPED` / `FAILED`).

## Rule engine (MEP v1)
Rule precedence:
1. Missing/unknown `personId` (`null` or `0`) -> `SKIPPED` (cannot create task).
2. `duration == null` -> `SKIPPED` (until fallback field is confirmed).
3. `duration == 0` -> `MISSED`.
4. `0 < duration <= SHORT_CALL_THRESHOLD_SECONDS` -> `SHORT`.
5. `duration > SHORT_CALL_THRESHOLD_SECONDS` -> `CONNECTED`.

Actions:
1. `MISSED`
- Task text: `Call back - previous attempt not answered`

2. `SHORT`
- Task text: `Follow up - previous call was very short`

3. `CONNECTED`
- Task text: `Follow up - connected call completed`

Recommendation:
- Default `SHORT_CALL_THRESHOLD_SECONDS=30`.

## APIs used
- Webhooks:
  - `POST /v1/webhooks`
  - `GET /v1/webhooks`
- Calls:
  - `GET /v1/calls/{id}`
- Tasks:
  - `POST /v1/tasks`

## Data model (recommended)
`processed_calls`
- `id` (PK)
- `call_id` (unique)
- `status` (`RECEIVED`, `PROCESSING`, `SKIPPED`, `TASK_CREATED`, `FAILED`)
- `rule_applied` (nullable)
- `task_id` (nullable)
- `failure_reason` (nullable)
- `retry_count` (default 0)
- `created_at`
- `updated_at`
- `raw_payload` (jsonb, optional)

## Reliability and retry policy
1. Signature verification failure:
- Reject request (`401/403`), do not enqueue.

2. `GET /calls/{id}` transient failure:
- Retry with exponential backoff + jitter.

3. `POST /tasks` transient failure (`429/5xx`):
- Retry with exponential backoff + jitter.

4. `POST /tasks` permanent failure (`4xx` validation):
- Mark `FAILED` with reason, no infinite retry.

## Security requirements
1. Verify `FUB-Signature` using configured signing key.
2. Use constant-time signature comparison.
3. Optional replay protection (timestamp window) if provided by FUB header set.
4. Never log API keys, signing keys, or raw authorization headers.

## Observability requirements
Structured logs (minimum fields):
- `callId`
- `eventType`
- `personId`
- `duration`
- `ruleMatched`
- `status`
- `taskId`

Metrics (phase 1.5+):
- `webhook_received_total`
- `webhook_signature_failed_total`
- `calls_processed_total`
- `tasks_created_total`
- `calls_skipped_total`
- `processing_failed_total`

## Service endpoints (planned)
- `POST /webhooks/fub`
  - Inbound webhook receiver + signature verification + enqueue
- `GET /admin/last-processed`
  - Latest processed call metadata
- `POST /admin/reprocess/{callId}` (optional)
  - Manual replay for support/debug

## Configuration
- `FUB_API_KEY`
- `FUB_BASE_URL`
- `FUB_X_SYSTEM` (integration identification header)
- `FUB_X_SYSTEM_KEY` (required for webhook signature verification and system-level operations)
- `SHORT_CALL_THRESHOLD_SECONDS`
- `POLL_INTERVAL_MS` (for optional reconciliation poller)

## Open decisions to lock
1. Confirm `SHORT_CALL_THRESHOLD_SECONDS` final value (`30` recommended).
2. Assignment strategy: `call.userId` first, fallback user when null.
3. Whether to process `callsUpdated` in MEP or defer.
4. Whether to create inbound/outbound-specific task templates in MEP.

## Test matrix (required baseline)
1. Valid signature + missed call -> one task created.
2. Duplicate webhook same `callId` -> no duplicate task.
3. Short call -> short follow-up task template used.
4. Connected call -> connected follow-up task template used.
5. Missing/unknown `personId` (`null` or `0`) -> skipped with persisted reason.
6. Task API `429` then success -> retry path succeeds.
7. Task API `400` -> marked failed without endless retry.
8. Invalid signature -> request rejected and not processed.
9. `callsDeleted` -> ignored safely.
10. Webhook payload with multiple `resourceIds` -> each call id processed independently.
11. Webhook payload with `uri: null` -> event still processed using `resourceIds`.

## Incremental implementation backlog
1. Add migration for `processed_calls` statusful schema.
2. Add webhook controller with raw-body signature verification.
3. Add async queue/worker boundary.
4. Add FUB client methods (`getCallById`, `createTask`) with retry wrapper.
5. Add rule engine + task mapper.
6. Add admin endpoints (`last-processed`, optional `reprocess`).
7. Add UI-focused admin APIs to expose webhook inbox activity and processed call outcomes in one operator view.
8. Add integration tests and run full suite.

## Implementation (Six Actionable Steps)
1. Webhook foundation ✅ Completed
- Build `POST /webhooks/fub` endpoint with raw-body signature verification and fast `202` acknowledgment.
- Persist inbound webhook metadata (`eventId`, `event`, `resourceIds[]`, nullable `uri`, payload hash, received timestamp).
- Delivered:
  - Modular webhook ingress endpoint and service flow.
  - Signature verification, payload normalization, and inbox persistence (`webhook_events`).
  - Separate webhook exception package.
  - Placeholder dispatcher for Step 1 (`NoopWebhookDispatcher`).
  - Test coverage for controller, parser, verifier, repository, and ingress integration.
  - Latest signature implementation aligned to FUB docs: HMAC over base64-encoded JSON payload.
- Validation:
  - Full test suite executed successfully (`./mvnw clean test`).

2. Follow Up Boss client and auth layer ✅ Completed
- Implement client methods for `registerWebhook`, `getCallById`, and `createTask`.
- Use Basic Auth (`API_KEY:`) and include required integration headers (`X-System`, `X-System-Key`).
- Delivered:
  - Added service port `FollowUpBossClient` and FUB adapter implementation.
  - Implemented real HTTP methods for `getCallById` and `createTask`.
  - Implemented mocked/stubbed `registerWebhook` method for Step 2 boundary completeness.
  - Added outbound FUB config properties and centralized auth/header wiring.
  - Added FUB DTOs and typed exceptions (`FubTransientException`, `FubPermanentException`) for retry-ready error classification.
  - Added adapter test coverage for auth headers, success mappings, error mappings, and stub behavior.
- Validation:
  - Full test suite executed successfully (`./mvnw clean test`).

3. Idempotent processing pipeline ✅ Completed
- Process webhook events asynchronously through a worker/executor boundary.
- Enforce deduplication by `call_id` (and webhook `eventId` where available) using `processed_calls`.
- Track status transitions in `processed_calls` and persist call-level outcomes.
- Delivered:
  - Replaced noop dispatch with async dispatcher + task executor boundary.
  - Added `processed_calls` persistence with per-call tracking and idempotent reprocessing behavior.
  - Implemented event processing per `resourceIds` call id.
  - `callsCreated` now runs Step 4 decision engine and either creates task, skips, or fails with explicit reason codes.
  - `callsUpdated` and `callsDeleted` are captured and marked failed with message: `EVENT_TYPE_NOT_SUPPORTED_IN_STEP3`.
  - Ingress `POST /webhooks/fub` continues to return `202` with message-based acknowledgment.
- Validation:
  - Runtime verification completed using real webhook traffic and DB rows in `processed_calls`.
- Pending (acceptance checks to close Step 3 fully):
  - Confirm duplicate webhook deliveries for the same `call_id` do not trigger duplicate processing side effects.
  - Confirm multi-`resourceIds` payloads produce independent `processed_calls` rows for each call id.
  - Confirm ingress response body messaging for unsupported intake event types matches expected operator text.

4. Rule engine and task mapping ✅ Completed
- Implement outcome rules:
  - Missing/invalid `userId` (`null` or `0`) -> `SKIPPED` (`MISSING_ASSIGNEE`)
  - `outcome == No Answer` -> callback task (`OUTCOME_NO_ANSWER`) regardless of duration
  - `duration == null` and unmapped `outcome` -> `FAILED` (`UNMAPPED_OUTCOME_WITHOUT_DURATION`)
  - `duration == 0` -> callback task
  - `0 < duration <= SHORT_CALL_THRESHOLD_SECONDS` -> short-call callback task
  - `duration > SHORT_CALL_THRESHOLD_SECONDS` -> `SKIPPED` (`CONNECTED_NO_FOLLOWUP`)
- Map actionable outcomes to a task payload:
  - task due tomorrow (`dueDate = today + 1`, `dueDateTime = null`)
  - assign to `call.userId`
  - unknown `personId` (`null`/`0`) uses generic task path (send `personId=null`, let FUB accept/reject)

### Step 4 Reference: `GET /v1/calls/{id}` field map
Use this map as the canonical reference for rule evaluation and task assignment.

| Field | Meaning | Step 4 usage | Confidence |
|---|---|---|---|
| `id` | Call log id in FUB | Traceability, logging, persistence key input | Confirmed |
| `personId` | Matched FUB person id; `0` means unknown/unmatched caller | Not a skip condition in Step 4; use generic task path (`personId=null`) | Confirmed |
| `duration` | Call duration in seconds | Core classifier (`MISSED`/`SHORT`/`CONNECTED`) | Confirmed |
| `userId` | FUB user/agent associated with the call log | Primary `assignedUserId` for task creation | Confirmed |
| `outcome` | Provider call outcome label (for example `No Answer`) | Outcome-first signal for callback creation (`No Answer`) | Confirmed |
| `isIncoming` | Whether call direction is inbound | Optional future template variant | Confirmed |
| `name` / `firstName` / `lastName` | Contact display fields when person is known | Optional in logs/observability only | Confirmed |
| `phone` | Primary number represented on call log | Observability/debug only in Step 4 | Confirmed |
| `fromNumber` | Originating number | Observability/debug only in Step 4 | Confirmed |
| `toNumber` | Destination/system number that received the call | Observability/debug only in Step 4 | Confirmed |
| `recordingUrl` | Recording location when available | Not used in Step 4; reserved for Scenario 2 | Confirmed |
| `created` / `updated` / `startedAt` | Call timestamps | Optional future due-date/time logic | Confirmed (`startedAt` may be null) |
| `userName` | Display name for `userId` | Logging/debug only | Confirmed |
| `createdById` / `updatedById` | Actor ids that created/updated log (often system values) | Not used in Step 4 | Inferred usage |
| `relationshipId` | Relationship/link id for call record | Not used in Step 4 | Inferred usage |
| `sharedInboxId` | Shared inbox context id (`0` often none) | Not used in Step 4 | Inferred usage |
| `conferenceCallId` | Conference call grouping id when present | Not used in Step 4 | Inferred usage |
| `systemId` | External/system linkage id when present | Not used in Step 4 | Inferred usage |
| `ringDuration` | Ringing duration when provided | Optional future rule signal; not used in Step 4 | Inferred usage |
| `forwardNumber` | Forward target number when forwarding occurred | Not used in Step 4 | Inferred usage |
| `note` | Call note text | Not used in Step 4 | Confirmed |

Rule precedence for Step 4:
1. `userId == null || userId == 0` -> `SKIPPED` (`MISSING_ASSIGNEE`)
2. `outcome == "No Answer"` -> `CREATE_TASK` (`OUTCOME_NO_ANSWER`)
3. `duration == null` and outcome unmapped -> `FAILED` (`UNMAPPED_OUTCOME_WITHOUT_DURATION`)
4. `duration > SHORT_CALL_THRESHOLD_SECONDS` -> `SKIPPED` (`CONNECTED_NO_FOLLOWUP`)
5. `duration == 0` -> `CREATE_TASK` (`MISSED`)
6. `0 < duration <= SHORT_CALL_THRESHOLD_SECONDS` -> `CREATE_TASK` (`SHORT`)

5. Reliability, visibility, and validation ✅ Completed
- Add retry/backoff for transient FUB errors (`429`, `5xx`) and terminal handling for permanent `4xx`.
- Assume webhook retries/duplicates can occur and keep processing idempotent.
- Add admin visibility endpoint (`GET /admin/last-processed`) and optional replay endpoint.
- Add tests for signature checks, idempotency, rule routing, retry behavior, and failure handling; execute full suite.

6. UI API layer for operations dashboard (planned next)
- Extend admin API surface so the UI can render both:
  - processed calls (current outcomes/replay controls), and
  - incoming webhook stream (what arrived, when, and processing state).
- Expand the current admin controller design to include webhook-focused endpoints (or a dedicated admin webhook controller behind the same `/admin` boundary), with filter/paging support for:
  - `source`, `eventType`, `receivedAt` range, and processing status.
- Return list payloads that are UI-ready for a “webhooks coming in” page:
  - `eventId`, `source`, `eventType`, `resourceIds`, `receivedAt`, and ingestion/processing status summary.
- Keep layered boundaries intact:
  - controller -> service -> repository,
  - no provider HTTP logic in admin controller/service,
  - map persistence entities to explicit response DTOs for UI contracts.

## Webhook payload contract notes (FUB)
- `resourceIds` is an array and may include multiple ids in one event.
- `uri` may be null; processing should not depend on it.
- Use `resourceIds` as the primary source for call lookup.
