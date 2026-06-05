# Research — Settings + Status Screens

Consolidated findings. **Part A = Settings page. Part B = Status screens.**

---

# Part A — Settings page

## A1. Authoritative design spec

**Source of truth = the design-system kit** (`ui/AGENTS.md`):
`Automation Engine Design System/ui_kits/automation-engine/screens-settings.jsx` (+ `HANDOFF.md`).

- **Layout:** four-region shell. Panel = "Configuration" section-nav (4 buttons; active pill
  `--color-brand-soft` bg + `--color-brand` text). Content = one card per active section, `max-width ~720px`.
  **No inspector.**
- **Header:** title "Settings", subtitle "Platform configuration for this workspace."
- **Sections:**
  1. **Business hours** — timezone `select`, start/end hour `number` (unit `h`), weekdays-only `toggle`.
  2. **Feature flags** — 4 toggles: `engine.write.emit-events`, `workflow.worker.enabled`,
     `workflow.worker.stale-processing-enabled`, `webhook.sources.fub.enabled`.
  3. **Connections** — `fub.base-url`/`fub.x-system` text, `fub.api-key`/`fub.x-system-key` secret;
     green "Connected" badge + live dot.
  4. **Managed webhooks** — read-only table (event · status · last delivery) + "Sync now".
- **Dirty bar:** sticky `--shadow-hover`; "You have unsaved changes" + Reset (outline) + Save changes;
  "Settings saved" toast.

> Superseded: `ui/Docs/ui-product-design-proposal.md` (lines 202–233) describes an earlier 2-tab read-only
> variant with different sections — **the kit supersedes it.** We follow the kit.

## A2. Backend read contract (real, today)

