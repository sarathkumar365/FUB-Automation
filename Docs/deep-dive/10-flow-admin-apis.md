# Flow F: Admin Observability and Operations APIs

## API surface summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/webhooks` | List webhook events (paginated) |
| `GET` | `/admin/webhooks/{id}` | Webhook event detail |
| `GET` | `/admin/webhooks/stream` | SSE live feed |
| `GET` | `/admin/processed-calls` | List processed calls |
| `POST` | `/admin/processed-calls/{callId}/replay` | Replay a failed call |
| `GET` | `/health` | Health check |

## Webhook feed API

**`GET /admin/webhooks`**

Query parameters:

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | `WebhookSource` | no | Filter by source (`FUB`) |
| `status` | `WebhookEventStatus` | no | Filter by status |
| `eventType` | `String` | no | Filter by event type (exact match, trimmed) |
| `from` | `OffsetDateTime` | no | Inclusive start time |
| `to` | `OffsetDateTime` | no | Inclusive end time |
| `limit` | `Integer` | no | Page size (default: 50, max: 200) |
| `cursor` | `String` | no | Pagination cursor from previous response |
| `includePayload` | `boolean` | no | Include full payload in items (default: false) |

**Validation:** `from` must be ≤ `to` or throws `InvalidWebhookFeedQueryException` → 400.

**Response:** `WebhookFeedPageResponse`
```json
{
  "items": [
    {
      "id": 1,
      "eventId": "abc-123",
      "source": "FUB",
      "eventType": "callsCreated",
      "catalogState": "SUPPORTED",
      "normalizedDomain": "CALL",
      "normalizedAction": "CREATED",
      "status": "RECEIVED",
      "receivedAt": "2026-04-08T10:00:00Z",
      "payload": null
    }
  ],
  "nextCursor": "eyJyZWNlaXZlZEF0Ijoi...",
  "serverTime": "2026-04-08T10:05:00Z"
}
```

**Pagination implementation:** Keyset/cursor-based pagination using `(receivedAt, id)` as the bookmark. The cursor is a Base64-encoded JSON `{"receivedAt":"<ISO8601>","id":<number>}`. The query fetches `limit + 1` rows to detect if more pages exist. The SQL uses:
```sql
WHERE (received_at < :cursorReceivedAt
   OR (received_at = :cursorReceivedAt AND id < :cursorId))
ORDER BY received_at DESC, id DESC
```

**Why keyset pagination:** Offset/limit pagination breaks with concurrent inserts (rows shift, causing duplicates or missed items). Keyset pagination is stable regardless of concurrent writes.

**`GET /admin/webhooks/{id}`** → Returns `WebhookEventDetailResponse` with all fields including `payloadHash` and full `payload`. Returns 404 if not found.

## Webhook SSE live feed

**`GET /admin/webhooks/stream`** (produces `text/event-stream`)

Query parameters: `source`, `status`, `eventType` (all optional filters).

**Implementation:** `WebhookSseHub`

- Creates an `SseEmitter` with configurable timeout (default 30 minutes)
- Assigns unique subscriber ID (atomic sequence)
- Registers completion/timeout/error callbacks to clean up subscribers
- **Event types:**
  - `webhook.received` — published when a new webhook is persisted. Payload: `{id, eventId, source, eventType, status, receivedAt}`
  - `heartbeat` — sent every 15 seconds (configurable) to keep connection alive. Payload: `{serverTime: <ISO8601>}`
- **Filter matching:** Each subscriber has optional `source`, `status`, `eventType` filters. An event matches if all non-null filters match. Null filter = match all.
- **Error handling:** `IOException` or `IllegalStateException` during send → remove subscriber, complete emitter with error
- **Known issue:** `Map.of()` used for payload construction does not allow null values — `eventId` can be null, causing `NullPointerException`. TODO to replace with null-tolerant builder.

## Processed calls API

**`GET /admin/processed-calls`**

Query parameters:

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `ProcessedCallStatus` | no | Filter by status |
| `from` | `OffsetDateTime` | no | Start time filter |
| `to` | `OffsetDateTime` | no | End time filter |
| `limit` | `Integer` | no | Page size (default: 50, max: 200) |

**Response:** `List<ProcessedCallSummaryResponse>`
```json
[
  {
    "callId": 12345,
    "status": "TASK_CREATED",
    "ruleApplied": "MISSED",
    "taskId": 67890,
    "failureReason": null,
    "retryCount": 0,
    "updatedAt": "2026-04-08T10:00:00Z"
  }
]
```

Uses JPA `Specification` for dynamic filtering, sorted by `updatedAt DESC`.

## Processed call replay

**`POST /admin/processed-calls/{callId}/replay`**

**Behavior:**
1. Look up `ProcessedCallEntity` by `callId` — if not found → 404
2. Check status is `FAILED` — if not → 409 CONFLICT ("Only FAILED calls can be replayed")
3. Reset entity:
   - `status = RECEIVED`
   - `failureReason = null`
   - `ruleApplied = null`
   - `taskId = null`
   - `updatedAt = now()`
   - **Note:** `retryCount` is NOT reset (TODO in code)
4. Build synthetic `NormalizedWebhookEvent`:
   - `source = FUB`, `domain = CALL`, `action = CREATED`
   - `eventId = "replay-{callId}-{timestamp}"` (unique per replay attempt)
   - `payload` with `eventType: "callsCreated"` and `resourceIds: [callId]`
   - `payloadHash = null`, `receivedAt = now()`
5. Dispatch via `webhookDispatcher.dispatch(event)` — re-enters the normal async processing pipeline
6. Returns 202 ACCEPTED with message `"Replay accepted for callId: {callId}"`

> **Removed:** the `/admin/policies/*` and `/admin/policy-executions/*` API surface, plus their controllers and services (`AdminPolicyController`, `AdminPolicyExecutionController`, `AutomationPolicyService`, `AdminPolicyExecutionService`, `PolicyExecutionCursorCodec`), was deleted along with the policy subsystem (tables dropped in V12). Workflow admin APIs (`/admin/workflows/*`) replace them.

## Files in this flow

| Role | File |
|------|------|
| Webhook admin controller | `controller/AdminWebhookController.java` |
| Webhook admin service | `service/webhook/AdminWebhookService.java` |
| Webhook feed repository | `persistence/repository/JdbcWebhookFeedReadRepository.java` |
| Webhook cursor codec | `service/webhook/WebhookFeedCursorCodec.java` |
| SSE hub | `service/webhook/live/WebhookSseHub.java` |
| Processed calls controller | `controller/ProcessedCallAdminController.java` |
| Processed calls service | `service/webhook/ProcessedCallAdminService.java` |
