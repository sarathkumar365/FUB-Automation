# Flow F: Admin Observability and Operations APIs

## API surface summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/webhooks` | List webhook events (paginated) |
| `GET` | `/admin/webhooks/{id}` | Webhook event detail |
| `GET` | `/admin/webhooks/stream` | SSE live feed |
| `GET` | `/admin/processed-calls` | List processed calls |
| `POST` | `/admin/processed-calls/{callId}/replay` | Replay a failed call |
| `GET` | `/admin/policies` | List policies by scope |
| `GET` | `/admin/policies/{domain}/{policyKey}/active` | Get active policy |
| `POST` | `/admin/policies` | Create policy |
| `PUT` | `/admin/policies/{id}` | Update policy |
| `POST` | `/admin/policies/{id}/activate` | Activate policy version |
| `GET` | `/admin/policy-executions` | List execution runs (paginated) |
| `GET` | `/admin/policy-executions/{id}` | Execution run detail with steps |
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

## Policy control plane API

**`POST /admin/policies`** — Create new policy

Request body:
```json
{
  "domain": "ASSIGNMENT",
  "policyKey": "FOLLOW_UP_SLA",
  "enabled": true,
  "blueprint": { ... }
}
```

Creates an `AutomationPolicyEntity` with `status = INACTIVE` and `version = 0`. Blueprint is validated via `PolicyBlueprintValidator`. Returns 201 CREATED with `PolicyResponse`.

**`PUT /admin/policies/{id}`** — Update policy

Request body:
```json
{
  "enabled": true,
  "expectedVersion": 1,
  "blueprint": { ... }
}
```

Uses **optimistic locking** via JPA `@Version`. If `expectedVersion` doesn't match → 409 CONFLICT (stale version). Blueprint is re-validated on update.

**`POST /admin/policies/{id}/activate`** — Activate policy version

Request body:
```json
{
  "expectedVersion": 1
}
```

Activation logic:
1. Find policy by ID — 404 if not found
2. Check `expectedVersion` matches — 409 CONFLICT if stale
3. **Deactivate all other active policies in the same scope** via `deactivateActivePoliciesInScopeExcludingId()`:
   ```sql
   UPDATE automation_policies
   SET status = 'INACTIVE'
   WHERE domain = :domain AND policy_key = :policyKey
     AND status = 'ACTIVE' AND id <> :excludedId
   ```
4. Set this policy's `status = ACTIVE`
5. Returns 200 OK with `PolicyResponse`

**Why single-active invariant:** The unique partial index `uk_automation_policies_active_per_scope` on `(domain, policy_key) WHERE status = 'ACTIVE'` ensures at most one active policy per domain+key combination at the database level. The deactivation query provides application-level safety.

**`GET /admin/policies`** — List policies by scope

Query params: `domain` (required), `policyKey` (required). Returns `List<PolicyResponse>` ordered by `id DESC`.

**`GET /admin/policies/{domain}/{policyKey}/active`** — Get active policy

Returns the active policy for the given scope. Validates blueprint on read — returns 422 if blueprint is invalid (allows detection of corrupted policies). Returns 404 if no active policy.

## Policy execution read APIs

**`GET /admin/policy-executions`**

Query parameters:

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `PolicyExecutionRunStatus` | no | Filter by run status |
| `policyKey` | `String` | no | Filter by policy key (trimmed, uppercased) |
| `from` | `OffsetDateTime` | no | Start time filter (on `createdAt`) |
| `to` | `OffsetDateTime` | no | End time filter |
| `limit` | `Integer` | no | Page size (default: 50, max: 200) |
| `cursor` | `String` | no | Pagination cursor |

**Response:** `PolicyExecutionRunPageResponse`
```json
{
  "items": [
    {
      "id": 1,
      "source": "FUB",
      "eventId": "abc-123",
      "sourceLeadId": "456",
      "domain": "ASSIGNMENT",
      "policyKey": "FOLLOW_UP_SLA",
      "policyVersion": 1,
      "status": "COMPLETED",
      "reasonCode": "COMPLIANT_CLOSED",
      "createdAt": "2026-04-08T10:00:00Z",
      "updatedAt": "2026-04-08T10:15:00Z"
    }
  ],
  "nextCursor": "eyJjcmVhdGVkQXQiOiI...",
  "serverTime": "2026-04-08T10:20:00Z"
}
```

**Pagination:** Same keyset/cursor pattern as webhook feed, using `(createdAt, id)` bookmark with Base64-encoded JSON cursor. Uses JPA `Specification` for filtering.

**`GET /admin/policy-executions/{id}`**

Returns `PolicyExecutionRunDetailResponse` with full run details plus all steps ordered by `stepOrder`:

```json
{
  "id": 1,
  "source": "FUB",
  "eventId": "abc-123",
  "webhookEventId": 10,
  "sourceLeadId": "456",
  "domain": "ASSIGNMENT",
  "policyKey": "FOLLOW_UP_SLA",
  "policyVersion": 1,
  "policyBlueprintSnapshot": { ... },
  "status": "COMPLETED",
  "reasonCode": "COMPLIANT_CLOSED",
  "idempotencyKey": "PEM1|a1b2c3...",
  "createdAt": "2026-04-08T10:00:00Z",
  "updatedAt": "2026-04-08T10:15:00Z",
  "steps": [
    {
      "id": 1,
      "stepOrder": 1,
      "stepType": "WAIT_AND_CHECK_CLAIM",
      "status": "COMPLETED",
      "dueAt": "2026-04-08T10:05:00Z",
      "dependsOnStepOrder": null,
      "resultCode": "CLAIMED",
      "errorMessage": null,
      "createdAt": "2026-04-08T10:00:00Z",
      "updatedAt": "2026-04-08T10:05:00Z"
    }
  ]
}
```

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
| Policy controller | `controller/AdminPolicyController.java` |
| Policy service | `service/policy/AutomationPolicyService.java` |
| Policy execution controller | `controller/AdminPolicyExecutionController.java` |
| Policy execution service | `service/policy/AdminPolicyExecutionService.java` |
| Policy execution cursor | `service/policy/PolicyExecutionCursorCodec.java` |
