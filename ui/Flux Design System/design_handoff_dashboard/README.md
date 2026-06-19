# Handoff: Operations Dashboard — "Health headline" (Direction A)

The landing dashboard for the **Flux** console — the internal operations
tool for a Follow Up Boss call-automation platform. This screen answers one question
first: **is the pipeline healthy right now?** — then lets an operator confirm trends,
scan recent runs, and clear failed runs.

---

## About the design files

The files in this bundle are **design references created in HTML/React-via-Babel** —
a working prototype showing the intended look, layout, and behavior. They are **not
production code to copy verbatim.** The task is to **recreate this design inside the
real product codebase** (`sarathkumar365/FUB-Automation`, `ui/` — React 19 + TypeScript +
Vite + Tailwind v4 + TanStack Query) using its existing components, tokens, and data
hooks. Where the prototype hand-rolls something the codebase already has (buttons,
badges, tables, the app shell), **use the codebase's version.**

The dashboard module already exists in the product at
`ui/src/modules/dashboard/ui/DashboardPage.tsx` with a real data hook
(`useDashboardSnapshotQuery`). This is a **redesign of that page** — keep the data
layer, replace the presentation.

## Fidelity: **High-fidelity**

Final colors, typography, spacing, and interactions. Recreate pixel-faithfully using
the codebase's Tailwind tokens (which already mirror the values below — see
`ui/src/styles/tokens.css`). Charts are hand-built SVG in the prototype; in the app,
use whatever the codebase standardizes on (a thin SVG component is fine — no heavy
chart lib needed for these).

---

## How to run the prototype locally

Two options:

1. **`Dashboard-standalone.html`** — fully self-contained (all scripts + styles inlined,
   fonts embedded). Double-click to open in any browser. No server, no network. **Start here.**

2. **`prototype/Dashboard.html`** — the readable multi-file source. Babel transpiles the
   `.jsx` in-browser, so it must be served over HTTP (file:// will block the module
   fetches). From the `prototype/` folder:
   ```bash
   npx serve .        # or: python3 -m http.server 8000
   ```
   then open the printed URL. React/Babel load from unpkg (needs internet); everything
   else is local.

Interact: toggle light/dark (moon icon, bottom of the left rail), click a **Replay**
button (confirm → toast → the failure clears and the headline recomputes), click a run
row, and open the **Tweaks** panel (if your host exposes it) to change accent / toggle
conversion rates / switch the chart range.

---

## Layout

A single scrollable column inside the product's **four-region app shell**
(icon rail · [no contextual panel] · content · [no inspector]). The dashboard
deliberately drops the panel and inspector — it's a full-width overview.

Content column: `max-width: 1240px`, centered, `24px` page padding (from the shell's
`<main>`), vertical rhythm of **`18px` gaps** between the three stacked sections:

```
┌───────────────────────────────────────────────────────────┐
│  HERO  (status headline  +  throughput chart)              │  ~ grid 1.05fr / 1fr
├───────────────────────────────────────────────────────────┤
│  FUNNEL RAIL  (4 stages, conversion % between them)        │  one bordered surface
├──────────────────────────────────┬────────────────────────┤
│  RECENT RUNS (table)             │  NEEDS ATTENTION (list) │  grid 1.7fr / 1fr, gap 16
└──────────────────────────────────┴────────────────────────┘
```

---

## Sections & components

### 1 · Hero — pipeline status
A `surface` card (`border`, `radius-md` 12px, `shadow-subtle`, `28px` padding,
`position: relative; overflow: hidden`) with an **ambient brand wash**: a single large
blurred radial-gradient blob of `--color-brand` at ~50% opacity, top-right, behind the
content (`z-index:0`; content `z-index:1`). Two columns, `gap: 36px`, vertically centered.

