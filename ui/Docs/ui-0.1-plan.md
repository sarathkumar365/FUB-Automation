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

## Status (as of 2026-03-19)
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
  - `/admin-ui/session-disabled` rendered within shell with guard redirect support
- Vite proxy configured for `/admin/*` and `/webhooks/*`.
- `scripts/run-app.sh` dev mode updated to run backend + frontend together and stream logs.

### In Progress / Pending
- Webhooks live behavior (stream updates + connection state in page).
- Processed-calls feature behavior (real list/replay UX).
- Production packaging integration (copy `ui/dist` into build output + Spring SPA fallback wiring).

### Step 1 implementation checkpoint (as of 2026-03-19)
Status: Complete
- Shared foundations added with reusable primitives:
  - `PageHeader`, `PanelNav`, `InspectorPanel`, `DataTable`, `StatusBadge`, `LoadingState`, `EmptyState`, `ErrorState`, `FilterBar`, `ConfirmDialog`.
- Centralized constants added and adopted:
  - `shared/constants/uiText.ts`
  - `shared/constants/routes.ts`
  - `shared/constants/queryDefaults.ts`
- Global notification system added:
  - app-level `NotifyProvider` + `useNotify()`
  - floating top-right stack with `success|error|warning|info`, queueing, auto-dismiss, and manual close.
- Stream hook boundary added:
  - `platform/stream/useWebhookStream.ts` with connection-state contract.
  - stream lifecycle is stable for unchanged semantic filters (no reconnect on object-identity-only rerenders).
- Theme updated to cyan + teal primary brand tokens.
- Step 1 tests added and passing (`npm run test`, `npm run lint`, `npm run build` all green).
- Option 1 shell layout migration completed across current routes:
  - Rail + Panel + Content + Inspector is now the single app-shell contract.
  - `/admin-ui/webhooks`, `/admin-ui/processed-calls`, and `/admin-ui/session-disabled` all render inside this shell.
- Route-level region registration contract implemented:
  - pages can publish panel/inspector descriptors through shell registration hooks.
  - panel rendering is context-driven: desktop panel is shown only when a route publishes panel content.

## Backend Interfaces to Consume
- `POST /webhooks/{source}` (producer ingress path)
- `GET /admin/webhooks`
- `GET /admin/webhooks/{id}`
- `GET /admin/webhooks/stream`
- `GET /admin/processed-calls`
- `POST /admin/processed-calls/{callId}/replay`

## 5-Step Gradual Implementation Flow
### Step 1: Set up basic structure and shared components
- Finalize app shell, routing skeleton, and core layout regions.
- Build shared UI primitives and states (loading/empty/error/status).
- Keep platform foundations ready: API client wrapper, Zod parsing, query provider, and SSE hook boundary.

#### Step 1 execution breakdown (decision-complete)
1. Shared components baseline (`shadcn/ui` first):
   - Create reusable primitives in `src/shared/ui`: `DataTable`, `StatusBadge`, `EmptyState`, `ErrorState`, `LoadingState`, `PageHeader`.
   - Use token-aligned variants and avoid page-specific visual duplication.
2. Centralize literals/constants:
   - Add `src/shared/constants/uiText.ts` for visible copy and action labels.
   - Add `src/shared/constants/routes.ts` for route paths/segments.
   - Add `src/shared/constants/queryDefaults.ts` for list defaults and limits.
3. Enforce module separation:
   - `src/modules/*/ui` for views only.
   - `src/modules/*/data` for hooks/state orchestration.
   - `src/modules/*/lib` for pure helpers/mappers.
4. Finalize shell layout contract:
   - Option 1 mandatory layout: Rail + Panel + Content + Inspector.
   - Use route-level descriptor registration for panel and inspector content.
   - Keep responsive behavior:
     - desktop: rail/content/inspector remain active regions; panel appears when route publishes panel descriptors
     - tablet: panel/inspector toggle behavior
     - mobile: panel/inspector drawer behavior
5. Finalize setup contracts:
   - Keep TanStack Query defaults in provider.
   - Introduce platform stream hook boundary (`src/platform/stream/useWebhookStream.ts`) for normalized event handling + connection state shape.
6. Step 1 test additions:
   - Add tests for shared state primitives.
   - Add tests verifying route shell layout regions.
   - Add tests proving centralized strings/constants are consumed by pages.

### Step 2: Implement page 1 (Webhooks history + detail)
- Build filters, history table, and pagination with `GET /admin/webhooks`.
- Add row selection and detail inspector with `GET /admin/webhooks/{id}`.
- Keep filter/apply/reset flow URL-friendly and predictable.

#### Step 2 implementation checkpoint (as of 2026-03-19)
Status: Complete
- Implemented structured filter controls (`source`, `status`, `eventType`, `from`, `to`) with explicit Apply/Reset behavior.
- Implemented URL search-param mapping for list state (`source`, `status`, `eventType`, `from`, `to`, `cursor`, `selectedId`).
- Implemented webhook list query hook with platform defaults (`limit=25`) and cursor-based pagination.
- Implemented selectable history table rows and inspector detail loading via `GET /admin/webhooks/{id}`.
- Added/updated Step 2 tests and validated UI checks (`npm run test`, `npm run lint`, `npm run build`).

### Step 3: Add live behavior to page 1
- Connect `GET /admin/webhooks/stream` for live updates.
- Handle `webhook.received` and `heartbeat`, prepend new items, and deduplicate by stable `id`.
- Show connection/live state clearly in the page.

### Step 4: Implement page 2 (Processed calls + replay)
- Build processed-calls list and filters with `GET /admin/processed-calls`.
- Add replay action via `POST /admin/processed-calls/{callId}/replay`.
- Handle `202`, `404`, and `409` responses with confirmation + clear feedback.

### Step 5: Production integration and hardening
- Copy `ui/dist` to `${project.build.outputDirectory}/static/admin` during Maven build.
- Serve `/admin-ui/*` through SPA fallback while preserving `/admin/*` and `/webhooks/*` APIs.
- Add smoke/e2e checks and finalize dev/prod runbook commands.

## Validation Baseline
- UI checks: `npm run lint`, `npm run build`, `npm run test` (inside `ui/`).
- Backend checks: `./mvnw clean test`.

## Engineering directives (must follow)
- Use `shadcn/ui` as the default source for reusable UI components.
- Keep all user-facing string literals in centralized constants files; avoid inline strings in feature components.
- Prefer modular design at component, hook, and method levels; extract reusable logic into helper files.
- Follow and preserve UI architecture structure (`app`, `platform`, `modules`, `shared`), and keep responsibilities strict.
- Prefer performant code by default:
1. avoid avoidable re-renders
2. avoid redundant state copies
3. use memoization/selectors only where it reduces real work
4. keep list rendering and keys stable

## Assumptions
- UI remains internal-first for v0.1.
- Backend contracts are consumed as-is (no new API contract changes in this plan).
- Hybrid model is intentional: standalone dev UX, Spring-served production artifact.

## Migration Notes (Option 1)
- Legacy shell style (single left sidebar + content + static inspector) is deprecated.
- All `/admin-ui/*` pages now rely on one shell contract and shared region components.
- Any new route must provide inspector descriptors and may optionally provide panel descriptors based on route context, instead of custom ad-hoc page shell structures.
