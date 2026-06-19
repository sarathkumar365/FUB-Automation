# Flux — Console UI Kit

A high-fidelity, **interactive** recreation of the Flux admin console. It's a cosmetic
prototype — components are simplified and data is mocked — but the layout, tokens, copy, and
interactions mirror the real product (`sarathkumar365/FUB-Automation`, `ui/`).

## Run it
Open **`index.html`**. You land on the **marketing landing** → Sign in / Create account →
the console.

### What works
- **Landing** — faithful recreation of the product's swirling 5-milestone pipeline timeline.
- **Auth** — **split layout** with a branded pipeline panel (the product "spine"): **Login** + a
  **Signup** (request-account) flow.
- **Icon rail** navigation (Dashboard · Webhooks · Processed Calls · Workflows · Persons) + logout.
- **Light / dark toggle** in the rail (moon/sun). Persists to `localStorage`. *Dark mode is an
  extension beyond the product, which ships light-only.*
- **Dashboard** — reimagined: no contextual panel/inspector; a **pipeline-health band**
  (Ingested → Decided → Tasks → Failed, connected, each with a micro-sparkline), a refined
  **Recent Runs** table, and a **Needs attention** panel.
- **Webhooks (flagship)** — a **live streaming feed**: pulse-dot Live pill, **icon pause/resume**,
  heartbeat timestamp, **activity tick-strip** (amber flash on each beat), structured filters with
  **Apply / Reset**, and a detail **inspector** (click any row → payload).
- **Processed Calls** — outcome table, **replay** on FAILED rows → confirm dialog → toast, inspector.
- **Workflows** — definitions list → detail with **Definition / Storyboard / Runs** tabs; the
  Storyboard tab renders the node graph on the dot-grid canvas with category accent dots.
- **Persons** (formerly Leads) — list → rich **detail** mirroring `GET /admin/persons/{id}/summary`:
  identifiers, timestamps, live-refresh status, a unified **activity timeline** (calls + runs +
  webhooks) with filters, per-stream recents, and the stored FUB snapshot.

### Notable UI patterns
- **Custom `Dropdown`** — all filter selects are a styled, keyboard-accessible popover (not native
  `<select>`).
- **Refined `DataTable`** — tracked uppercase headers, hairline row rules, and a leading status
  **accent rail** that lights on hover/selection (`accent` prop colors it per row).
- **`Button` caveat** — never pass a `style` prop to `Button`; the editor instrumentation strips its
  internal styles. Use the `fullWidth` prop and wrap for margins.
- **No opacity entrance animations on content** — a throttled preview iframe freezes CSS animations
  at frame 0, leaving content stuck hidden. Keep the visible state as the base.

## Files
| File | Role |
|---|---|
| `index.html` | Loads React 18 + Babel, the stylesheet, and every module below, in order. |
| `styles.css` | `@import`s `../../colors_and_type.css` + the kit's animations (heartbeat, entrance, blobs). |
| `icons.jsx` | The stroke icon set (Lucide-style, 1.8px) lifted from `ui/src/shared/ui/icons.tsx`, + a few additions. |
| `primitives.jsx` | `Button · Badge · StatusBadge · Input · Dropdown · DateInput · FilterBar · DataTable · PageHeader · PageCard · ConfirmDialog · Toast`. |
| `shell.jsx` | Four-region `ShellLayout` (icon Rail · Panel · Content · Inspector) + `ThemeCtx`. |
| `data.jsx` | Mock data + formatters (event types, statuses, runs, persons + summary, storyboard graph, accents). |
| `screens-landing.jsx` | `LandingPage` — the swirling-timeline marketing landing. |
| `screens-auth-dash.jsx` | `LoginPage`, `SignupPage` (split pipeline layout), `DashboardPage`, `PipelineStage`. |
| `screens-webhooks.jsx` | `WebhooksPage`, `LivePill`, `ActivityTickStrip`. |
| `screens-processed-workflows.jsx` | `ProcessedCallsPage`, `WorkflowsPage`, `StoryboardCanvas`. |
| `screens-persons.jsx` | `PersonsPage` — list + rich detail (activity timeline, live status, streams). |
| `app.jsx` | Auth gate, route switch, theme provider, mount. |

## Conventions worth copying
- **Everything is token-driven** — colors/radii/shadows come from `colors_and_type.css` vars; no
  hard-coded hex in component logic.
- **Components export to `window`** at the end of each file (no bundler), so later `<script>`s can use
  them. Each file's local style objects are uniquely named to avoid global collisions.
- **The seven locked UX rules** (see root README §4) are honored here — pulse-dot not a Live button,
  icon pause, no clear action, tick-strip, structured filters, keyboard-accessible rows, minimal
  inspector actions.

## Known simplifications
- Data is mocked and the "stream" is a timer; no real SSE/webhooks.
- The storyboard is a static preview of one graph (no drag/zoom/validation).
- The full mobile drawer behavior (rail/panel/inspector collapse) is not reproduced — the kit targets
  a desktop console width.
- Best viewed wide: when Panel **and** Inspector are both open, the content column needs room
  (≥1200px) before tables/storyboards stop clipping.
