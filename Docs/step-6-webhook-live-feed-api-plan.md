# Step 6: Webhook Live Feed API Plan

## Summary
Implement a UI-ready webhook live feed with:
- `GET /admin/webhooks` (snapshot/history, cursor pagination),
- `GET /admin/webhooks/{id}` (full detail),
- `GET /admin/webhooks/stream` (SSE live updates).

This revision explicitly includes:
1. Cursor paging must be DB keyset-based.
2. Remove scan-loop behavior (`SCAN_MULTIPLIER`, `MAX_SCAN_ROUNDS`) entirely.
3. For `includePayload=false`, do not fetch payload from DB.

## Implementation Changes
1. Schema update (required to satisfy no-payload-fetch requirement)
- Add columns to `webhook_events`:
  - `event_type VARCHAR(64) NOT NULL`
  - `resource_ids JSONB NOT NULL` (array form)
- Add indexes:
  - `(source, event_type, received_at DESC, id DESC)`
  - `(status, received_at DESC, id DESC)`
  - `(received_at DESC, id DESC)`
- Keep existing `payload` column for detail/debug use.

2. Ingress persistence update
- In `WebhookIngressService`, after parser normalization and before save:
  - derive `eventType` and `resourceIds` once,
  - persist both into new columns with existing payload.
- Publish live event DTO after save (non-fatal on publish errors).

3. Admin read APIs
- New `AdminWebhookController`:
  - `GET /admin/webhooks`
  - `GET /admin/webhooks/{id}`
  - `GET /admin/webhooks/stream`
- New `AdminWebhookService`:
  - param validation (`limit`, `from/to`, cursor),
  - list/detail orchestration,
  - cursor encode/decode,
  - SSE subscribe/publish delegation.
- New `WebhookSseHub`:
  - emitter registry, filter matching, heartbeat, cleanup.

4. Repository/query design (DB-only filtering + exact fetch size)
- Add projection-based list query for `includePayload=false` selecting only:
  - `id,eventId,source,eventType,resourceIds,status,receivedAt`
- Add second list query for `includePayload=true` including payload.
- Apply all filters in DB:
  - `source,status,eventType,from,to`
- Keyset cursor condition in DB:
  - `(receivedAt,id) < (:cursorReceivedAt,:cursorId)`
- Fetch exactly `limit + 1` rows for `nextCursor` (no scan rounds, no in-memory eventType filtering).

5. DTO / interface updates
- `WebhookFeedItemResponse`: `id,eventId,source,eventType,resourceIds,status,receivedAt,payload?`
- `WebhookFeedPageResponse`: `items,nextCursor,serverTime`
- `WebhookEventDetailResponse`: includes `payloadHash,payload`.
- Cursor format remains opaque base64 JSON with `receivedAt` + `id`.

## Test Plan
1. Migration + persistence
- Verify new columns populate correctly during webhook ingestion.
- Verify backward safety for existing rows (migration defaults/compat handling).

2. Repository tests
- Keyset pagination correctness with tie on same `receivedAt`.
- DB-level filter correctness for `eventType/source/status/time`.
- `limit + 1` behavior and `nextCursor` generation.

3. Service/controller tests
- `400` invalid cursor/time range, `404` missing id.
- `includePayload=false` response omits payload.
- `includePayload=true` includes payload.
- Cursor round-trip and page continuity.

4. SSE tests
- Subscriber receives `webhook.received` immediately after ingest.
- Filtered stream only receives matching events.
- Heartbeat event emitted.
- Emitter cleanup on disconnect/error.

5. Integration flow
- Ingest webhook -> appears in `GET /admin/webhooks`.
- Ingest webhook while stream connected -> live event received.
- Reconnect + snapshot backfill recovers missed events.
- Duplicate ingest does not create duplicate row or duplicate live event.

## Assumptions / Defaults
- Minimal service count remains: only `AdminWebhookService` and `WebhookSseHub` are added.
- Single-instance in-memory SSE hub for v1; multi-instance fanout (Redis/Kafka) deferred.
- `resource_ids` stored as JSONB array to avoid payload reads in list path.
- Existing webhook ingest API contract (`POST /webhooks/fub`) remains unchanged externally.
