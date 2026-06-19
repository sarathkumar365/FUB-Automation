# Handoff: System Status Screens (Error · 404 · Session Disabled)

## Overview
Three full-page **system/utility status screens** for **Flux** (internal
operations console + event-driven workflow platform):

| Component | Route / trigger | Tone |
|---|---|---|
| `AppErrorFallback` | React error boundary **and** the router's `errorElement` (whole-screen) | error (rose) |
| `NotFoundPage` | `*` and `/admin-ui/*` — unknown URLs, standalone **or** inside the admin shell | brand (cyan) |
| `SessionDisabledPage` | `/admin-ui/session-disabled` — admin UI access gated server-side | warn (amber) |

They are designed as **one family** driven by a shared shell so they look and feel consistent.
The chosen visual direction is **"B + console strip"**: bare-on-gradient (no card), one
iconographic status glyph per page rendered large and faint behind the message (a "ghost glyph"),
plus a small mono **status strip** carrying the operational detail.

## About the design files
The files in `reference/` are **design references created in HTML + inline-Babel React** — a
prototype showing intended look and behavior, **not production code to copy verbatim**. The task
is to **recreate these screens in the real repo** (React 19 + TypeScript + Vite + Tailwind v4 +
class-variance-authority) using its established tokens and components — most importantly the
existing `tokens.css` CSS variables and the `AuthShell` / `LogoMark` already in the codebase.

- `reference/status-screens.jsx` — the full component reference (shell + 3 directions + 3 pages). **This is the source of truth for structure, copy, and measurements.**
- `reference/status.css` — the entrance-motion helper (a transform-only "rise").
- `reference/final.html` + `reference/final-canvas.jsx` — the gallery harness that lays the final pages out (light, dark, in-shell). Needs the project's UI-kit files to run; for reference only.

> Note: the prototype reproduces the kit's primitives (`Button`, `LogoMark`, `ShellLayout`,
> tokens) cosmetically. In the real repo, **import the actual ones** — do not re-implement them.

## Fidelity
**High-fidelity.** Final colors, typography, spacing, copy, and interaction are specified.
Recreate pixel-faithfully using the codebase's existing libraries and tokens. Every color is a
token (`var(--color-…)`) — **never hard-code a hex**; the dark theme remaps the same names
automatically (`<html data-theme="dark">`), so a correct token-only build themes for free.

---

## The shared shell — extract this first (`FullPageStatus`)
Per the repo's "extract shared UI, don't duplicate" rule, all three pages reuse one shell.

**`FullPageStatus`** renders:
1. **Ambient brand gradient** background (the family signature, lifted from `AuthShell`):
   ```
   radial-gradient(760px 360px at 50% -14%, color-mix(in srgb, var(--color-brand) 13%, transparent), transparent 64%),
   radial-gradient(560px 300px at 86% 114%, color-mix(in srgb, var(--color-brand-2) 10%, transparent), transparent 60%),
   var(--color-bg)
   ```
2. **Brand lockup** pinned top-center (absolute, `top: 40px`): `LogoMark size=34` + wordmark
   ("Flux" 15px/800/`-.01em` over "Operations console" 12px/600 `--color-text-muted`).
3. A **centered content slot** (`flex:1`, centered, padding `104px 40px 56px`).

Props:
- `inShell` (bool) — drops the gradient (→ transparent) and the lockup, padding `32px`. Used when the page renders inside the four-region admin shell content area; the shell already provides branding + `--color-bg`.
- `hideLockup` (bool) — hides the lockup only.

The page body is passed as children — one of the **direction renderers** below.

### Status tones (`TONES`)
Each page maps to a tone; **always color + a word, never a bare color/dot:**
| tone | `fg` | `bg` | eyebrow word |
|---|---|---|---|
| `bad` (error) | `var(--color-status-bad)` | `var(--color-status-bad-bg)` | `ERROR` |
| `warn` (session) | `var(--color-status-warn)` | `var(--color-status-warn-bg)` | `RESTRICTED` |
| `brand` (404) | `var(--color-brand)` | `var(--color-brand-soft)` | `404 · NOT FOUND` |

---

## Chosen layout — Direction B + console strip (`DirB` with `strip`)
Centered column on the gradient, with a **ghost glyph watermark** behind it.

