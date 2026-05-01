# UI Submodule Agent Guide

This file defines how to work inside the `ui/` submodule for `automation-engine`.

## Context
- Submodule purpose: build and maintain the internal admin UI for webhook visibility and replay workflows.
- Parent module: `/Users/sarathkumar/Projects/2Creative/automation-engine`
- Product priority:
1. Scenario 1: call outcome -> task automation (primary)
2. Scenario 2: intent/transcription -> task (future)

## Source of truth (read first)
- Parent agreement: `/Users/sarathkumar/Projects/2Creative/automation-engine/AGENTS.md`
- Structure/implementation rules: `/Users/sarathkumar/Projects/2Creative/automation-engine/developer-rules.md`
- UI plan: `/Users/sarathkumar/Projects/2Creative/automation-engine/ui/docs/ui-0.1-plan.md`
- UI style decisions: `/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/ui-style-guide-v1.md`
- Figma baseline: `/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/ui-figma-reference.md`
- Tokens: `/Users/sarathkumar/Projects/2Creative/automation-engine/ui/src/styles/tokens.css`

## UI architecture decisions (locked for v0.1)
- Keep `ui/` as a standalone frontend module.
- Use hybrid runtime model:
1. Dev: Vite (`:5173`) + Spring (`:8080`) with proxy
2. Prod: built static assets served by Spring at `/admin-ui/*`
- Keep API paths relative (`/admin/*`, `/webhooks/*`); do not hardcode env-specific hosts.
- UI must consume HTTP contracts only; no server template coupling.
- Canonical shell layout is Option 1 and mandatory:
1. `Rail`: global icon-level navigation
2. `Panel`: section/context navigation + quick controls (route-context driven; rendered when route publishes panel content)
3. `Content`: route workspace
4. `Inspector`: contextual details/actions
- Keep module boundaries:
1. `src/app`: app shell, router, providers
2. `src/platform`: API adapters, Zod validation, query setup, SSE wrapper
3. `src/modules/webhooks`: webhook list/live/detail features
4. `src/modules/processed-calls`: processed call list/replay features
5. `src/shared`: reusable types/utils/primitives

## UX and style decisions (locked for v1)
- Canonical stream baseline: Figma `node-id=23-2` in file key `svLM7vfwHvmdxjoNE1Sr3U`.
- Layout model: Rail + Panel + Content + Inspector.
- Stream state/action rules:
1. Corner live indicator icon (not a full "Live" button)
2. Pause icon control allowed
3. No stream clear action
4. Compact activity tick strip under live feed header
- Filter model:
1. Structured filters by `source`, `status`, `eventType`, and time window
2. Explicit `Apply` and `Reset`
- Data priority:
1. Webhook list: `eventId`, `source`, `eventType`, `status`, `receivedAt`
2. Webhook detail: `id`, `eventId`, `source`, `eventType`, `status`, `payloadHash`, `payload`, `receivedAt`
3. Processed calls: `callId`, `status`, `ruleApplied`, `taskId`, `failureReason`, `retryCount`, `updatedAt`
- Accessibility and semantics:
1. Keyboard-accessible controls and rows
2. Status shown with color + text
3. Confirmation + explicit feedback for replay/destructive-like actions

## Engineering rules for this UI module
- Build in small vertical slices (feature + test + docs update when needed).
- Reuse backend contracts as-is; avoid UI-only API invention.
- Validate adapter responses with Zod before entering feature state.
- Keep server state in TanStack Query; avoid unnecessary local copies.
- Keep SSE logic isolated and reconnect-safe with:
1. explicit handling of `webhook.received` and `heartbeat`
2. deduplication by stable `id`
3. connection state visibility in UI
- Never log secrets or sensitive payloads.
- Use `shadcn/ui` as the default component system for reusable UI primitives.
- Keep string literals centralized. Do not scatter user-facing labels/messages in feature files.
- Reuse shared pagination and JSON display primitives for workflow/operator surfaces:
1. `shared/ui/PagePagination.tsx` for page/size/total pagination APIs
2. `shared/ui/JsonViewer.tsx` for read-only JSON payload rendering with copy support
- Prefer modular design for components, hooks, and methods. Extract reusable logic to helper files when duplicated or complex.
- Follow strict UI structure and separation:
1. `app`: route/shell/provider composition only
2. `platform`: adapters, transport, query wiring, stream contracts
3. `modules/*/data`: hooks and data orchestration
4. `modules/*/ui`: view components only
5. `shared`: reusable primitives, constants, helpers, and cross-module types
- Prefer performant code by default:
1. avoid unnecessary re-renders and derived-state duplication
2. memoize expensive computation/selectors where relevant
3. keep lists render-efficient and key-stable
4. prefer narrow props and pure presentational components

