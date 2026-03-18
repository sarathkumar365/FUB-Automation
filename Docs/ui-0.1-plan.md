# UI 0.1 Implementation Plan (React + Vite)

## Summary
Build `ui 0.1` as the first frontend module inside this repo at `ui/`, using React + TypeScript + Vite with Tailwind + shadcn.

- Hybrid delivery model:
  - Development: standalone Vite (`:5173`) + Spring (`:8080`) run in parallel for HMR/live reload.
  - Production: UI is packaged into the Spring artifact as static assets.

## Repo Placement and Structure
- Frontend root: `ui/`
- Production static output target: `${project.build.outputDirectory}/static/admin` (copied at build time)

Suggested frontend module layout:
- `ui/src/platform` (app shell, auth/session boundary, API client, query client, error handling)
- `ui/src/modules/webhooks` (Step 6 live feed UI)
- `ui/src/modules/processed-calls` (list + replay UI)
- `ui/src/shared` (types, formatters, utilities)
- `ui/src/app` (router, providers, layout, nav)

## Locked Tech Stack
- React + TypeScript + Vite
- Tailwind CSS + shadcn/ui
- React Router
- TanStack Query
- Zod for API response validation
- Native `EventSource` wrapper for SSE
- Vitest + Testing Library
- Playwright (smoke/e2e)

## Backend Interfaces to Consume
Existing/validated endpoints:
- `POST /webhooks/{source}` (webhook producer ingress path)
- `GET /admin/webhooks`
- `GET /admin/webhooks/{id}`
- `GET /admin/webhooks/stream` (SSE, `text/event-stream`)
- `GET /admin/processed-calls`
- `POST /admin/processed-calls/{callId}/replay`

## UI 0.1 Feature Scope
### 1) Webhooks page (Phase 1 priority)
- Filter bar: `source`, `eventType`, `status`, `from`, `to`, `includePayload`
- Snapshot/history list with cursor pagination
- SSE live prepend for `webhook.received` events
- SSE connection status indicator
- Row click opens webhook detail panel via `GET /admin/webhooks/{id}`

### 2) Processed calls page
- Status/date/limit filtering
- Replay action with confirmation modal and feedback toast

### 3) App shell
- Session-aware route guard
- Left navigation for modules
- Shared table/status/date UI primitives

## Delivery Model
### Development
- Run Spring backend (`:8080`) and Vite dev server (`:5173`) together
- Configure Vite proxy for backend routes (`/admin/*`, `/webhooks/*`)
- Keep API paths relative in frontend code
- Update `scripts/run-app.sh` dev mode to start both Spring and Vite (plus existing tunnel flow)

### Production
- Build frontend (`ui/dist`)
- Copy build output into `${project.build.outputDirectory}/static/admin` during Maven build
- Serve UI under `/admin-ui/*` with SPA fallback to `index.html`
- Keep API endpoints under existing backend routes

## 5-Phase Implementation Plan
### Phase 1: Webhooks page (live + non-live)
- Implement `/admin-ui/webhooks` with snapshot list (`GET /admin/webhooks`) and detail panel (`GET /admin/webhooks/{id}`).
- Add SSE live stream (`GET /admin/webhooks/stream`) with dedup by `id` and connection status.
- Keep history and live feed in one unified table/filter experience.

### Phase 2: Frontend foundation and module boundaries
- Bootstrap `ui/` workspace and lock dependencies/tooling.
- Build platform layer: router, query provider, API client, zod contracts, shared primitives.
- Add internal placeholder route guard and app shell navigation.

### Phase 3: Development runtime and startup script integration
- Configure Vite proxy for `/admin/*` and `/webhooks/*`.
- Extend `scripts/run-app.sh` dev mode to run Spring + Vite + tunnel with clean shutdown.
- Validate HMR/live reload while backend APIs remain served by Spring.

### Phase 4: Production packaging and Spring SPA routing
- Add build step that copies `ui/dist` to `${project.build.outputDirectory}/static/admin`.
- Add Spring `/admin-ui/*` SPA fallback to `index.html`.
- Ensure no source-tree static asset churn and clean behavior under `mvn clean`.

### Phase 5: Processed calls module and end-to-end hardening
- Implement `/admin-ui/processed-calls` list and replay flow.
- Add replay confirmation modal and success/error feedback handling (`202/404/409`).
- Finalize docs/runbook and complete frontend + backend validation pass.

## Test Plan and Acceptance
- Frontend unit/integration:
  - snapshot render and filter behavior
  - SSE insert + dedup by `id`
  - reconnect/backfill behavior
  - webhook detail fetch/render
  - processed-calls replay request/response handling
- Backend validation:
  - run admin webhook integration flow test to verify list/detail ingest behavior
- Done criteria:
  - existing backend tests pass (subject to current environment constraints)
  - new frontend tests pass
  - no API contract mismatches for Step 6 payloads

## Assumptions
- This UI is internal-first in v0.1 but intentionally structured as the CRM foundation.
- Spring remains the single backend boundary for now.
- Future AI capabilities will be added behind Spring APIs without changing the frontend architecture model.
- Hybrid model is intentional: standalone UI in dev, packaged UI in production.