**Watermark** (`aria-hidden`, absolute, centered `translate(-50%, -56%)`, `color: tone.fg`,
`opacity: 0.07`, `pointer-events:none`):
- Error → alert-triangle glyph, `size 372`, `stroke-width 1.1`
- Session → lock glyph, `size 372`, `stroke-width 1.1`
- 404 → the **numerals "404"** (not a glyph): `var(--font-ui)`, `font-weight 800`, `font-size 300px`, `letter-spacing -.04em`

**Foreground stack** (centered, `text-align:center`, `max-width 560`):
1. **Pill eyebrow** (`PillEyebrow`): inline-flex, `background: tone.bg`, `color: tone.fg`,
   `padding 6px 13px 6px 10px`, `border-radius 999`, `font 11px/800`, `letter-spacing .13em`,
   `text-transform: uppercase`, gap 8 — leads with the page glyph at `size 14, stroke 2`. `margin-bottom 20`.
2. **Title** (`h1`): `font-size 32`, `weight 800`, `letter-spacing -.025em`, `line-height 1.1`, `color var(--color-text)`.
3. **Body** (`p`): `margin-top 14`, `font-size 15`, `line-height 1.55`, `color var(--color-text-muted)`, `max-width 42ch`, `text-wrap: pretty`.
4. **Helper** (optional, session only): `margin-top 14`, `font-size 13.5`, muted; emphasizes "workspace administrator" with `color var(--color-text)` `weight 600`.
5. **Console status strip** (`StatusStrip`, `margin-top 20`): inline-flex, `padding 9px 14px`,
   `border 1px solid var(--color-border)`, `border-radius var(--radius-sm)` (8px),
   `background var(--color-surface)`, `box-shadow var(--shadow-subtle)`; a `7px` round dot in
   `tone.fg` + mono text (`var(--font-mono)`, `12px`, `color var(--color-text)`, `white-space:nowrap`).
6. **Actions** (`margin-top 26`, centered, gap 16) — see per-page below.
7. **Extra** — error only: the dev-only collapsible stack (`ErrorDetails`).

> The other explored directions (A "Quiet center" — medallion glyph over a centered stack; C
> "Console line" — horizontal glyph+text with the strip indented) live in `status-screens.jsx`
> as `DirA` / `DirC` if you ever want them. **Ship `DirB` with `strip`.**

---

## Screens

### 1. `AppErrorFallback` (tone: error)
- **Glyph:** alert-triangle (Lucide-style, stroke 1.8, round caps).
- **Eyebrow:** `ERROR`
- **Title:** `Something went wrong`
- **Body:** `The console hit an unexpected error. Reloading usually fixes it.`
- **Strip text (mono):** `BOUNDARY · render error caught · 500`
- **Actions:** primary **Reload** (brand `Button size="lg"`, calls `window.location.reload()`); secondary quiet **Go to dashboard** — must be a plain `<a href="/admin-ui">`, **not** a router link (this can render outside the router).
- **Dev-only `ErrorDetails`** (render only when `import.meta.env.DEV`): a collapsible, **collapsed by default**. Trigger row = chevron + `ERROR DETAILS` (11px/800 uppercase) + a small mono `dev only` chip. Expanded body = `<pre>` in `var(--font-mono)`, `11.5px`, `line-height 1.65`, `color var(--color-text-muted)`, `background var(--color-surface-alt)`, `1px var(--color-border)`, `border-radius var(--radius-sm)`, `max-height 168`, `white-space: pre-wrap`, scrollable. Renders `error.message` + `error.stack`.
- **Signature:** `AppErrorFallback({ error?: unknown })`. Dependency-light — **no router hooks** (it must work as both an error-boundary fallback and a router `errorElement`).

### 2. `NotFoundPage` (tone: brand)
- **Glyph:** compass. **Watermark = the numerals "404".**
- **Eyebrow:** `404 · NOT FOUND`
- **Title:** `Page not found`
- **Body:** `The page you are looking for does not exist or has moved.`
- **Strip text (mono):** `GET /admin-ui/leads/9f2a · 404` (use the real attempted path at runtime if available).
- **Actions:** primary **Go to dashboard** (router link to `/admin-ui`); secondary quiet **Back** with a leading chevron-left icon (e.g. `history.back()`).
- **Two placements:**
  - **Standalone** (unknown top-level URL): full `FullPageStatus` on the gradient (as above).
  - **In-shell** (`/admin-ui/*` miss): render inside the four-region shell's content area — pass `inShell` so the gradient + lockup drop and the message sits calmly centered in the content region (the prototype uses the calmer `DirA` body here; either body is acceptable in-shell).
