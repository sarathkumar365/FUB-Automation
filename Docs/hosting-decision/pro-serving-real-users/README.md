# Pro Hosting Notes (Serving Real Users)

Date: 2026-04-17

## Scope
This document tracks production risks and required controls when serving real users.

## SSE Risks That Matter in Production

1. Multi-instance fanout gap (Critical when running 2+ app replicas)
- Current model keeps SSE subscribers in-memory per instance.
- Impact: users connected to instance A will not receive events published on instance B.
- Required mitigation: add shared pub/sub fanout (for example Redis) or enforce single replica until fanout is implemented.

2. Proxy/load-balancer buffering and idle timeout (Critical even with 1 replica)
- Some proxies buffer streaming responses or close long-lived HTTP connections.
- Impact: delayed updates, silent disconnects, unreliable real-time UI.
- Required mitigation: disable response buffering for `/admin/webhooks/stream` and set read/idle timeouts higher than heartbeat interval.

3. Missing replay/backfill on reconnect (High)
- SSE reconnect can miss events emitted during disconnect windows.
- Impact: operator UI can miss webhook updates.
- Required mitigation: implement replay/backfill by last-seen cursor/id and rehydrate missed events on reconnect.

4. Null-safety bug risk in SSE payload map (High)
- `WebhookSseHub` has a TODO about `Map.of(...)` with nullable fields (for example `eventId`).
- Impact: publish path can fail for specific events and drop live updates.
- Required mitigation: replace with null-tolerant payload construction.

## Lower-Risk but Required SSE Hygiene
- Preserve SSE response behavior and headers (`Content-Type: text/event-stream`, `Cache-Control: no-cache`).
- Keep heartbeat enabled and aligned with proxy idle settings.

## Production Go/No-Go Gates for SSE
- [ ] SSE route validated through production ingress (no buffering, no premature disconnects).
- [ ] Multi-instance fanout strategy implemented or deployment constrained to single replica.
- [ ] Reconnect replay/backfill tested under forced disconnect scenario.
- [ ] Null-safe payload publish fix shipped and verified.