## Current delivery plan
1. Phase 1: webhooks page behavior (list/filter/cursor pagination + detail drawer + SSE prepend/dedup/state)
2. Phase 2: processed-calls behavior (filters + replay UX for `202/404/409`)
3. Phase 3: production packaging (`ui/dist` copied to `${project.build.outputDirectory}/static/admin`)
4. Phase 4: Spring SPA fallback for `/admin-ui/*` while preserving `/admin/*` and `/webhooks/*` APIs
5. Phase 5: hardening, smoke/e2e coverage, and runbook completion

## Step 1 completion plan (setup + component separation)
1. Establish shared UI primitives with `shadcn/ui` wrappers in `shared/ui`:
   - `DataTable`, `StatusBadge`, `EmptyState`, `ErrorState`, `LoadingState`, `PageHeader`
2. Create centralized constants:
   - `shared/constants/uiText.ts` for labels/messages
   - `shared/constants/routes.ts` for route segments
   - `shared/constants/queryDefaults.ts` for UI/query defaults
3. Separate module concerns:
   - `modules/*/ui`: page and presentational components only
   - `modules/*/data`: hooks/selectors/mappers only
   - `modules/*/lib`: helpers and pure transformers
4. Add a reusable right-inspector layout contract in `app` shell composition so feature pages can mount inspector content consistently.
5. Add platform-level stream hook boundary (`platform/stream/useWebhookStream.ts`) that normalizes subscribe/unsubscribe and connection state for module usage.
6. Ensure Step 1 tests cover:
   - shared state components render paths
   - shell layout regions and route structure
   - centralized constants usage in page headers/actions

## Step 1 status snapshot (as of 2026-03-19)
- Status: Complete
- Completed:
1. Shared reusable component baseline implemented (including `PageHeader`, `DataTable`, `StatusBadge`, state components, `FilterBar`, `ConfirmDialog`, `InspectorPanel`, `PanelNav`).
2. Workflow UI foundation shared components are now available:
   - `PagePagination` for page-based endpoint pagination
   - `JsonViewer` for reusable payload/graph/trigger JSON display
3. Centralized constants implemented and consumed (`uiText`, `routes`, `queryDefaults`).
4. Global floating notification system implemented (`NotifyProvider` + `useNotify` with success/error/warning/info).
5. Cyan + teal primary theme tokens applied.
6. Stream contract hook added (`useWebhookStream`) for Step 2 readiness.
   - Subscription lifecycle is stable for unchanged semantic filters; reconnects occur only on semantic filter change or unmount/remount.
7. Step 1 test suite added and validated with lint/build/test pass.
8. Option 1 shell migration completed across current routes (`/admin-ui/webhooks`, `/admin-ui/processed-calls`, `/admin-ui/session-disabled`) with route-level panel/inspector registration contracts and context-driven panel rendering.

## Working method
- Prefer small, reviewable increments.
- Follow composition over large page components.
- Separate hooks/data mapping/view components.
- Update docs when decision-level behavior changes.

## Test and validation policy
- For every behavior/code change:
1. add at least one new frontend test
2. run the new test
3. run existing frontend suite
- For every new code change/addition, writing new tests is mandatory:
1. add at least one newly created test that validates the new behavior/component/logic
2. execute the newly added test(s) in the same change cycle
3. execute the full existing test suite every time to verify backward compatibility and prevent regressions
- After every code change, verify lint and build pass before marking work complete (`npm run lint` and `npm run build` are mandatory each change cycle).
- For cross-layer changes, also run backend tests.
- Required UI baseline commands inside `ui/`:
1. `npm run lint`
2. `npm run build`
3. `npm run test`
- Report blockers clearly if execution is not possible; do not claim completion without validation evidence.
