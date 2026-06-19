---
name: flux-design
description: Use this skill to generate well-branded interfaces and assets for Flux — the internal operations console for a Follow Up Boss call-automation platform — either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the `README.md` file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create
static HTML files for the user to view. If working on production code, you can copy assets and read
the rules here to become an expert in designing with this brand.

If the user invokes this skill without any other guidance, ask them what they want to build or design,
ask some questions, and act as an expert designer who outputs HTML artifacts _or_ production code,
depending on the need.

## Where things are
- `README.md` — product context, content fundamentals, visual foundations, iconography, and an index.
- `colors_and_type.css` — all design tokens (color, type, radius, spacing, shadow) + semantic type
  roles + a proposed dark theme. `@import` this (after loading Manrope + JetBrains Mono from Google
  Fonts) and reference tokens via CSS variables — never hard-code a hex.
- `preview/` — specimen cards for every foundation + component.
- `ui_kits/flux/` — interactive, high-fidelity recreation of the console. Lift
  components and interaction patterns from here. Start at its `index.html` + `README.md`.
- `ui/` — read-only reference copies of the real product source (tokens, shared UI, screens, copy).

## Non-negotiables (the seven locked UX rules)
1. Streaming = a corner **live pulse-dot pill**, never a "Live" button.
2. **Pause is an icon control**; there is **no stream "clear"** action.
3. A compact **activity tick-strip** sits under live-feed headers.
4. Filters are **structured controls** (source / status / eventType / time window) with explicit
   **Apply + Reset** — never a freeform query box.
5. **Low-clutter spacing**; avoid dense telemetry blocks.
6. **Everything keyboard-accessible.**
7. **Inspector actions stay minimal and context-safe.**

## House style in one breath
Light, calm, structural ops console. Cool off-white canvas, white surfaces, single hairline borders,
soft low shadows, generous spacing. Cyan/teal brand on a slate base. Manrope for UI (heavy titles),
JetBrains Mono for every technical value. Status is always colour **+** an UPPERCASE label. Four-region
shell: icon Rail · contextual Panel · Content · Inspector. No emoji, no marketing voice.
