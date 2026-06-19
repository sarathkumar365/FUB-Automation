# Handoff: Flux — Console & Design System

## Overview
This bundle is the **Flux design system + a high-fidelity console UI kit**. It defines
the brand foundations (color, type, spacing, shadows), component patterns, and a working,
clickable recreation of the operator console (landing, auth, dashboard, live webhook feed,
processed calls, workflows + storyboard, persons, settings).

## About the design files
The files here are **design references authored in HTML/React-via-Babel** — prototypes that show the
intended look and behavior. They are **not** production code to copy verbatim. Your task is to
**recreate these designs in the target codebase's environment** using its established patterns and
libraries.

> The real product front-end is **React 19 + TypeScript + Vite + Tailwind v4** (Radix primitives,
> TanStack Query, `class-variance-authority`). Reference copies of its actual source live in `ui/`.
> If you're implementing into that app, prefer its existing components in `ui/src/shared/ui/*` and
> the tokens in `ui/src/styles/tokens.css` — this kit mirrors them.

## Fidelity
**High-fidelity.** Colors, typography, spacing, radii, shadows, and interactions are final. Recreate
the UI faithfully using the codebase's libraries. All visual values are tokenized in
`colors_and_type.css` — use those variables, never hard-code hex.

## Where everything is
| Path | What it is |
|---|---|
| `README.md` | **Start here.** Full product context, content fundamentals, visual foundations, iconography, locked UX rules. |
| `colors_and_type.css` | All design tokens (color, type, radius, spacing, shadow) + semantic type roles + dark theme. The thing to port to your theme layer. |
| `ui_kits/flux/` | The interactive console. `index.html` runs it; `README.md` documents every screen + component file. |
| `preview/` | Per-concept specimen cards (foundations + components) — visual reference for any single token/component. |
| `ui/` | Read-only copies of the real product source (tokens, shared UI, screens, copy) — the source of truth. |
| `SKILL.md` | Agent-Skills front-matter (see "Use as a Claude Code skill" below). |

## Screens
Each screen is documented in detail in **`ui_kits/flux/README.md`** (purpose, layout,
components, interactions). In brief:
- **Landing** — marketing pipeline story (swirling 5-milestone timeline).
- **Login / Signup** — centered card, JWT bearer framing.
- **Dashboard** — four-region shell; pipeline-health band (Ingested → Domain events → Workflow runs →
  Failed) + Recent Runs table + Needs-attention panel.
- **Webhooks** — live streaming feed: pulse-dot Live pill, icon pause, activity tick-strip, structured
  filters (Apply/Reset), detail inspector.
- **Processed Calls** — outcome table + replay-on-FAILED → confirm → toast, inspector.
- **Workflows** — definitions list → detail with Definition / Storyboard / Runs tabs (node graph).
- **Persons** — list → detail (identifiers, timestamps, live FUB refresh, unified activity timeline,
  per-stream recents, snapshot).
- **Settings** — section-nav config: Business hours · Feature flags · Connections (editable) · Managed
  webhooks, with a Save/Reset dirty bar.

## Locked UX rules (do not violate — see README §4)
1. Streaming = a corner **pulse-dot pill**, never a "Live" button.
2. **Pause is an icon control**; there is **no stream "clear"** action.
3. A compact **activity tick-strip** sits under live-feed headers.
4. Filters are **structured controls** (source / status / eventType / time window) with explicit
   **Apply + Reset** — never a freeform query box.
5. **Low-clutter spacing.**
6. **Everything keyboard-accessible.**
7. **Inspector actions stay minimal and context-safe.**

## Design tokens
Full set in `colors_and_type.css`. Headlines: brand `#0f9fb8` / soft `#e3f7fb` / teal `#0d9488`;
text `#0f172a` / muted `#64748b`; surfaces `#ffffff` on `#f7f9fc`, border `#dde6f2`; status ok
`#0f766e`/`#ccfbf1`, warn `#b45309`/`#fef3c7`, bad `#be123c`/`#ffe4e6`; live `#10b981`; radii
8/12/999; soft low shadows only. Type: **Manrope** (UI) + **JetBrains Mono** (IDs/hashes/timestamps/
payloads), both from Google Fonts.

## Assets / icons
No raster brand assets — the logo is the **flow-nodes monogram** (inline stroke SVG) and all icons are
Lucide-style inline stroke SVGs (24-grid, 1.8px, round caps). Recreate with **Lucide** in your stack
for a 1:1 match. (The repo's `favicon.svg` is a leftover template placeholder — ignore it.)

## Use as a Claude Code skill (optional)
This folder is Agent-Skills compatible. Drop it into your skills directory (e.g.
`~/.claude/skills/flux-design/` or a repo's `.claude/skills/`). `SKILL.md` lets Claude
Code load these guidelines on demand to design on-brand Flux UI.

## How to run the reference
Open `ui_kits/flux/index.html` in a browser (it loads React + Babel from CDN). Sign in
with the prefilled values; the rail switches screens; the moon/sun toggles dark mode.
