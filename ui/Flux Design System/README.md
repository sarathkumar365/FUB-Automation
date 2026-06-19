# Flux — Design System

> Internal operations console for a **Follow Up Boss** call-automation platform.
> This folder is the single source of truth for designing on-brand Flux
> interfaces, assets, and decks — for production work *or* throwaway prototypes.

---

## 1 · Product context

**Flux** is an internal admin console and **event-driven workflow platform** for
real-estate operations teams. It **ingests external events** from connected source systems
(**Follow Up Boss is the first; the model is source-agnostic**), **normalizes them into typed domain
events**, and runs **workflows triggered by those events**. Workflows are built from typed steps
(trigger, wait, branch, side-effect, compute) and **every step is recorded** for a complete audit
trail. Auto-creating a follow-up task on a call outcome is one workflow among many — not the whole
platform.

It is an **operations console, not a consumer app.** The tone is clean, high-clarity, professional,
modern. Density is deliberately low — generous spacing, no telemetry walls.

### What the product does
- **Ingests external events** (signed FUB webhooks today, `/webhooks/fub`) and persists them inbox-first.
- **Normalizes** raw payloads into typed **domain events** the platform can reason about.
- **Triggers workflows** matched to domain events by type and rules.
- Runs **typed workflow steps** — wait, branch, side-effect, compute — to completion.
- **Records every step** and replays failed runs safely (full, queryable audit trail).
- Provides admin visibility: **live event feed**, **processed-event status with replay**, **persons**,
  and a visual **workflow builder + runs** surface.

### Core surfaces (the screens you'll design)
| Surface | What it is |
|---|---|
| **Landing** | Marketing-style pipeline story (5 milestones on a swirling timeline). |
| **Login** | JWT bearer auth, single centered card. |
| **Dashboard** | Stat tiles (active workflows / failed runs / ingest activity) + recent runs. |
| **Webhooks** | The flagship pattern — live streaming feed, pause, activity tick-strip, structured filters, detail inspector. |
| **Processed Calls** | Outcome table with replay-on-failure + replay inspector. |
| **Persons** | Local person records synced from source systems (formerly "Leads"), with a unified activity timeline + live FUB refresh. |
| **Workflows / Runs** | Definitions list, workflow detail with a **storyboard** (node graph) tab, run observability. |

---

## 2 · Sources (provided inputs)

Everything here was reverse-engineered from the product's own code. The reader is encouraged to
explore the repository directly for deeper fidelity:

