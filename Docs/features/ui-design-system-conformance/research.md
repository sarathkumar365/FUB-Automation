# Research — Settings + Status Screens

Consolidated findings. **Part A = Settings page. Part B = Status screens.**
Decisions resolved during the design review are listed in [§ Review-resolved decisions](#review-resolved-decisions).

---

# Part A — Settings page

## A1. Authoritative design spec

**Source of truth = the design-system kit** (`ui/AGENTS.md`):
`Automation Engine Design System/ui_kits/automation-engine/screens-settings.jsx` (+ `HANDOFF.md`).

- **Layout:** four-region shell. Panel = "Configuration" section-nav (4 buttons; active pill
  `--color-brand-soft` bg + `--color-brand` text). Content = one card per active section, `max-width ~720px`.
  **No inspector.**
- **Header:** title "Settings", subtitle "Platform configuration for this workspace."
- **Sections:** Business hours · Feature flags · Connections · Managed webhooks (controls + copy per the kit).

> Superseded: `ui/Docs/ui-product-design-proposal.md:202` (older 2-tab read-only variant) — the kit wins.

## A2. Backend read contract (real, today)

`GET /admin/settings/config` — `AdminSettingsController` → `AdminSettingsService` → `SettingsConfigResponse`.
- Roles ADMIN/OPERATOR/VIEWER; 401 unauthenticated. **Read-only by design.** No POST/PUT/PATCH.
- Shape (confirmed by `AdminSettingsControllerTest`): `businessHours{timezone,startHour,endHour,weekdaysOnly}`,
  `fubConnection{baseUrl,apiKey{present,value},xSystem,xSystemKey{...}}`, `fubRetry{...}`,
  `webhook{maxBodyBytes,sources{enabled,signingKey{...}},liveFeed...}`, `callRules{...}`.
- **Secrets redacted:** `Redactable{present, value:"***"|null}`.

## A3. Design ↔ backend reconciliation — show only real data

The page renders **only what the backend actually returns**. Fields the design shows but the endpoint does
**not** expose are surfaced as an explicit **"not available yet"** state — **never fabricated**. (This honors
the UI rule *"do not invent UI-only backend behavior"* and avoids misrepresenting safety-relevant config.)

| Design section | Backend reality | Treatment |
|---|---|---|
| **Business hours** (4 fields) | `businessHours{...}` — exact match | Real values, read-only display. |
| **Connections** | `fubConnection{...}`, secrets redacted | Real values; secrets show **Configured / Not configured**. |
| **Feature flags** (4) | Only `webhook.sources.enabled` returned | Backed flag shown (read-only); the other **3 → "not available yet"** (no fabricated value). |
| **Managed webhooks** | **No endpoint** | Section shows a **"not available yet"** empty state — **no mock rows**. |
| `fubRetry`, `callRules`, webhook body/heartbeat | Returned, not in the kit | Not shown. |

**RD-006 note (engine-echo safety):** one unexposed flag is `engine.write.emit-events`, the engine-echo
control governed by `RD-006` (safe-by-default, platform-gated). Because the endpoint doesn't return it,
fabricating a value (e.g. `false`) could tell an operator echo-emission is OFF when its real state is
**unknown**. It therefore renders "not available yet", never a guessed value.

## A4. Editing model — "coming soon", zero local state

Editing is **not functional** this pass. Per the UI rule *"keep server state in TanStack Query; avoid
duplicating fetched data in local component state"*, the page holds **no local form state**:
- Controls are **controlled by the query data** (`checked`/`value` come straight from `useSettingsConfigQuery`).
- A change attempt fires `notify.info("This editing feature is coming soon.")` and **does not mutate** — the
  control snaps back to the query value (nothing to persist, nothing to copy locally).
- The write seam (`SettingsPort.updateConfig` + the design's Save/Reset dirty bar + a mutation hook) is the
  documented drop-in for when the backend `PUT` lands.

## A5. Conventions to reuse (Settings)

- **Central API layer:** `platform/adapters/http/httpJsonClient.ts`, `platform/container.ts` (`appPorts`),
  `app/useAppPorts.ts`.
- Module shape `modules/<feature>/{data,lib,ui}`; port/adapter/DI; `queryKeys.ts`; `useQuery`
  (mirror `useDashboardSnapshotQuery`); `useShellRegionRegistration`; `uiText.ts` (centralized copy);
  `shared/ui` primitives (`PageHeader`, `PageCard`, `DataTable`, `StatusBadge`, `Button`, `Input`, `Select`);
  `useNotify`; routing in `routes.ts` + `router.tsx` + `AppRail.tsx`.
- **`Toggle` primitive — wrap `@radix-ui/react-switch`** (no switch dep yet; the repo already wraps Radix
  `dialog`/`popover`/`tabs`, so a Radix wrapper is the established pattern and gives a11y for free). Add the
  dep; export the wrapper from the barrel. On this page the Toggle is rendered controlled-by-query with its
  change handler gated to the coming-soon notice.
- Add `SettingsIcon` (rail) + `RefreshIcon` ("Sync now") to `icons.tsx`.

---

# Part B — Status screens

## B1. Design handoff (source of truth)

`ui/Automation Engine Design System/design_handoff_status_screens/`:
- `README.md` (spec). `reference/status-screens.jsx` (component reference — recreate, don't copy).
- `reference/status.css` — `.aestatus-root { isolation: isolate }` + link/details hover; motion is
  **JS-driven (WAAPI)**, never CSS opacity.
- `reference/final.html` + `final-canvas.jsx` — gallery harness (reference only); confirms ship =
  **all three `variant="B" strip`**, in-shell 404 = `<NotFoundPage inShell />`, dark = `data-theme="dark"`.

**Chosen direction:** Direction **B + console strip**.

## B2. Shared shell — `FullPageStatus`

1. **Ambient brand gradient** (handoff values — see § gradient caveat):
   `radial-gradient(760px 360px at 50% -14%, color-mix(in srgb, var(--color-brand) 13%, transparent), transparent 64%),`
   `radial-gradient(560px 300px at 86% 114%, color-mix(in srgb, var(--color-brand-2) 10%, transparent), transparent 60%), var(--color-bg)`
2. **Brand lockup** pinned top-center: `LogoMarkIcon` + wordmark. **Reuse the existing centralized wordmark
   strings** (`uiText.authShell.wordmark` / `wordmarkSub`) — do **not** introduce new "Automation Engine"
   literals (`RD-005` renames the product to *Throughline*; keep the rename single-source).
3. **Centered content slot** (`flex:1`, padding `104px 40px 56px`).
- Props: `inShell` (gradient→transparent + drop lockup, padding `32px`), `hideLockup`.
- Root sets **`isolation: isolate`**.

### Tones (`TONES`)
| tone | fg | bg | eyebrow |
|---|---|---|---|
| `bad` | `--color-status-bad` | `--color-status-bad-bg` | `ERROR` |
| `warn` | `--color-status-warn` | `--color-status-warn-bg` | `RESTRICTED` |
| `brand` | `--color-brand` | `--color-brand-soft` | `404 · NOT FOUND` |

## B3. Direction B body

- **Ghost-glyph watermark** (`aria-hidden`, absolute, `translate(-50%,-56%)`, `tone.fg`, `opacity 0.07`):
  error → alert-triangle (372, sw 1.1); session → lock; 404 → **"404" numerals** (`--font-ui` 800, 300px, `-.04em`).
- **Foreground stack** (`max-width 560`): PillEyebrow → Title (`h1` 32/800/`-.025em`) → Body (15/1.55 muted,
  `42ch`) → optional Helper → **StatusStrip** (mono console strip) → Actions → optional Extra.

## B4. Per-page spec

- **`AppErrorFallback`** (bad): alert-triangle; "Something went wrong"; strip `BOUNDARY · render error caught · 500`;
  primary **Reload**, secondary **plain `<a href="/admin-ui">`** (not a router link); dev-only `ErrorDetails`
  (`error.message`+`error.stack`, `import.meta.env.DEV` only). **Signature `({ error?: unknown })`, no router hooks.**
- **`NotFoundPage`** (brand): compass + **"404" watermark**; "Page not found"; strip `GET <attempted path> · 404`
  (real path via `useLocation`); primary router link → `/admin-ui`, secondary **Back** (`history.back()`).
  **Standalone + `inShell`.**
- **`SessionDisabledPage`** (warn): lock; "Admin access is disabled"; helper emphasizing "workspace
  administrator"; strip `GUARD · admin-ui access disabled`; **no primary**, quiet **Return to sign in**.
  **Renders full-page** (see Decision D below).

## B5. Current repo state + wiring (being replaced)

- `src/app/AppErrorFallback.tsx` — plain `Button` + plain `<a>`; **no `error` prop / no `ErrorDetails`** yet;
  already router-hook-free.
- `src/app/NotFoundPage.tsx` — plain + a router `<Link>`; no in-shell variant.
- `src/app/SessionDisabledPage.tsx` — currently renders **inside `AppShell`** (shell-region + `PageCard`).
- Fallback consumers: `RouteErrorBoundary` (`useRouteError()`) + `AppErrorBoundary` (class, **outside**
  RouterProvider) — **neither passes the error yet**; the rebuild threads it through for `ErrorDetails`.
- `router.tsx`: top-level `*` (standalone 404) + in-shell `*` under `adminUi` (in-shell 404) both render
  `<NotFoundPage />`; `session-disabled` is an `adminUi` child (inside the shell today).

## B6. Reuse / new (Status screens)

- **Reuse:** `LogoMarkIcon`, `Button` (`size="lg"`), the centralized wordmark, token names.
- **Gradient caveat:** the status family uses its **own** constant with the **handoff** values
  (`760×360 / -14% / brand 13%` + a `var(--color-bg)` base). `AuthShell`'s gradient is *similar but not
  identical* (`700×320 / -10% / brand 12%`, no base) — **leave `AuthShell` untouched**; any unification is a
  separate, deliberate visual change.
- **New glyphs in `shared/ui/icons.tsx`:** alert-triangle, compass, lock, chevron-left, chevron-down.
- **New hook `useRise`:** transform-only WAAPI rise (`translateY(14px)→0`, 540ms, `cubic-bezier(.2,.7,.3,1)`),
  skip on `prefers-reduced-motion`; no opacity, no fill.

## B7. Constraints

- `AppErrorFallback` + `FullPageStatus` **router-hook-free**; dev-only error stack hidden in prod; terminal
  states (no loading/data/forms); keyboard-accessible; ≥44px touch targets.

---

# Review-resolved decisions

Decisions taken during the design review (apply to both parts):

- **A. Wordmark single-source** — status/auth lockups render the existing `uiText.authShell.wordmark` /
  `wordmarkSub`; no new product-name literals (RD-005 rename pending). *Accepted.*
- **B. No fabricated data** — Settings shows only backend-returned values; unexposed flags + managed-webhooks
  render "not available yet". *Accepted.* (RD-006 safety + "don't invent UI-only behavior".)
- **C. Zero local form state** — Settings controls are controlled by the query data; interaction fires the
  coming-soon notice and does not mutate. *Accepted.*
- **D. `SessionDisabledPage` is full-page** — the latest handoff wins over `ui-0.1-plan.md`'s in-shell note;
  the route renders outside `AppShell`. *Accepted (user, this review).*
- **E. `Toggle` wraps `@radix-ui/react-switch`** — consistent with existing Radix wrappers; add the dep. *Accepted.*
- **F. Test-with-each-phase** — every phase that adds code lands its own test (not deferred to the final phase).
  *Accepted.*

## Tokens (both parts — all present, light + dark, in `ui/src/styles/tokens.css`)

`--color-bg`, `--color-brand`, `--color-brand-2`, `--color-brand-soft`, `--color-status-bad(-bg)`,
`--color-status-warn(-bg)`, `--color-status-ok(-bg)`, `--color-surface(-alt)`, `--color-border`,
`--color-text(-muted)`, `--color-live`, `--radius-sm/md/pill`, `--shadow-subtle/hover/float`,
`--font-ui`, `--font-mono`. **Never hard-code hex** (`lint:tokens`).