**Left column**
- **Kicker** — `PIPELINE STATUS · LAST 24H`. 11px / 800 / uppercase / `letter-spacing .12em` / muted.
- **Headline row** (`gap: 14px`, baseline-ish center):
  - `<h1>` — text is **"Healthy"** when there are open failures, **"All clear"** when zero.
    46px / 800 / `letter-spacing -0.03em`.
  - **Live pill** — rounded-pill, `--color-status-ok-bg` fill, `--color-status-ok` text,
    1px border `color-mix(status-ok 30%, transparent)`, `5px 12px`. Leading dot is
    `--color-live` (#10b981) with a soft pulsing ring (`box-shadow` keyframe, 2.2s).
    Label: **"Pipeline live"**.
- **Subtitle** — 14px / muted / line-height 1.5 / max-width 440px. Text is dynamic:
  `"Event ingestion, normalization and workflow runs are all flowing. {N} failed
  run(s) is/are waiting on replay."` → when N = 0: `"…flowing. No failures need attention."`
- **Stat strip** — top hairline border, `16px` padding-top, three stats divided by 1px
  vertical rules. Each: uppercase 11px muted label, then a **26px / 700 mono** tabular
  number, then a delta line (12px / 700, up/down chevron icon):
  - **Runs** `312` · delta `+8%` (green/up)
  - **Success** `97.0%` · delta `+0.3pt` (green/up)
  - **Open failures** `{N}` (red number when N>0) · delta `−2 today` (green; or `cleared` at 0)

**Right column — throughput chart**
- Header row: uppercase muted label `THROUGHPUT · EVENTS / HR` on the left; on the right
  a small live readout `{liveMin}/min` with a pulsing `--color-live` dot. `liveMin` ticks
  to a new random 6–10 every 2.6s while the live ticker is on.
- **Area chart** — smooth (Catmull-Rom) line in `--color-brand`, 2.4px stroke, soft
  vertical gradient fill (brand at 0.22 → 0 alpha), a 1px `--color-border` baseline, and
  a hollow end-point marker (surface fill, brand stroke). Height 150px, full width.
  Data = 24 hourly points (or last 12 when range = "12h").
- Footer row, 10px mono muted: `{range} ago` · `peak 166 · avg 132` · `now`.

### 2 · Funnel rail — the pipeline spine
**One** bordered surface (`radius-md`, `shadow-subtle`, `overflow: hidden`) — NOT four
separate cards. Four equal stage blocks (`flex: 1`, `16px 22px` padding) separated by
**connector** cells. This is the key fix over the old design: the stages read as one
continuous funnel, and each connector carries the **conversion rate** to the next stage.

Each **stage block**:
- Dot (7px, brand — or `--color-status-bad` for the Failed stage) + uppercase 11px muted label.
- **30px / 700 mono** value + small muted sub-label.
- A **sparkbar** mini-chart (8 bars, last 2 highlighted, 26px tall).

Stages: **Ingested** `148` (events/hr) · **Domain Events** `142` (normalized) ·
**Workflow Runs** `97` (started) · **Failed** `{N}` (needs replay; red).

Each **connector** (between stages): a `1px` left border, a chevron glyph, and — when
"conversion rates" is on — a centered **percentage** (12.5px / 700 mono, colored) over a
9.5px uppercase caption:
- Ingested→Events: `round(142/148) = 96%` "NORMALIZED" (green)
- Events→Runs: `round(97/142) = 68%` "TO RUNS" (neutral/muted)
- Runs→Failed: `(N/97×100).toFixed(1) = 3.1%` "FAIL RATE" (red)

> The conversion math is live — replaying a failure lowers the Failed count and the
> fail-rate %. Wire these to the same snapshot numbers the counts come from.

### 3a · Recent Runs (table)
A `PageCard` (title **"Recent Runs"**, subtitle "Latest workflow executions", 18px pad).
Uses the kit's **DataTable** "ledger" treatment: tracked uppercase 11px header, hairline
row rules, a **leading accent rail** (4px) per row colored by status
(error→bad, success→ok, else brand), row hover → `--color-surface-alt` + brand rail tint,
whole row clickable. Columns:
| Run (mono) | Workflow (mono) | Status (badge) | Duration (mono, `completed−started` in `s`, or `—`) | Completed (mono `HH:MM:SS` or `—`) |

Status badge tones: `SUCCEEDED`→success, `FAILED`→error, `RUNNING`→info(brand),
`CANCELED`→muted, `BLOCKED`→warning. Footer: an **outline "Browse all runs"** button.
Row click + button → navigate to the runs list (prototype fires a toast as a stand-in).

### 3b · Needs attention (worklist)
A `PageCard` (title **"Needs attention"**, subtitle "{N} failed run(s) awaiting replay").
List of failure rows (`9px` gap), each on a `--color-status-bad-bg` tint, `radius-sm`:
- A small uppercase **kind** tag (`RUN` / `CALL`) in a status-bad chip.
- mono **ref** id (bad-colored) + muted mono **reason** (e.g. `SIDE_EFFECT_ERROR`,
  `FUB 429 rate limit`, `Lead not found`), truncated.
- A second mono line: `{workflow} · {retries} retries · {ago} ago`.
- An **outline "Replay"** button → opens a **ConfirmDialog**
  ("Replay failed run?", body cites the ref + reason + the rule "Replay is available only
  for FAILED items") → on confirm, removes the row, shows a success **Toast**
  ("Replay queued · {kind} {ref}"), and the hero/funnel recompute.
- **Empty state** (N=0): a green check + "No failed runs — pipeline is healthy."

---

## Interactions & behavior

| Trigger | Behavior |
|---|---|
| Rail moon/sun button | Toggles `data-theme="dark"` on the app wrapper. All tokens remap (see dark theme in `colors_and_type.css`). |
| Replay button | ConfirmDialog → confirm → remove item, toast, recompute open-failure count, fail-rate %, Failed stage, headline ("Healthy"→"All clear" at 0). |
| Run row / "Browse all runs" | Navigate to runs view (prototype: toast placeholder). |
| Live ticker (on) | `events/min` readout updates every 2.6s. Respects `prefers-reduced-motion`. |
| Tweaks: Accent | Overrides `--color-brand` / `--color-brand-soft` on the app wrapper (cyan / teal / indigo / pink). |
| Tweaks: Conversion rates | Show/hide the connector percentages. |
| Tweaks: Throughput range | `12h` (last 12 pts) vs `24h` (all 24). |
| Tweaks: Live ticker | Pause/resume the events/min updater. |

**Motion** — entrance is intentionally NOT an opacity fade (a throttled/background tab
freezes CSS anims at frame 0, hiding content). Life comes from the live pulse dot,
sparkbars, and the ticker. Hover/press transitions are 0.12–0.15s. All decorative motion
disables under `prefers-reduced-motion: reduce`.

**Responsive** — designed at ~1360×900 desktop (an internal console). The hero and the
runs/attention grids are 2-column; collapse to single-column below ~960px if you support
narrow widths. The shell content area scrolls; the rail is fixed 64px.

---

## State

Local UI state in the prototype (`dashboard-app.jsx`):
- `theme` — `'light' | 'dark'` (provided via React context to the shell).
- `attention` — array of open failures; `Replay` filters one out. `openCount = attention.length`
  drives the headline, the hero "Open failures" stat, and the funnel "Failed"/fail-rate.
- `confirm` — the item pending replay confirmation (or null).
- `toast` — transient `{ kind, title, body }`, auto-clears after 2.8s.
- `liveMin` — the events/min number, ticked on an interval.
- Tweaks (`accent`, `conversions`, `range`, `live`) — persisted by the Tweaks host.

**In the real app:** all the numbers (counts, success rate, throughput series, recent
runs, open failures) come from the dashboard snapshot query
(`useDashboardSnapshotQuery`) — bind them, don't hard-code. The replay action should call
the existing replay mutation (`useReplayProcessedCallMutation` for calls; the equivalent
for runs) and invalidate the snapshot. The mock values below are the prototype's
stand-ins so it renders standalone.

---

## Design tokens (from `colors_and_type.css` / product `tokens.css`)

**Color (light)**
- bg `#f7f9fc` · surface `#ffffff` · surface-alt `#fbfdff` · border `#dde6f2`
- text `#0f172a` · text-muted `#64748b`
- brand `#0f9fb8` · brand-soft `#e3f7fb` · brand-2 (teal) `#0d9488`
- status ok `#0f766e` on `#ccfbf1` · warn `#b45309` on `#fef3c7` · bad `#be123c` on `#ffe4e6`
- live `#10b981` (pulse dot only)

**Color (dark, `[data-theme="dark"]`)**
- bg `#0b1220` · surface `#131c2e` · surface-alt `#1a2438` · border `#28344c`
- text `#e8eef9` · text-muted `#93a4c0`
- brand `#2dc4dd` · brand-soft `rgba(45,196,221,.16)`
- status ok `#5eead4` / warn `#fbbf24` / bad `#fb7185` on translucent fills · live `#34d399`

**Type** — UI: **Manrope** (400/500/600/700/800). Mono (all IDs, numbers, timestamps):
**JetBrains Mono**. Headline 46/800/−0.03em; card title 18/600; body 14/500/1.5;
kicker 11/800/uppercase/.12em; caption 11/600/uppercase; mono 12/500. Big numbers use
`font-variant-numeric: tabular-nums`.

**Radius** 8 (buttons/inputs/badges) · 12 (cards/panels) · 999 (pills).
**Spacing** 4px scale (4/8/12/16/20); cards pad 20 (18 here), section gaps 16–18.
**Shadow** subtle `0 1px 3px rgba(15,23,42,.08)`; hover adds `0 4px 12px`.

---

## Assets
No images or icon fonts. All icons are inline Lucide-style stroke SVGs (24-grid,
`stroke-width 1.8`, round caps, `currentColor`) — see `ui_kit/icons.jsx` and the
product's `ui/src/shared/ui/icons.tsx`. The logo is a flow-nodes glyph (one node
branching to two) in a brand-cyan rounded square — see `LogoMark` in `ui_kit/shell.jsx`.
Charts are hand-built SVG — see `charts.jsx`.

---

## Files in this bundle

```
Dashboard-standalone.html      ← open this; fully self-contained, runs offline
README.md                      ← this file
prototype/                     ← readable source (serve over HTTP to run)
  Dashboard.html               ← entry point / script load order
  dashboard-app.jsx            ← the screen: hero, funnel, runs, attention, replay, tweaks
  charts.jsx                   ← AreaChart, Sparkbars, Sparkline, Donut, HBars (token-driven SVG)
  dash-data.jsx                ← mock data + LivePill, Kicker, AttentionRow atoms
  dashboards.css               ← chart/layout helpers + animations (pulse, hero wash)
  tweaks-panel.jsx             ← the Tweaks panel shell + controls
  colors_and_type.css          ← all design tokens (light + dark) + semantic type roles
  ui_kit/                      ← faithful recreation of the product's shared components
    styles.css                 ← imports fonts + colors_and_type; kit helpers
    icons.jsx                  ← stroke icon set
    primitives.jsx             ← Button, Badge/StatusBadge, Input, Dropdown, DataTable,
                                 PageCard, ConfirmDialog, Toast, etc.
    shell.jsx                  ← four-region app shell + rail + LogoMark + ThemeCtx
    data.jsx                   ← RUNS / status tones / formatters used by the screen
```

**Where this maps in the product repo:** replace the body of
`ui/src/modules/dashboard/ui/DashboardPage.tsx`. Reuse `shared/ui/*` (Button, Badge,
DataTable, ConfirmDialog, the toast/notify system), the app shell + rail, and the
existing dashboard data hook. The prototype's `ui_kit/` and `primitives.jsx` exist only
so it renders standalone — prefer the real components.