`GET /admin/settings/config` — `AdminSettingsController` → `AdminSettingsService` → `SettingsConfigResponse`.
- Roles ADMIN/OPERATOR/VIEWER; 401 unauthenticated. **Read-only by design** ("editable settings are a
  separate future initiative"). No POST/PUT/PATCH.
- Shape (confirmed by `AdminSettingsControllerTest`): `businessHours{timezone,startHour,endHour,weekdaysOnly}`,
  `fubConnection{baseUrl,apiKey{present,value},xSystem,xSystemKey{...}}`, `fubRetry{...}`,
  `webhook{maxBodyBytes,sources{enabled,signingKey{...}},liveFeed...}`, `callRules{...}`.
- **Secrets redacted:** `Redactable{present, value:"***"|null}` — raw key never sent.

## A3. Design ↔ backend reconciliation (the crux)

| Design section | Backend reality | Verdict |
|---|---|---|
| **Business hours** | `businessHours{...}` — exact match | **Fully backed (read).** |
| **Connections** | `fubConnection{...}`, **secrets redacted** | Backed; secrets show **Configured / Not configured** only. |
| **Feature flags** | Only `webhook.sources.enabled` returned | 1 of 4 backed; **3 mock-defaulted for display.** |
| **Managed webhooks** | **No endpoint** (`/admin/webhooks` = ingested events, not registrations) | **Fully mocked.** |
| `fubRetry`, `callRules`, webhook body/heartbeat | Returned, **not in the kit** | Intentionally not shown. |

**Editing is structurally blocked server-side** (future write-API concern, documented):
- Values bind once from `@ConfigurationProperties` at startup; runtime edits need a persistence/override layer.
- `WorkflowExecutionDueWorker` is gated by `@ConditionalOnProperty(prefix="workflow.worker", name="enabled")`
  — startup-only; `workflow.worker.enabled` can't be runtime-toggled without refactoring the worker.
- `BusinessHoursService` reads its mutable properties live — the only cleanly runtime-editable group.

## A4. Conventions to reuse (Settings)

- **Central API layer ("where we make API calls"):** `platform/adapters/http/httpJsonClient.ts`
  (`HttpJsonClient`), `platform/container.ts` (`appPorts`), `app/useAppPorts.ts`.
- Module shape `modules/<feature>/{data,lib,ui}` (mirror `modules/dashboard`); port/adapter/DI;
  `queryKeys.ts`; `useQuery` (mirror `useDashboardSnapshotQuery`); `useShellRegionRegistration`;
  `uiText.ts` (centralized copy); `shared/ui` primitives (`PageHeader`, `PageCard`, `DataTable`,
  `StatusBadge`, `Button`, `Input`, `Select`); `useNotify` toasts; routing in `routes.ts` + `router.tsx` +
  `AppRail.tsx`.
- **Gap:** no `Toggle`/`Switch` primitive exists (backlog #11) — build it token-clean + accessible, export
  from the barrel. No Settings rail icon — add `SettingsIcon` (+ `RefreshIcon` for "Sync now").

---

# Part B — Status screens

## B1. Design handoff (source of truth)

`ui/Automation Engine Design System/design_handoff_status_screens/`:
- `README.md` — the written spec. `reference/status-screens.jsx` — component reference (recreate, don't copy).
- `reference/status.css` — `.aestatus-root { isolation: isolate }` + link/details hover (`color .12s`);
  motion is **JS-driven (WAAPI)**, never CSS opacity.
- `reference/final.html` + `final-canvas.jsx` — gallery harness (reference only); confirms ship =
  **all three `variant="B" strip`**, in-shell 404 = `<NotFoundPage inShell />`, dark = `data-theme="dark"`.

**Chosen direction:** Direction **B + console strip**. (A/C are explored alternatives — not shipped.)

## B2. Shared shell — `FullPageStatus`

Lifted from `AuthShell`:
1. **Ambient brand gradient:**
   ```
   radial-gradient(760px 360px at 50% -14%, color-mix(in srgb, var(--color-brand) 13%, transparent), transparent 64%),
   radial-gradient(560px 300px at 86% 114%, color-mix(in srgb, var(--color-brand-2) 10%, transparent), transparent 60%),
   var(--color-bg)
   ```
2. **Brand lockup** pinned top-center (`top:40px`): `LogoMark size≈34` + "Automation Engine" (15/800/`-.01em`)
   over "Operations console" (12/600 muted).
3. **Centered content slot** (`flex:1`, padding `104px 40px 56px`).
- Props: `inShell` (gradient→transparent + drop lockup, padding `32px`), `hideLockup`.
- Root sets **`isolation: isolate`** (own stacking context for the watermark/gradient layering).

### Tones (`TONES`) — always color **+ a word**
| tone | fg | bg | eyebrow |
|---|---|---|---|
| `bad` | `--color-status-bad` | `--color-status-bad-bg` | `ERROR` |
| `warn` | `--color-status-warn` | `--color-status-warn-bg` | `RESTRICTED` |
| `brand` | `--color-brand` | `--color-brand-soft` | `404 · NOT FOUND` |

## B3. Direction B body

- **Ghost-glyph watermark** (`aria-hidden`, absolute, `translate(-50%,-56%)`, `tone.fg`, `opacity 0.07`):
  error → alert-triangle (372, sw 1.1); session → lock (372, 1.1); 404 → **"404" numerals**
  (`--font-ui` 800, 300px, `-.04em`).
- **Foreground stack** (`max-width 560`, centered): PillEyebrow (tone pill, glyph 14) → Title (`h1`
  32/800/`-.025em`) → Body (15/1.55 muted, `42ch`, `text-wrap:pretty`) → optional Helper → **StatusStrip**
  (7px tone dot + mono 12px; `--radius-sm`, `1px --color-border`, `--color-surface`, `--shadow-subtle`) →
  Actions (gap 16) → optional Extra.

## B4. Per-page spec

- **`AppErrorFallback`** (bad): alert-triangle; `ERROR`; "Something went wrong"; body "The console hit an
  unexpected error. Reloading usually fixes it."; strip `BOUNDARY · render error caught · 500`; primary
  **Reload** (`window.location.reload()`), secondary **Go to dashboard** = **plain `<a href="/admin-ui">`**;
  dev-only collapsible `ErrorDetails` (`error.message`+`error.stack`). **Signature `({ error?: unknown })`,
  no router hooks.**
- **`NotFoundPage`** (brand): compass glyph, **"404" watermark**; `404 · NOT FOUND`; "Page not found"; body
  "The page you are looking for does not exist or has moved."; strip `GET <attempted path> · 404` (real path);
  primary router link → `/admin-ui`, secondary **Back** (chevron-left, `history.back()`). **Standalone +
  `inShell` placements.**
- **`SessionDisabledPage`** (warn): lock glyph; `RESTRICTED`; "Admin access is disabled"; body about the
  session guard; helper emphasizing "workspace administrator"; strip `GUARD · admin-ui access disabled`;
  **no primary**, quiet **Return to sign in**.

## B5. Current repo state + wiring (being replaced)

- `src/app/AppErrorFallback.tsx` — plain `Button` + plain `<a>`; **no `error` prop / no `ErrorDetails`** yet;
  already router-hook-free.
- `src/app/NotFoundPage.tsx` — plain + a router `<Link>`; no in-shell variant.
- `src/app/SessionDisabledPage.tsx` — renders **inside `AppShell`** (shell-region + `PageCard`/`EmptyState`).
- Consumers of the fallback: `RouteErrorBoundary` (`useRouteError()`) and `AppErrorBoundary` (class, **outside**
  RouterProvider) — **neither passes the error yet**; the rebuild threads it through for `ErrorDetails`.
- `router.tsx`: top-level `*` (standalone 404) + in-shell `*` under `adminUi` (in-shell 404) both render
  `<NotFoundPage />`; `session-disabled` is an `adminUi` child.

## B6. Reuse / new (Status screens)

- **Reuse:** `LogoMarkIcon`, `Button` (`size="lg"`), token names. **Gradient caveat:** the status family
  uses the **handoff gradient** (`760×360 at 50% -14%` / brand 13% / brand-2 10% **+ a `var(--color-bg)`
  base layer**). `AuthShell` inlines a *similar but not identical* gradient (`700×320 at 50% -10%` / brand
  12% / brand-2 9%, **no** bg layer). So the status family gets its **own** gradient constant — do **not**
  blindly refactor `AuthShell` onto it (that would visibly shift the auth screens). Optionally unify later
  as a deliberate visual change, not a silent DRY merge.
- **New glyphs in `shared/ui/icons.tsx`:** alert-triangle, compass, lock, chevron-left, chevron-down.
- **New hook `useRise`:** transform-only WAAPI rise (`translateY(14px)→0`, 540ms, `cubic-bezier(.2,.7,.3,1)`),
  skip on `prefers-reduced-motion`; no opacity, no fill.

## B7. Constraints (Status screens)

- `AppErrorFallback` + `FullPageStatus` **router-hook-free**; dev-only error stack hidden in prod; terminal
  states (no loading/data/forms); keyboard-accessible; ≥44px touch targets.

---

## Tokens (both parts — all present, light + dark, in `ui/src/styles/tokens.css`)

`--color-bg`, `--color-brand`, `--color-brand-2`, `--color-brand-soft`, `--color-status-bad(-bg)`,
`--color-status-warn(-bg)`, `--color-status-ok(-bg)`, `--color-surface(-alt)`, `--color-border`,
`--color-text(-muted)`, `--color-live`, `--radius-sm/md/pill`, `--shadow-subtle/hover/float`,
`--font-ui`, `--font-mono`. **Never hard-code hex** (`lint:tokens`).
