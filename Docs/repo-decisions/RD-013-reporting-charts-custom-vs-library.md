# RD-013: Reporting charts — custom SVG for design-primitives, a library for the analytical layer

## Status
Accepted (2026-06-19) — the **custom-vs-library split** is decided. The specific library
(**lean: visx**) is a recommendation, **confirmed at adoption** (the first chart-heavy
consumer). Scope: `ui/` reporting (and any future charting in the app). Implementation
begins with reporting-platform Phase 1 (dashboard charts, custom).

## Context
The reporting platform will need charts across its lifetime, but they fall into two very
different classes:
- The **operations dashboard** (Phase 1) needs two **decorative design-primitives** — a
  smooth Catmull-Rom area line with a gradient wash + hollow end-dot, and flexbox sparkbars.
  No axes, scales, tooltips, legends, or interactivity. The design handoff hand-builds them
  as token-driven SVG and explicitly says *"no heavy chart lib needed."*
- The later **analytical / ad-hoc (Vanna) layer** will need **real analytical charts** —
  axes, scales, multi-series, interactivity, possibly user-driven chart types — whose
  requirements are not visible today.

Facts: no chart library is currently a dependency; the codebase already hand-rolls SVG
(`icons.tsx`, `StoryboardViewer.tsx`, `LandingPage.tsx`) and has a strict token-driven
design system (Tailwind v4 + `tokens.css`). Choosing a library off the dashboard — an
atypical, decorative, interaction-less consumer — would bias the choice wrongly, the same
premature-commitment trap [RD-012](./RD-012-reporting-platform-architecture.md) avoids by
extracting from evidence.

## Decision

1. **Design-primitive charts → custom SVG, component-local.** Decorative charts with no
   axes/scales/interactivity (the dashboard throughput `AreaChart`, funnel `Sparkbars`) are
   hand-built token-driven SVG. **No library.** They live with their consumer (dashboard-
   local) until a second consumer wants them, then promote to `shared/ui` (rule of three).

2. **Analytical charts → a library, introduced at the first chart-heavy consumer.** Charts
   needing axes, scales, multi-series, or interactivity adopt a library — chosen **then**,
   from real requirements (the analytics / ad-hoc / Vanna layer), not now.

3. **Library lean = visx** (composable SVG primitives + D3 scales). It preserves the
   token-driven bespoke style and **composes with the custom charts** — custom-now is "SVG +
   hand-written scaling"; visx is "SVG + `@visx/scale`", so the Phase-1 charts port into it
   (~swap the scaling), not throwaway. **Avoid batteries-included declarative libraries**
   (Recharts, Tremor) — they impose a visual paradigm that fights the design system. *(Lib
   choice provisional; confirm at adoption, with a version/landscape sanity-check.)*

4. **Do not choose the library off the dashboard.** It's decorative and atypical; the
   library decision waits for a representative, chart-rich consumer. Evidence-first, per
   RD-012.

## Going-forward rule (per new chart)
- Decorative design-primitive (no axes/interactivity)? → **custom token-driven SVG**,
  component-local.
- Analytical (axes/scales/series/interactivity)? → **use the library**. If it's the *first*
  such chart, that's the trigger to adopt one — lean visx, confirm then.

## Consequences
- Dashboard charts are custom now — a ~2-component port, not a build; no new dependency.
- The library decision is deferred to where requirements are visible, and made from a
  representative consumer.
- Custom charts are **visx-compatible**, so deferring costs nothing and forecloses nothing;
  adopting a heavy declarative lib now *would* partially foreclose the better choice.
- "We can't hand-build every chart" is honoured: bespoke primitives (few) are custom;
  standard analytical charts (most, later) use the library.
