# UI 0.1 Implementation Plan (React + Vite)

## Summary
Build `ui` as an internal admin frontend using a frontend port-adapter architecture.

- Dev model: Vite (`:5173`) + Spring (`:8080`) in parallel with proxy.
- Prod model: packaged static assets served by Spring under `/admin-ui/*`.
- UI component strategy: prioritize reusable components and shared primitives first; avoid page-specific one-off implementations unless reuse is not practical.

## UI Source of Truth
- Style rules: `docs/ui-style-guide-v1.md`
- Figma references: `docs/ui-figma-reference.md`
- Code-level tokens: `ui/src/styles/tokens.css`

## Status (as of 2026-03-18)
### Completed
- `ui/` workspace scaffolded with React + TypeScript + Vite.
- Foundation dependencies added (`react-router-dom`, `@tanstack/react-query`, `zod`, testing stack, Playwright, Tailwind/PostCSS foundation).
- Frontend module boundaries implemented:
  - `ui/src/app`
  - `ui/src/platform`
  - `ui/src/modules/webhooks`
  - `ui/src/modules/processed-calls`
  - `ui/src/shared`
- Port-adapter foundation implemented:
  - `AdminWebhookPort`, `ProcessedCallsPort`, `WebhookStreamPort`
  - HTTP adapter (`GET /admin/webhooks`, `GET /admin/webhooks/{id}`)
  - SSE adapter wrapper for `/admin/webhooks/stream`
  - Zod validation at adapter boundary
- App shell and routing implemented:
  - `/` -> `/admin-ui/webhooks`
  - `/admin-ui/webhooks`
  - `/admin-ui/processed-calls`
  - placeholder session guard boundary
- Vite proxy configured for `/admin/*` and `/webhooks/*`.
- `scripts/run-app.sh` dev mode updated to run backend + frontend together and stream logs.

### In Progress / Pending
- Webhooks feature behavior (real list/filter/pagination/detail/live state updates in UI).
- Processed-calls feature behavior (real list/replay UX).
- Production packaging integration (copy `ui/dist` into build output + Spring SPA fallback wiring).

## Backend Interfaces to Consume
- `POST /webhooks/{source}` (producer ingress path)
- `GET /admin/webhooks`
- `GET /admin/webhooks/{id}`
- `GET /admin/webhooks/stream`
- `GET /admin/processed-calls`
- `POST /admin/processed-calls/{callId}/replay`

## Remaining 5-Phase Delivery Plan
### Phase 1: Webhooks page behavior (next)
- Implement history table + filters + cursor pagination from `GET /admin/webhooks`.
- Implement detail drawer from `GET /admin/webhooks/{id}`.
- Implement SSE live prepend + dedup + connection state from `GET /admin/webhooks/stream`.

### Phase 2: Processed-calls behavior
- Implement list filters and replay action UX for processed calls.
- Handle `202/404/409` replay responses with clear user feedback.

### Phase 3: Production asset packaging
- Copy `ui/dist` to `${project.build.outputDirectory}/static/admin` during Maven build.
- Avoid copying built assets into source-managed `src/main/resources`.

### Phase 4: Spring SPA route integration
- Serve `/admin-ui/*` with SPA fallback to `index.html`.
- Keep `/admin/*` and `/webhooks/*` API routes unchanged.

### Phase 5: Hardening and runbook completion
- Add e2e/smoke coverage for core flows.
- Finalize docs with dev/prod startup and verification commands.

## Validation Baseline
- UI checks: `npm run lint`, `npm run build`, `npm run test` (inside `ui/`).
- Backend checks: `./mvnw clean test`.

## Assumptions
- UI remains internal-first for v0.1.
- Backend contracts are consumed as-is (no new API contract changes in this plan).
- Hybrid model is intentional: standalone dev UX, Spring-served production artifact.