- **Signature:** `NotFoundPage()` (+ internal `inShell` switch).

### 3. `SessionDisabledPage` (tone: warn)
- **Glyph:** lock.
- **Eyebrow:** `RESTRICTED`
- **Title:** `Admin access is disabled`
- **Body:** `Session guard is enabled and admin UI access is currently turned off. Enable admin UI access to restore the workspace.`
- **Helper line:** `No action needed here — ask a `**`workspace administrator`**` to re-enable admin UI access.`
- **Strip text (mono):** `GUARD · admin-ui access disabled`
- **Actions:** **no primary button** (don't invent one — there may be nothing the user can do). One quiet secondary link **Return to sign in** (legitimate). The helper line carries the real guidance.
- **Signature:** `SessionDisabledPage()`.

---

## Interactions & behavior
- **Entrance motion:** a subtle **transform-only rise** on mount — `translateY(14px) → 0` over `540ms`, `cubic-bezier(.2,.7,.3,1)`, driven via the Web Animations API (`element.animate`), **no `opacity` keyframe and no `forwards` fill**. Rationale: a throttled/background tab freezes any animation at frame 0; a transform-only rise can never strand content hidden (worst case it rests a few px low). Guard with `prefers-reduced-motion: reduce` (skip entirely). See `reference/status.css` + `useRise()` in `status-screens.jsx`.
- **ErrorDetails:** click toggles expand/collapse; chevron rotates. Collapsed by default.
- **Links** (`.aestatus-link`): muted, transition `color .12s`, hover → `var(--color-text)`.
- No loading/error sub-states; these *are* terminal states. No form validation.
- **Responsive:** single centered column; the `max-width`/`ch` caps handle narrow viewports. Keep tap targets ≥44px (the `Button size="lg"` is 40px tall — bump to ≥44 on touch if needed).

## State management
None beyond `ErrorDetails`' local open/closed boolean. `AppErrorFallback` receives the caught
`error` by prop; everything else is static. No data fetching.

## Design tokens (all already defined in the repo's `tokens.css`)
Use these names; do not introduce new hexes.
- **Surfaces:** `--color-bg` `#f7f9fc`, `--color-surface` `#ffffff`, `--color-surface-alt` `#fbfdff`, `--color-border` `#dde6f2`
- **Text:** `--color-text` `#0f172a`, `--color-text-muted` `#64748b`
- **Brand:** `--color-brand` `#0f9fb8`, `--color-brand-soft` `#e3f7fb`, `--color-brand-2` `#0d9488`
- **Status:** `--color-status-bad` `#be123c` / `--color-status-bad-bg` `#ffe4e6`; `--color-status-warn` `#b45309` / `--color-status-warn-bg` `#fef3c7`; `--color-status-ok` `#0f766e` / `--color-status-ok-bg` `#ccfbf1`
- **Radius:** `--radius-sm` 8 (controls/strip), `--radius-md` 12 (medallions in A/C), `--radius-pill` 999
- **Shadow:** `--shadow-subtle` (strip), `--shadow-float` (AuthShell card), `--shadow-hover`
- **Type:** `--font-ui` Manrope (heavy 700–800 titles, tight tracking), `--font-mono` JetBrains Mono (IDs/paths/stacks). Eyebrows 11px UPPERCASE tracked muted.
- **Dark theme:** same names remapped under `[data-theme="dark"]` — verify both modes.

## Assets
- **Brand mark:** existing `LogoMark` (flow-nodes monogram — one node branching to two, white glyph in a `--color-brand` rounded square). Import it; don't redraw.
- **Icons:** Lucide-style inline stroke SVGs (`stroke-width 1.8`, round caps, `currentColor`) — alert-triangle, compass, lock, chevron-left, chevron-down. Use your icon library's equivalents.
- No raster images.

## Files in this bundle
- `reference/status-screens.jsx` — shell + `TONES` + glyphs + `DirA`/`DirB`/`DirC` + `StatusStrip` + `ErrorDetails` + the three page wrappers (`AppErrorFallback`, `NotFoundPage`, `SessionDisabledPage`). **Primary reference.**
- `reference/status.css` — entrance-motion notes.
- `reference/final.html`, `reference/final-canvas.jsx` — gallery harness (reference only).

## In the live project (for context, if accessible)
- `status-screens/final.html` — the chosen family (light · dark · in-shell).
- `status-screens/index.html` — the full exploration (Directions A / B / C side by side).
