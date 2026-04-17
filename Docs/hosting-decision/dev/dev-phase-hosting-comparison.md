# Dev-Phase Hosting Comparison (Internal Tool)

Date: 2026-04-14

## Scope and Requirements
- App stage: development phase only
- Usage: internal tool
- Priorities: lowest cost, simple setup, reliable webhook ingestion
- Not in scope right now: scaling strategy, multi-region availability
- Stack requirements:
  - Spring Boot backend (always-on webhook endpoint)
  - PostgreSQL database
  - React/Vite admin frontend
  - SSE/live updates for admin UI

## Providers Compared
- Railway
- Render
- Amazon Web Services (AWS)
- Microsoft Azure

## Summary Analysis
- Railway and Render both satisfy the core technical requirements for this app.
- AWS and Azure can also satisfy requirements, but usually introduce higher setup complexity and higher baseline cost for this stage.
- For this specific dev/internal phase, lowest-cost operation with acceptable platform limits is the deciding factor.

## Cost-Oriented Decision
- Winner: **Railway**

Why Railway wins for this phase:
- Lowest practical entry cost profile for always-on app + small Postgres in dev.
- Fast deployment path for Spring Boot + Postgres + env-based config.
- Supports required runtime behavior (public webhook endpoint, SSE-compatible behavior with heartbeat).
- Good enough operational controls for an internal tool without over-investing in infrastructure now.

## Notes and Tradeoffs
- Render remains a strong alternative if we later prefer more fixed-tier, predictable billing.
- AWS/Azure remain good learning and long-term platform options, but are not the cheapest fit for current scope.
- Re-evaluate hosting when requirements include production reliability targets, higher traffic, or multi-region.

## Final Recommendation
Use **Railway** for current dev-phase hosting.

## Minimal Hosting-Ready Checklist (Internal/Dev Phase)
- [ ] Pick one host (Railway) and one Postgres instance.
- [ ] Set required env vars on host: `DB_*`, `FUB_*`, webhook/rules/worker vars.
- [ ] Add UI production wiring:
  - [ ] Build `ui` during deploy.
  - [ ] Copy `ui/dist` into Spring static assets.
  - [ ] Serve SPA fallback for `/admin-ui/*` without breaking `/admin/*` and `/webhooks/*`.
- [ ] Deploy backend JAR and confirm app boots with Flyway migrations.
- [ ] Run core validation before deploy:
  - [ ] `./mvnw clean test`
  - [ ] `cd ui && npm run lint && npm run build && npm run test`
- [ ] Fix/adjust Playwright smoke so `cd ui && npm run test:e2e` passes.
- [ ] Sanity-check hosted endpoints:
  - [ ] `GET /health`
  - [ ] `GET /admin-ui`
  - [ ] `GET /admin/webhooks`
  - [ ] `GET /admin/webhooks/stream` (SSE connects + heartbeat)
- [ ] Send one real/simulated webhook and confirm end-to-end: ingest -> DB row -> admin UI visible.
- [ ] Disable noisy hosted logs (`spring.jpa.show-sql=false` for hosted profile).
- [ ] Rotate any exposed local secrets and ensure `.env` is never committed.

## SSE Hosting Notes (Important)
SSE reliability risk is mainly in hosting/proxy behavior, not containerization itself.

Key risks:
- Proxy/CDN response buffering can delay live events.
- Idle/read timeouts can close long-lived SSE connections.
- With multiple app replicas, in-memory subscriber maps are instance-local; events published on one instance are not automatically broadcast to clients connected to another instance.
- Reconnect windows can miss events if there is no replay/backfill strategy.
- Current code has a known null-safety TODO in `WebhookSseHub` (`Map.of(...)` with potentially nullable fields like `eventId`).

Minimum hosting settings for SSE route (`/admin/webhooks/stream`):
- Disable proxy buffering for SSE responses.
- Keep read/idle timeout higher than heartbeat interval.
- Preserve SSE headers and behavior (`Content-Type: text/event-stream`, `Cache-Control: no-cache`).
- Start with single app replica for internal/dev phase unless shared pub/sub is added.
