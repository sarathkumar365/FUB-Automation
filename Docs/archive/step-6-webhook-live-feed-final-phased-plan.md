# Step 6: Webhook Live Feed API Plan

## Summary
Implement a UI-ready webhook live feed with:
- `GET /admin/webhooks` (snapshot/history, cursor pagination),
- `GET /admin/webhooks/{id}` (full detail),
- `GET /admin/webhooks/stream` (SSE live updates).

Primary goal: when a webhook is persisted, UI receives a live `webhook.received` event immediately.

## Phase 1: Schema + Ingress + Live Publish Hook
1. Update `webhook_events` schema:
- Add `event_type` column.
- Keep existing `payload` column for detail/debug use.
- Add feed-oriented indexes for source/event_type/status/time.

2. Update ingress persistence:
- In `WebhookIngressService`, derive `eventType` once from normalized payload.
- Persist `event_type` with existing event fields and payload.

3. Add post-save live publish hook:
- Publish live feed DTO after successful save.
- Publish failures are non-fatal and must not fail webhook ingest.
- Duplicate ingests (pre-check or DB unique conflict) must not publish.

## Phase 3: SSE Live Stream
Status: Complete (2026-03-18)

## Phase 2: Snapshot + Detail APIs
Status: Complete (2026-03-18)

1. Add `GET /admin/webhooks`:
- Supports `limit`, `cursor`, `source`, `status`, `eventType`, `from`, `to`, `includePayload`.
- `includePayload` default is `false`.
- Uses cursor pagination with opaque base64 JSON token containing `receivedAt` + `id`.
- Fetches `limit + 1` rows to compute `nextCursor`.

2. Query strategy:
- Use pragmatic DB filtering for core fields, optimized for maintainability and performance.
- For `includePayload=false`, use lightweight projection/list path.
- For `includePayload=true`, use payload-inclusive path.

3. Add `GET /admin/webhooks/{id}`:
- Returns full event detail including `payloadHash` and `payload`.

4. DTO / interface updates:
- `WebhookFeedItemResponse`: `id,eventId,source,eventType,status,receivedAt,payload?`
- `WebhookFeedPageResponse`: `items,nextCursor,serverTime`
- `WebhookEventDetailResponse`: includes `payloadHash,payload`.

## Test Plan
1. Phase 1 tests:
- New migration/persistence test validates `event_type` is populated on ingest.
- Duplicate ingest path verifies no duplicate row and no duplicate live publish trigger.

2. Phase 2 tests:
- Repository/service/controller coverage for cursor continuity and filter behavior.
- `400` for invalid cursor or invalid time range.
- `404` for missing webhook id.
- `includePayload=false` omits payload by default.
- `includePayload=true` includes payload.

3. Phase 3 tests:
- SSE subscriber receives `webhook.received` immediately after ingest.
- Filtered stream receives only matching events (`source/status/eventType`).
- Heartbeat event emitted every 15 seconds.
- Emitter cleanup on disconnect/error.

4. Integration flow:
- Ingest webhook -> appears in `GET /admin/webhooks`.
- Ingest webhook while stream connected -> live event received.
- Reconnect + snapshot backfill recovers missed events.
- Duplicate ingest does not create duplicate row or duplicate live event.

5. Validation execution:
- For each code increment, run newly added tests and then the existing suite.
- Do not mark complete unless tests pass and overall success remains above project threshold.

## Assumptions / Defaults
- Minimal service count remains: only `AdminWebhookService` and `WebhookSseHub` are added.
- Single-instance in-memory SSE hub for v1; multi-instance fanout (Redis/Kafka) deferred.
- `resource_ids` is out of scope for this phase.
- Backfill for legacy rows is handled manually outside this implementation.
- SSE heartbeat interval is set to 15 seconds.
- Existing webhook ingest API contract (`POST /webhooks/fub`) remains unchanged externally.
