# UI Design-System Conformance — Settings + Status Screens

> **Status:** ✅ Complete (archived) — all phases shipped on `feature/ui-design-system-conformance`.
> **Scope:** Frontend-only this pass. No backend changes.
> **Gate:** `npm run check` green — 394 tests; status family browser-verified light/dark + in-shell.

One pass that brings two design-system surfaces into the admin UI:

| Part | Deliverable | Source design |
|---|---|---|
| **A** | **Settings page** — new primary-nav surface | `Automation Engine Design System/ui_kits/automation-engine/screens-settings.jsx` |
| **B** | **Status screens** — rebuild Error · 404 · Session Disabled as one family | `Automation Engine Design System/design_handoff_status_screens/` |

Both follow the **design-system kit as the source of truth** (`ui/AGENTS.md`), reuse the existing tokens /
primitives / central API layer, and are **token-only** (dark mode themes for free).

## Part A — Settings page

The one primary-nav surface the design system specifies that the UI hasn't built. The backend ships a
**read-only** `GET /admin/settings/config`; there is no write API, no managed-webhooks endpoint, secrets are
redacted, and only 1 of the design's 4 feature flags is exposed.

**Scope decision — real read, show only real data, editing "coming soon":**
- **Read path is fully real**, wired through the existing central API layer
  (`HttpJsonClient` → `container.ts` `appPorts` → `useAppPorts`).
- **Show only what the backend returns.** Fields the design shows but the endpoint doesn't expose (3 of 4
  feature flags; managed-webhooks) render an explicit **"not available yet"** — **never fabricated values**
  (one of those flags is the RD-006 engine-echo safety control; a guessed value would mislead).
- **No local form state** — controls are controlled by the query data; a change attempt fires
  **"This editing feature is coming soon."** and does not mutate. Nothing is persisted or faked.
- The write seam (`updateConfig` + the design's Save/Reset dirty bar) is the documented drop-in for when the
  backend `PUT` lands.

## Part B — Status screens

Rebuild `AppErrorFallback` (error), `NotFoundPage` (404), `SessionDisabledPage` (session) — all plain today —
as **one family** per the handoff: **"Direction B + console strip"** (bare-on-gradient, a large faint
ghost-glyph watermark, brand lockup top-center, a mono console status strip), driven by **one shared
`FullPageStatus` shell**.

**Scope decision:** extract the shared shell + content renderer (don't duplicate); ship Direction B + strip
for standalone, calmer in-shell 404; **`SessionDisabledPage` renders full-page** (latest handoff — overrides
`ui-0.1-plan.md`'s in-shell note); the lockup **reuses the centralized wordmark** (`uiText.authShell.wordmark`
— no new "Automation Engine" literals, RD-005 rename pending); `AppErrorFallback` stays **router-hook-free**;
transform-only entrance motion guarded by `prefers-reduced-motion`.

## Documents

- [`plan.md`](./plan.md) — design + research: architecture, lifecycle diagrams, file lists, verification, and
  the consolidated findings + review-resolved decisions A–G.
- [`implementation-log.md`](./implementation-log.md) — append-only, one dated section per shipped phase.

## Phase tracker (all shipped)

| # | Phase | Test |
|---|-------|------|
| **A1** | Settings foundation lib (schema, projection, sections) | `settings-projection` |
| **A2** | Platform seam — `settingsPort` + `httpSettingsAdapter` + DI + queryKeys | `settings-adapter` |
| **A3** | Data hook — `useSettingsConfigQuery` | `settings-hooks` |
| **A4** | `Toggle` (Radix switch) + lucide `Settings`/`Refresh` icons | `settings-primitives` |
| **A5** | UI — `SettingRow`, `ManagedWebhooksCard`, `SettingsPage` (coming-soon gate) | `settings-page` |
| **A6/A7** | Wire-in (route/rail/nav) + live browser verification | (routing) |
| **B1** | Shared foundation — `FullPageStatus`, `useRise`, status glyphs | `status-foundation` |
| **B2** | Content renderer — `StatusScreen` (Direction B + console strip) | `status-screen` |
| **B3** | `AppErrorFallback` + `ErrorDetails` + error threading (router-free) | `app-error-boundary` |
| **B4** | `NotFoundPage` — "404" watermark, real-path strip, standalone + in-shell | `app-routing` |
| **B5** | `SessionDisabledPage` (warn) — full-page, route out of `AppShell` | `session-disabled` |
| **B6** | Copy — `uiText` (`appError`/`notFound`/`session`) centralized | (in page tests) |
| **B7** | Gate + browser-verify (light/dark/in-shell) | — |

## Out of scope (future backend work)

- Settings write API (`PUT /admin/settings/config`) → unblocks real editing (then add `updateConfig`, a
  mutation hook, the Save/Reset dirty bar; remove the coming-soon gate). Needs a runtime-override store and a
  refactor of the worker's startup-only `@ConditionalOnProperty`.
- Expose the 3 missing feature flags in the GET response.
- `GET /admin/settings/webhooks` + FUB registration sync → wires "Sync now" for real.