- **GitHub:** [`sarathkumar365/FUB-Automation`](https://github.com/sarathkumar365/FUB-Automation)
  - Backend: Java 21 · Spring Boot 4 · Spring Data JPA · Flyway · PostgreSQL
  - Frontend (the design source of truth): `ui/` — React 19 + TypeScript + Vite + **Tailwind v4**,
    TanStack Query, Zod, Radix primitives, `class-variance-authority`.
  - Design tokens lifted verbatim from **`ui/src/styles/tokens.css`**.
  - Component vocabulary from **`ui/src/shared/ui/*`** and the feature screens in
    `ui/src/modules/*`.
  - Product copy from **`ui/src/shared/constants/uiText.ts`** (every user-facing string lives here).
  - Design notes worth reading: `ui/Docs/ui-product-design-proposal.md`,
    `ui/Docs/workflow-builder-ui-audit.md`, `ui/Docs/workflow-ux-audit.md`.

> Imported reference copies of the key source files live under **`ui/`** in this project (read-only
> reference — not part of the deliverable). They are the receipts for every token and component here.

---

## 3 · Content fundamentals (how copy is written)

The product's voice is **terse, literal, operational** — it documents system state, it doesn't sell.

- **Person & address.** Mostly impersonal/system-descriptive ("Select a webhook row to load event
  detail.", "No failed runs — pipeline is healthy."). Occasional second person in guidance. **Never
  first person.** No "we", no marketing "you should".
- **Casing.**
  - **Title Case** for page titles, card titles, table headers, button labels, nav items
    ("Webhook History", "Processed Calls", "Browse Failed Runs").
  - **UPPERCASE** for two things only: status enum values shown literally (`RECEIVED`,
    `TASK_CREATED`, `SKIPPED`, `FAILED`) and small overline/eyebrow labels (kickers, field captions).
  - **sentence case** for body sentences, subtitles, empty/error states, tooltips.
- **Status is always a word.** Never a bare colored dot for meaning — `FAILED`, `TASK_CREATED`, etc.
  are spelled out. Colour reinforces, never replaces, the label.
- **Technical values are shown raw**, in mono, untranslated: event IDs (`evt_8f2c…`), payload hashes,
  ISO-ish timestamps, call IDs, JSON payloads. The UI does not prettify them.
- **Subtitles describe scope, not benefit.** "Review processed call outcomes and replay activity.",
  "Live view of active workflows, run health, and inbound webhook activity." — they tell the operator
  what the screen covers.
- **Empty / error / confirm states are plain and reassuring.** "No webhook events found for the
  selected filters.", "Something went wrong", "This will replay the selected FAILED call and re-run
  automation."
- **No emoji. No exclamation marks. No jokes.** Internal tool register throughout.
- **Phase honesty.** Copy openly marks unfinished scope: "(Phase 1)", "read-only … (Phase 2.1
  foundation)", "Detailed … metrics are on the roadmap." Don't invent finished-product polish the
  product itself disclaims.

**Examples to imitate**
> "From call outcome to follow-up task — automated, auditable, reliable." *(landing H1 — the one
> place the voice warms up)*
> "Webhook event operations workspace." *(subtitle — scope, not pitch)*
> "Replay is available only for FAILED calls." *(disabled-control tooltip — literal rule)*

---

## 4 · Visual foundations

A **light, calm, structural** system. Cool off-white canvas, white surfaces, hairline borders,
soft low shadows, generous spacing. Cyan/teal brand on a neutral slate base. The feel is
"instrument panel you can read at a glance," not "dashboard with 40 widgets."

### Color
- **Canvas & surfaces.** App background `#f7f9fc` (cool off-white), surfaces `#ffffff`, alt surface
  `#fbfdff` (table heads, hovers), borders a single hairline `#dde6f2`.
- **Ink.** Text `#0f172a` (slate-900), muted `#64748b` (slate-500). That's the whole text ramp.
- **Brand.** Cyan `#0f9fb8` (primary actions, active nav, logo), brand-soft `#e3f7fb` (selected
  rows, secondary buttons, active panel-nav pill), teal `#0d9488` as the gradient/accent partner.
- **Semantic status (locked, always color + label):** ok `#0f766e` on `#ccfbf1`; warn `#b45309` on
  `#fef3c7`; bad `#be123c` on `#ffe4e6`.
- **Live indicator** `#10b981` — used *only* as the streaming pulse-dot, never as a "Live" button.
- **Storyboard node accents** (workflow builder): trigger = cyan, wait = indigo `#6366f1`,
  branch = pink `#db2777`, side-effect = amber `#d97706`, compute = emerald `#059669`, neutral =
  slate. Each tone has fg / soft-bg / solid-dot variants. Fills are soft (~14% alpha) so the dot-grid
  canvas stays calm.
- **Imagery vibe.** Effectively no photography. Visual interest comes from **ambient brand blobs**
  (large, heavily blurred cyan radial gradients at ~35% opacity, slowly drifting) behind the
  dashboard, and a **dashed gradient SVG path** on the landing timeline. Cool-toned, airy, digital —
  never warm, never grainy.

### Typography
- **Manrope** for all UI; **JetBrains Mono** for all technical values. (See §Fonts in the Type cards.)
- Display/title weights are heavy (700–800) with tight tracking (down to `-0.03em` on the hero);
  body is 500 at 14px / 1.5; captions & kickers are 11px uppercase with positive tracking.
- Minimum sizes: 12px (`text-xs`) for table cells / captions, 14px default body.

### Shape, depth & spacing
- **Radii:** 8px (buttons, inputs, badges), 12px (cards, panels), 999px (status pills, the live pill).
- **Shadows are soft and low only.** Resting card `0 1px 3px rgba(15,23,42,.08)`; hover adds a second
  `0 4px 12px` layer and a `-1px` lift; glass milestone cards use `0 8px 22px rgba(15,23,42,.04)`.
  No hard or dark drop shadows anywhere.
- **Spacing** is a 4px scale (4/8/12/16/20). Cards pad 20px, filter bars 12px, page sections stack
  with 16px gaps. Lots of breathing room — low clutter is a rule, not a preference.
- **Borders** carry most of the structure: a single hairline `#dde6f2` separates regions, table rows,
  and cards. Dividers over shadows.

### Motion
- **Subtle, functional, fast.** Entrance: `fade + 6–10px rise`, 0.35–0.5s ease, lightly staggered
  (dashboard tiles, landing milestones). Hover/press: 0.12–0.15s color/opacity/`-1px` transitions —
  no bounce, no spring.
- **The one signature animation:** the live activity **tick-strip** under feed headers, where the
  current slot flashes amber on each heartbeat (`heartbeat-flash`, 900ms). New live rows briefly
  wash brand-soft, then fade to transparent.
- **Ambient drift:** dashboard background blobs translate ~20px over 40–50s. Decorative only.
- Everything respects `prefers-reduced-motion: reduce` (all of the above disable).
- **Hover states:** buttons darken via `color-mix(brand, black 12%)`; outline/ghost go to
  `surface-alt`; rows go to `surface-alt`; nav items to brand-soft. **Press/disabled:** opacity 0.5,
  pointer-events off. Focus is a 2px brand ring with a 2px surface offset (keyboard-accessible).

### Cards
White fill, 12px radius, single hairline border, `shadow-subtle` at rest. No colored left-border
accent rails as decoration. Card title 18/600, optional muted subtitle, 16px gap to body.

### Locked UX rules (from the product, do not violate)
1. Streaming state = a **corner live pulse-dot pill**, never a "Live" button.
2. **Pause is an icon control.** There is **no stream "clear"** action.
3. A compact **activity tick-strip** sits under live-feed headers.
4. Filters are **structured controls** (source / status / eventType / time window) with explicit
   **Apply + Reset** — never a freeform query box.
5. **Low-clutter spacing**; avoid dense telemetry blocks.
6. **Everything keyboard-accessible.**
7. **Inspector actions stay minimal and context-safe.**

---

## 5 · Iconography

- **Code-defined stroke SVGs.** The product has no icon font and no shared raster sprite for UI.
  Icons are small inline React SVGs (`ui/src/shared/ui/icons.tsx`) in a **Lucide-style** system:
  `viewBox 0 0 24 24`, `fill: none`, `stroke: currentColor`, **stroke-width 1.8**, round line caps
  and joins, rendered at **16px** (`h-4 w-4`) inside controls. Color is always inherited
  (`currentColor`) so an icon takes the text/brand color of its context.
- **The set in use:** Filter, Apply (check), Reset/Replay (circular-arrow), Next/Prev (chevrons),
  Pause (two bars), Resume (play triangle), Close (×). Recreated faithfully in
  `ui_kits/flux/icons.jsx`, plus a few additions (Logout, Search, Refresh) drawn in the
  same language.
- **The logo mark** is a flow-nodes glyph (one node branching to two) in the same stroke language —
  it represents the workflow engine. Drawn at stroke-width 2 inside a brand-cyan rounded square.
  The historical `ui/public/favicon.svg` is a leftover purple Vite-template bolt and is **not** the
  brand — ignore it.
- **No emoji. No unicode pictographs.** The only non-icon glyph used as UI is a right-arrow "→" in
  text links ("Browse Workflows →") and the breadcrumb "›".
- **Substitution guidance:** if you need an icon not in the set, pull it from **Lucide**
  (lucide.dev) — it matches the stroke weight and corner style exactly. Keep stroke-width 1.8 (or 2
  for the logo), round caps, 24-grid. Do **not** mix in filled or duotone icon families.

---

## 6 · Index — what's in this folder

| Path | What it is |
|---|---|
| `README.md` | This file. |
| `SKILL.md` | Agent-Skills front-matter so this system is usable as a downloadable skill. |
| `colors_and_type.css` | All design tokens (color, type, radius, spacing, shadow) **+** semantic type roles. The thing you `@import`. |
| `preview/` | The Design-System-tab specimen cards (one concept each). `_card.css` is their shared chrome. |
| `ui_kits/flux/` | High-fidelity, interactive recreation of the console — see its own README. Start at `index.html`. |
| `ui/` | Read-only reference copies of the real product source (tokens, shared UI, screens, copy). |

*(Slides were not provided, so no `slides/` folder exists.)*

---

## 7 · How to use this system

- **Always `@import 'colors_and_type.css'`** (after loading Manrope + JetBrains Mono from Google
  Fonts) and reference tokens via CSS variables — never hard-code a hex.
- Build screens from the **four-region shell**: icon Rail · contextual Panel · Content · Inspector.
- Lift components and copy patterns from `ui_kits/flux/` rather than reinventing them.
- Obey the seven locked UX rules in §4. They're what makes a screen read as *Flux*.
