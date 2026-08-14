# RD-015: Charting library — Apache ECharts behind an in-house wrapper

## Status
Accepted (2026-08-14). Executes the deferred library choice from
[RD-013](./RD-013-reporting-charts-custom-vs-library.md) at its designated trigger — the
first chart-heavy consumer (the Phase 2 reporting sankey). Supersedes RD-013's provisional
lean (visx). Scope: all analytical/interactive charts in `ui/`, now and future.

## Context
Reporting Phase 2f needs a sankey (source → agent → outcome) with click-to-drill,
tooltips, and a labels toggle — an analytical, interactive chart, which under RD-013's
going-forward rule is the trigger to adopt a library. The product owner also set a
direction on 2026-08-14: **more chart types are coming across the UI**, and the charting
plumbing must be centralized so any future screen adds a chart without adding a library
or re-solving theming.

RD-013's lean was visx. Re-evaluated at adoption (per its own "confirm then" clause)
against today's actual requirements:

| | visx (RD-013 lean) | Recharts | Nivo | **ECharts** |
|---|---|---|---|---|
| Sankey | assemble from d3-sankey yourself | half-supported side feature | good | built-in, with events |
| Catalog breadth | build every chart | narrow-mid | per-package family, uneven upkeep | widest single library |
| Cost per future chart | high (it's a kit, not charts) | low | mid | **low — declare an option** |
| Theming | fully ours (pro) | CSS-ish | fights tokens | central theme object built from our tokens |
| Maintenance | active | active | uneven | Apache project, very active |

visx optimizes for pixel-bespoke charts at high per-chart build cost — the right lean when
we expected few, design-primitive-adjacent charts. The requirement has inverted: many
standard charts, built cheaply, uniformly themed. That is ECharts' exact shape.

## Decision
1. **Adopt `echarts`** (Apache-2.0) as the app's one charting library.
2. **In-house wrapper, no `echarts-for-react`.** The third-party wrapper lags ECharts
   releases; ours is ~80 lines we control. All charts render through one component.
3. **Centralized charts layer — `ui/src/platform/charts/`:**
   - `chartTheme.ts` — builds the ECharts theme by resolving CSS design tokens at
     runtime (light + dark; re-resolves on theme toggle). The **only** place chart
     colors are defined; keeps the `lint:tokens` no-raw-hex gate meaningful.
   - `EChart.tsx` — the single wrapper: `option` in, event handlers in, init/resize/
     dispose managed. Uses modular `echarts/core` imports so only registered chart
     types are bundled.
4. **Option builders are pure feature-local functions** (`lib/toSankeyOption.ts` etc.),
   unit-tested, consuming the module's query-hook data. Traceability chain per chart:
   API → Zod contract → port/adapter → query hook → pure option builder → `<EChart>`.
   No data shaping inside components.
5. **RD-013's custom-vs-library split stands.** Decorative design-primitives (dashboard
   `AreaChart`, `Sparkbars`) remain custom token-driven SVG and are not migrated.
   Analytical/interactive charts use ECharts — no per-chart relitigating, and no second
   chart library.

## Consequences
- First runtime charting dependency; bundle cost bounded by modular imports (sankey +
  canvas renderer initially).
- The 2f sankey is ECharts-rendered: close to the design handoff but not pixel-identical
  to its hand-rolled prototype (accepted by the product owner 2026-08-14; UX adjusts).
- Any future chart anywhere in the UI is: register the chart type, write an option
  builder, render `<EChart>`. No new dependencies, no new theming work.
- The design-handoff README's "no chart library / d3-sankey layout-only" recommendation
  (written before this decision) is overridden by this record.
