# UI 0.1 Implementation Plan (React + Vite)

## Summary
Build `ui 0.1` as the first frontend module inside this repo at `ui/`, using React + TypeScript + Vite with Tailwind + shadcn.

- Development: Vite + Spring run in parallel for hot reload and frontend DX.
- Production: built frontend assets are served by Spring as a single deployable artifact.

## Repo Placement and Structure
- Frontend root: `ui/`
- Production static output target: `src/main/resources/static/admin`

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
- `GET /admin/webhooks`
- `GET /admin/webhooks/{id}`
- `GET /admin/webhooks/stream` (SSE, `text/event-stream`)
- `GET /admin/processed-calls`
- `POST /admin/processed-calls/{callId}/replay`

## UI 0.1 Feature Scope
### 1) Webhooks page
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
- Run Spring backend (`:8080`) and Vite dev server (`:5173`)
- Configure Vite proxy for backend routes (`/admin/*`, `/webhooks/*`)
- Keep API paths relative in frontend code

### Production
- Build frontend (`ui/dist`)
- Copy build output into `src/main/resources/static/admin`
- Serve UI under `/admin-ui/*` with SPA fallback to `index.html`
- Keep API endpoints under existing backend routes

## Implementation Sequence
1. Bootstrap `ui/` workspace and core dependencies.
2. Build platform layer (router, query provider, API client, shared layout).
3. Implement webhooks module (snapshot + SSE + detail panel).
4. Implement processed-calls module (list + replay flow).
5. Add production asset copy and route fallback integration in Spring packaging flow.
6. Run frontend + backend validation and document runbook commands.

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
