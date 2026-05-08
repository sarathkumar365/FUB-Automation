# Workflow Builder UI — Audit & Remediation Tracker

**Scope:** `ui/src/modules/workflows-builder/**`, `ui/src/modules/workflows/ui/WorkflowDetailPage/**`
**Baseline:** audit performed 2026-04-21 against `ui/AGENTS.md`, `ui/src/styles/tokens.css`, `ui/src/shared/ui/`, `ui/src/shared/constants/uiText.ts`.
**Branch:** `feature/workflow-builder-storyboard`

## Legend

- Status: `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` dropped (with reason)
- Severity: **C** critical (violates AGENTS.md) · **H** high · **M** medium · **L** low
- Each fix must include: lint pass, build pass, test pass, new/updated test where behavior changes.

---

## Preflight deps

| id | status | item |
| --- | --- | --- |
| D1 | [x] | Install `@radix-ui/react-tabs` and `@radix-ui/react-popover` (currently only `react-dialog` is present). Add shadcn-style wrappers under `shared/ui/`. Done in Slice 1.1 — `shared/ui/Tabs.tsx` + `shared/ui/Popover.tsx`. |

---

## CRITICAL — violates AGENTS.md

| id | status | sev | file:line | issue | fix |
| --- | --- | --- | --- | --- | --- |
| C1 | [x] | C | `WorkflowDetailPage/WorkflowTabs.tsx:40-54` | Raw `<button>` underline bar; no `role="tab"`/`tablist`, no arrow-key nav, no keyboard a11y. | Add `shared/ui/Tabs.tsx` wrapping `@radix-ui/react-tabs`; migrate; keep search-param sync in a controller. Add test. |
| C2 | [x] | C | `StoryboardTab/SceneInspectorPopover.tsx:99-207` | Hand-rolled `<div role="dialog">` with global `mousedown`/`keydown` listeners. No focus trap/restore, no `aria-modal`. Close `×` is raw `<button>` with inline styles. | Add `shared/ui/Popover.tsx` wrapping `@radix-ui/react-popover`; migrate. Replace close button with `Button variant="ghost" size="sm"` + icon from `shared/ui/icons.tsx`. |
| C3 | [x] | C | `StoryboardTab/SceneInspectorPopover.tsx:31-38` | `ACCENT_TONES` inlines 18 hex/rgba pairs (brand, amber, indigo, pink, emerald, slate). Duplicates `--color-brand`/`--color-brand-soft`. | Add `--color-accent-{trigger,side-effect,wait,branch,compute,neutral}-{fg,bg,dot}` tokens; delete map; read via CSS vars. |
| C4 | [x] | C | `WorkflowDetailPage/WorkflowHeaderStrip.tsx:87-97` | Version chip built with inline `rgba(15,159,184,0.12/0.22)`; ignores `shared/ui/badge.tsx` + `StatusBadge`. | Use `<Badge variant="default">v{n}</Badge>` or add a `brand-soft` badge variant. Drop inline rgba. |
| C5 | [x] | C | `workflows-builder/surfaces/storyboard/Scene.tsx:68-125` | Card rendered with inline `#ffffff`, `#0f172a`, `rgba(15,23,42,*)`. | Replace with `var(--color-surface)`, `var(--color-text)`, `var(--color-border)`. Move dim constants to `surfaces/storyboard/constants.ts`. |

---

## HIGH — clear rework

| id | status | sev | file:line | issue | fix |
| --- | --- | --- | --- | --- | --- |
| H1 | [x] | H | `StoryboardTab/SceneInspectorPopover.tsx:169-476` | ~300 lines of inline-styled subcomponents (`InspectorBody`, `ConfigRow`, `TransitionRow`). Does not use `shared/ui/JsonViewer` for JSON config values (AGENTS.md mandates it). | Extract to `StoryboardTab/inspector/{InspectorBody,ConfigRow,TransitionRow}.tsx`. Use `JsonViewer` for object/array values. Use `badge.tsx` for transition chips. |
| H2 | [x] | H | `StoryboardTab/SceneInspectorPopover.tsx:40-42,96` | Magic numbers `POPOVER_WIDTH=340`, `POPOVER_OFFSET=16`, `POPOVER_MAX_HEIGHT=480`, bare `8/16/28/18`. | Move to `StoryboardTab/constants.ts`. |
| H3 | [x] | H | `StoryboardTab/SceneInspectorPopover.tsx:407,416` | `value as never` casts through `isFanoutTransition` / `isTerminalTransition`. | Widen guards to `unknown`; drop casts. |
| H4 | [x] | H | `StoryboardTab/SceneInspectorPopover.tsx:73-89` | Outside-click uses global `document.mousedown`; brittle with portals. | Resolved via C2 (Radix Popover handles it). |
| H5 | [x] | H | `StoryboardTab/index.tsx:117-124` | Canvas dot-grid inline `backgroundImage: radial-gradient(rgba(15,23,42,0.05) …)`. | Move to CSS class (`.dot-canvas`) sourcing a token. |
| H6 | [x] | H | `WorkflowDetailPage/WorkflowTabs.tsx` | No test exists. AGENTS.md: every new change requires tests. | Add test: URL-param roundtrip + a11y roles + keyboard nav. |
| H7 | [x] | H | `workflows-builder/surfaces/storyboard/Scene.tsx:52` | `@ts-expect-error — xmlns attribute is valid on divs inside foreignObject` suppression on every render. | Done in Slice 1.3 — dropped the `xmlns` attribute (React handles foreignObject children correctly). |

---

## MEDIUM — improvement opportunities

| id | status | sev | file:line | issue | fix |
| --- | --- | --- | --- | --- | --- |
| M1 | [x] | M | `Scene.tsx:28-31` + `SceneInspectorPopover.tsx:31-38` | Accent/color constants duplicated. | Single `surfaces/storyboard/tokens.ts` or CSS vars (covered by C3). |
| M2 | [-] | M | `ExitEdge.tsx:62-73`, `TerminalPill.tsx:67-83` | SVG chip rect+text pattern duplicated with different literals (`#ffffff` vs `rgba(100,116,139,0.08)`, `#334155` vs `#475569`). | Dropped: the actual color drift is already fixed by token unification (Slice 1.2–1.3); the two chips differ in shape (rx 8 vs 11), text anchoring (centered vs side-anchored), and positioning (transform vs explicit x), so a shared primitive would add abstraction without meaningful payoff. |
| M3 | [x] | M | `StoryboardViewer.tsx:62-72` + `StoryboardTab/index.tsx:103-104` | `Math.max(viewport.width, 320)` / `viewport.height+24, 240` duplicated. | Expose `canvasSize` from `useStoryboardModel`. |
| M4 | [x] | M | `chipMetrics.ts`, `layoutEngine.ts`, `viewport.ts` | Geometry constants scattered, no barrel. | Create `surfaces/storyboard/constants.ts` re-exporting canonical values. |
| M5 | [x] | M | `ValidationStrip.tsx:110-151` | `ToneSet` builder rebuilt on every render; collapsed list has no severity aria. | Module-level frozen map keyed by `state.mode`; add `aria-label` per severity. Done Slice 2 — `TONE_BY_MODE` frozen record + `SEVERITY_BY_MODE` with role/aria-live/label per mode. |
| M6 | [x] | M | `RunsTab/index.tsx:155-189` | Filter row hand-built (`<label><Select/></label>` + buttons) instead of `shared/ui/FilterBar`. Comment says "FilterBar border removed". | Extend `FilterBar` with `bordered={false}` variant and consume. Done Slice 2 — new `shared/ui/recipes/FilterBar.tsx` (no pre-existing FilterBar; created with `bordered?: boolean`); RunsTab consumes with `bordered={false}`. |
| M7 | [x] | M | `WorkflowHeaderStrip.tsx:126-130` | `readTriggerType` duplicates logic in `graphAdapters.ts:66` + `cardFormatters`. | Single `lib/readTriggerType.ts` shared by adapters + header. Done Slice 2 — `workflows/lib/readTriggerType.ts`; all three call sites migrated (`WorkflowHeaderStrip`, `graphAdapters.ts:66`, `cardFormatters.formatTrigger`). |
| M8 | [-] | M | `WorkflowDetailPage/index.tsx:147` | React `key` hack `${key}-${version}-${open/closed}` on `WorkflowEditModal` to force remount. | Move state reset inside modal via `useEffect` on open transition. **Dropped Slice 2** — React 19's `react-hooks/set-state-in-effect` rule (now enabled in our lint config) correctly flags that pattern; React docs actually recommend the `key`-remount for "reset state on prop change". Kept the key and added an inline JSDoc justifying it + noting the rejected alternative. |
| M9 | [x] | M | `ExitEdge.tsx:64` | Label rect literal `#ffffff` — breaks dark theme. | `style={{fill: 'var(--color-surface)'}}`. |
| M10 | [x] | M | `Scene.tsx` (selected state) | Selected scene card shows a harsh green-ish outline that clashes with the storyboard palette. | Replace the selection ring with a softer treatment — token-driven (e.g. `--color-storyboard-card-ring-selected`), likely an inset + subtle halo matching the accent of the scene's category rather than a flat green border. Confirm final look in Slice 3 visual pass (needs user sign-off on ring color + width). |
| M11 | [x] | M | `TerminalPill.tsx` | All terminal pills look identical regardless of kind (success / failure / skipped / noop / custom resultCode). Users can't distinguish outcomes at a glance. | Give each terminal kind a small visual tell — e.g. leading glyph (✓ / ✕ / ◻ / ↷), a token-driven color per kind, or both. Must stay readable against the dot-grid and not clash with accent palette. Needs user sign-off on glyph set + color mapping before implementation. |

---

## LOW — nits

| id | status | sev | file:line | issue | fix |
| --- | --- | --- | --- | --- | --- |
| L1 | [x] | L | `Scene.tsx:46` | Trigger label special-cased inline (`isTrigger ? 'trigger' …`). | Move to `cardFormatters`. Done Slice 2 — `formatSceneHeader(stepType, isEntry)` owns the `__trigger__` branch; Scene.tsx consumes the pill label + tooltip. |
| L2 | [x] | L | `StoryboardViewer.tsx:60` | `const handleSelect = onSelectScene ?? (() => {})`. | Accept `undefined` on `Scene.onSelect`. Done Slice 2 — `Scene.onSelect?` is optional; StoryboardViewer forwards `onSelectScene` directly. |
| L3 | [x] | L | `SceneInspectorPopover.tsx:459` | Literal `"on "` prefix not in `uiText`. | Add to `uiText`. Done Slice 2 — `uiText.workflows.sceneInspectorTransitionOn`. |
| L4 | [x] | L | `WorkflowBuilderPage.tsx:58,76-79,98,113` | Inline strings: "Builder info", "New workflow", "Start with a single entry step.", "Invalid graph", "Storyboard". | Route through `uiText`. Done Slice 2 — 10 new `uiText.workflows.builder*` keys; all inline copy in `WorkflowBuilderPage.tsx` now sourced from `uiText`. |
| L5 | [x] | L | `WorkflowTabs.tsx:34` | `aria-label="Workflow detail sections"` not in `uiText`. | Done in Slice 1.1 — routed via `uiText.workflows.detailTabsAriaLabel`. |
| L6 | [x] | L | `SceneInspectorPopover.tsx:206` | `×` character instead of icon. | Done in Slice 1.1 — replaced with `CloseIcon` from `shared/ui/icons.tsx`. |
| L7 | [x] | L | `ValidationStrip.tsx:23` | `showIssues` default `true` — not persisted across route changes. | Persist in URL or session if product wants. Track only. Done Slice 2 (track-only) — inline decision note on the useState line: session-local by design until real user signal justifies sessionStorage + the SSR/hydration cost. |

---

## Execution plan

Execute in order; each slice: code → new test → lint/build/test → commit.

### Implementation phases (2026-04-21)

Slice 3 + Slice 4 are being rolled out as four phases of small commits. Gate per commit: `npm run lint && npm run build && npm run test` all green.

| phase | status | tasks | commit shape |
| --- | --- | --- | --- |
| **A** — Slice 3 structural | [x] | S3-A, S3-B, S3-C | 1 commit: barrel + READMEs |
| **B** — Slice 3 recipes | [x] | S3-I, S3-D, S3-G, S3-E, S3-F, S3-H (each + its test from S3-J) | 4 commits: Skeleton+Section / CopyableValue / FieldRow+KeyValueList / DefinitionCard |
| **C** — Slice 4 popover v2 | [x] | S4-E, S4-D, S4-C, S4-A+S4-F, S4-B, S4-G, S4-H (optional — skipped) | 5 commits: kind+clamp / envelope / ConfigRow / TransitionRow / tests |
| **D** — Slice 6 storyboard polish | [x] | S6-A, S6-B, S6-C | 4 commits: decisions doc / ring token / terminal kinds / tests |
| **E** — remaining tracks (pick any order) | [x] | Slice 2 **complete 2026-04-21** (M8 dropped — React 19 lint forbids the planned fix); Slice 5 **dropped 2026-04-21** (D5.1 = c). | 7 commits for Slice 2 (L1-L3 / L4 / M5 / M6 / M7 / M7-followup / M8-docs / L7) |

Rules:
- Phase B recipes are additive only — no existing surface consumes them yet.
- Phase C is the first behavior change (the popover rebuild).
- Tracker item `[ ]` → `[x]` in the same commit that completes it.

### Slice 1 — Top 5 prioritized actions — **COMPLETE** (2026-04-21)

**Status summary:**
- Resolved: D1, C1, C2, C3, C4, C5, H1, H2, H3, H4, H5, H6, H7, M1, M3, M4, M9, L5, L6.
- Dropped: M2 (color drift solved by tokens; structural dedup offers no payoff given shape/anchor differences between the two chips).
- Tests: 228/228 passing (+1 new structured-config assertion, +1 new workflow-tabs test).
- Lint: clean. Build: green.

Files added:
- `ui/src/shared/ui/Tabs.tsx`, `ui/src/shared/ui/Popover.tsx` — shadcn-style Radix wrappers.
- `ui/src/modules/workflows-builder/surfaces/storyboard/accentTokens.ts` — FormatterAccent → CSS var lookup.
- `ui/src/modules/workflows-builder/surfaces/storyboard/constants.ts` — geometry barrel + canvas floors.
- `ui/src/modules/workflows/ui/WorkflowDetailPage/StoryboardTab/constants.ts` — popover dimension constants.
- `ui/src/modules/workflows/ui/WorkflowDetailPage/StoryboardTab/inspector/{InspectorBody,ConfigRow,TransitionRow,formatConfigValue,transitions}.ts(x)`.
- `ui/src/test/workflow-tabs.test.tsx` — Radix-tab URL-param + a11y roundtrip test.

Files modified (highlights):
- `ui/src/styles/tokens.css` — added `--color-accent-*`, storyboard color/shadow vars, arrow marker, `.storyboard-canvas` dot-grid.
- `ui/src/modules/workflows-builder/surfaces/storyboard/{Scene,ExitEdge,TerminalPill,StoryboardViewer,useStoryboardModel}.tsx` — all inline hex/rgba purged; canvasSize exposed from hook.
- `ui/src/modules/workflows-builder/state/runtimeContract.ts` — widened `isFanoutTransition` / `isTerminalTransition` to `unknown`, dropped `as never`.
- `ui/src/modules/workflows/ui/WorkflowDetailPage/WorkflowHeaderStrip.tsx` — version chip → `Badge variant="default"`.
- `ui/src/modules/workflows/ui/WorkflowDetailPage/StoryboardTab/{index,SceneInspectorPopover}.tsx` — dot-grid via CSS class; popover is now a shadcn-style Radix Popover; body extracted; JsonViewer used for structured config.

### Slice 1 — Top 5 prioritized actions (this pass)

1. **D1 + C1 + C2 + H4 + L6 + H6 + L5** — install radix tabs + popover; `shared/ui/Tabs.tsx` + `shared/ui/Popover.tsx` wrappers; migrate `WorkflowTabs` and `SceneInspectorPopover`; add Tabs test; route aria-label + `"on "` prefix through `uiText`.
2. **C3 + M1** — accent palette → CSS tokens. Delete `ACCENT_TONES`. Update `Scene.tsx` + inspector to read tokens.
3. **C5 + M9 + H5** — purge inline hex/rgba in `Scene.tsx`, `ExitEdge.tsx`, `TerminalPill.tsx`, `StoryboardViewer.tsx`, `StoryboardTab/index.tsx` dot-grid → token vars / CSS classes.
4. **H1 + H2 + H3** — extract popover internals to `StoryboardTab/inspector/*.tsx`; reuse `JsonViewer`; magic numbers → `StoryboardTab/constants.ts`; drop `as never`.
5. **C4 + M4 + M2 + M3** — header version chip → Badge; `surfaces/storyboard/constants.ts` barrel; `<StoryboardChip>` SVG primitive; expose `canvasSize`.

### Slice 2 — remaining mediums/lows

6. **M5, M6, M7, M8** — validation strip memo/a11y; RunsTab → FilterBar variant; `readTriggerType` consolidation; modal reset-on-open.
7. **L1, L2, L3, L4, L7** — cleanup pass; route strings; drop `handleSelect` fallback; trigger label formatter; validation issues persistence decision.

### Slice 3 — shared UI recipes foundation (new, from 2026-04-21 review)

Motivation: we have **primitives** (`Button`, `Badge`, `Tabs`, `Popover`, `JsonViewer`) and **features** (`WorkflowHeaderStrip`, `SceneInspectorPopover`), but nothing in between. Every feature that needs a "label-above-value row with a copy button" or a "section with an uppercase caption" inlines the markup. That is why the popover body and the header strip both had scattered inline styles. This slice creates the missing recipes layer so future features compose instead of copy.

> **⚠️ Visual design of each recipe needs user sign-off before implementing.**
>
> The structural decisions (where recipes live, barrel file, README) do not need product input. The *visual + API* decisions for each recipe (what a `FieldRow` looks like, whether `CopyableValue` shows the button always or on hover, etc.) do. When this slice is reached, walk the user through the decisions below per-recipe before writing the component. Some of these will be informed by Slice 4 decisions — consider doing Slice 4 decisions first, then Slice 3 implementation, then Slice 4 implementation.

#### Decisions required (collaborate with user before implementing each recipe)

| # | Question | Options to present | Decision |
| --- | --- | --- | --- |
| D3.1 | `Section` recipe — caption treatment? | (a) uppercase + letter-spacing (current style), (b) sentence-case bold, (c) small icon + label, (d) user picks per-use via prop | **(a) — locked 2026-04-21** |
| D3.2 | `FieldRow` recipe — default layout? | (a) `auto` (infer from value type), (b) always stacked, (c) always inline, (d) caller must specify | **(a) — locked 2026-04-21.** Heuristic: short scalars (booleans, numbers, enums, ≤ threshold chars) render inline; long strings / multi-line / structured render stacked. Exact threshold settled in D4.3. |
| D3.3 | `CopyableValue` recipe — copy button visibility? | (a) always visible, (b) visible on hover only, (c) visible after click on the value, (d) caller picks | **(b) — locked 2026-04-21.** Fallback: always-visible under `@media (hover: none)` for touch; focus-visible for keyboard. |
| D3.4 | `CopyableValue` recipe — feedback on copy? | (a) inline "Copied" swap for 1.5s (like `JsonViewer` today), (b) toast via `useNotify`, (c) icon change only | **(a) — locked 2026-04-21** |
| D3.5 | `DefinitionCard` recipe — header slot? | (a) just a title, (b) title + optional action button, (c) title + badge + action, (d) fully slot-based | **(c) — locked 2026-04-21.** Badge legibility handled by D3.5a. |
| D3.5a | If D3.5 is (c): how does the user know what each badge means? | (a) badge renders `displayName` (e.g. "Create Task") not id, (b) badge shows short label + native `title` tooltip with description, (c) both — human label + hover tooltip showing id + description from step-type catalog, (d) add a small "?" next to the badge opening a legend popover | **(c) — locked 2026-04-21.** Pull `displayName` + `description` from the step-type / trigger-type catalog endpoints (backend B1). Until catalog lands, fall back to id + no tooltip. |
| D3.6 | `Skeleton` recipe — shapes supported? | (a) just line + block, (b) line + block + circle (avatar), (c) full set (line, block, circle, table-row, card), (d) punt — add shapes as needed | **(d) — locked 2026-04-21.** Start with `line` + `block`; add shapes only when a real screen needs one. |
| D3.7 | `KeyValueList` — default layout? | (a) 2-col grid, (b) stacked, (c) responsive (grid on wide, stacked on narrow), (d) caller picks | **(a) with `stacked` / `dense` override prop — locked 2026-04-21.** Popover + other narrow surfaces opt into stacked; wide surfaces keep the grid. |

After decisions are recorded, implement per the task table below. Items S3-A, S3-B, S3-C (barrel + READMEs) can proceed without decisions.

| id | status | item |
| --- | --- | --- |
| S3-A | [x] | **Phase A — done 2026-04-21.** `ui/src/shared/ui/index.ts` barrel re-exporting primitives (recipe section commented out until Phase B). |
| S3-B | [x] | **Phase A — done 2026-04-21.** `ui/src/shared/ui/README.md` covering the three tiers, token rule, barrel guidance, strings rule, tests policy. |
| S3-C | [x] | **Phase A — done 2026-04-21.** `ui/src/styles/tokens.README.md` covering all token categories, add-vs-reuse criteria, naming convention, dark-mode forward note. |
| S3-D | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/Section.tsx` per **D3.1-a**. |
| S3-E | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/FieldRow.tsx` per **D3.2-a** + **D4.3-b**; exports `FIELD_ROW_LONG_THRESHOLD`. |
| S3-F | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/KeyValueList.tsx` per **D3.7-a**; `grid` default, `stacked` variant + `stacked` shorthand prop. |
| S3-G | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/CopyableValue.tsx` per **D3.3-b** + **D3.4-a**. |
| S3-H | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/DefinitionCard.tsx` per **D3.5-c**; catalog-driven badge rule (**D3.5a-c**) documented in the JSDoc, applied at call sites when Slice 4 rebuilds the inspector header. |
| S3-I | [x] | **Phase B — done 2026-04-21.** `shared/ui/recipes/Skeleton.tsx` per **D3.6-d** (line + block only). |
| S3-J | [x] | **Phase B — done 2026-04-21.** 34 recipe tests across 6 files under `ui/src/test/shared-ui-recipes-*.test.tsx`. |

### Slice 4 — popover layout v2 (long-text + detail UX)

Motivation: in the current popover, long `transitions` and long config values (`context`, URLs, JSONata expressions) break mid-word due to the fixed 340px width + 2-col `110px \| 1fr` grid + `overflow-wrap: anywhere`. Result: visually broken rows, not a layout. Depends on Slice 3 recipes.

> **⚠️ DO NOT start implementation until UX decisions are made with the user.**
>
> When this slice is reached, the assistant MUST pause and walk the user through each decision below before writing code. Present the options with trade-offs; do not pick unilaterally. Once decisions are recorded (fill in the "Decision" columns below), proceed with implementation.

#### Decisions required (collaborate with user before implementing)

| # | Question | Options to present | Decision |
| --- | --- | --- | --- |
| D4.1 | How should **short scalar** config values render (booleans, numbers, short strings)? | (a) inline 2-col grid like today, (b) always stacked label-above-value, (c) pill/badge on the right side of a stacked row | **(a) — locked 2026-04-21** |
| D4.2 | How should **templating strings** (containing `$`, `{{`, `}}`) render? | (a) monospace chip with horizontal scroll, (b) `CopyableValue` stacked block, (c) syntax-highlighted code block, (d) collapsed by default with "Show" toggle | **(b) — locked 2026-04-21.** Stacked monospace block with `CopyableValue` wrapper. |
| D4.3 | What is the **length threshold** for "short" vs "long" strings? | (a) 40 chars, (b) 60 chars, (c) container-width-based (measure), (d) no threshold — always stacked | **(b) 60 chars — locked 2026-04-21.** Threshold lives in `StoryboardTab/inspector/constants.ts` (or sibling) so we can tune without code review elsewhere. |
| D4.4 | How should **long plain strings** render? | (a) stacked with `pre-wrap` + "Show more" at N lines, (b) full stacked always, (c) collapsed preview + modal on expand, (d) truncated + tooltip | **(a) — locked 2026-04-21.** Line count set in D4.10. |
| D4.5 | Should **URL-shaped** strings get special treatment? | (a) yes — link styling + copy button, (b) no — treat as normal string, (c) yes but behind a "copy URL" affordance only | **(c) — locked 2026-04-21.** No navigation affordance; copy-only. URL-shape detection helper in `formatConfigValue.ts`. |
| D4.6 | How should **transitions** render for long resultCodes + long targets? | (a) card-per-transition, stacked caption above target, (b) wider horizontal rows with better wrapping rules, (c) table with fixed columns + ellipsis + tooltip on overflow, (d) disclosure — collapsed summary, expand to see target | **(a) — locked 2026-04-21.** Each transition = card. Top line: resultCode chip (color-coded once Slice 6 D6.3 resolves). Bottom line: `→ target_scene_id` wrapping cleanly on its own full-width row. |
| D4.7 | Popover **default width**? | (a) keep 340px, (b) widen to 380px, (c) widen to 420px, (d) fully responsive min/max envelope | **(c) 420px — locked 2026-04-21.** Update `POPOVER_WIDTH` in `StoryboardTab/constants.ts`; verify side-picker math against wider footprint. |
| D4.8 | Popover **max height** behavior for tall content? | (a) keep inner scroll (today), (b) remove max-height and let it grow, (c) split tall content across tabs inside the popover | **(a) with 560px max-height — locked 2026-04-21.** Scale `POPOVER_MAX_HEIGHT` from 480 → 560 to match the wider width. |
| D4.9 | **Copy action** — on which field types? | (a) all long strings, (b) only URL + templating strings, (c) every field has a copy affordance, (d) none (keep clean) | **(b) — locked 2026-04-21.** Only URLs (D4.5) and templating strings (D4.2) carry `CopyableValue`. Plain long strings and short scalars render without copy. |
| D4.10 | **"Show more" disclosure** — at how many lines? | (a) 3, (b) 4, (c) 6, (d) no cap — always show full | **(b) 4 lines — locked 2026-04-21.** |

#### Implementation tasks (decisions locked 2026-04-21)

| id | status | item |
| --- | --- | --- |
| S4-A | [x] | **Phase C.** Rebuild `StoryboardTab/inspector/ConfigRow.tsx` on top of `FieldRow` recipe. Rules: **D4.1-a** short scalars stay inline; **D4.2-b** templating strings (contain `$` / `{{` / `}}`) render as stacked monospace `CopyableValue`; **D4.3-b** 60-char threshold; **D4.4-a** long plain strings stack with `pre-wrap`; **D4.5-c** URL-shape values render stacked with copy-only affordance (no anchor/nav). |
| S4-B | [x] | **Phase C.** Rebuild `StoryboardTab/inspector/TransitionRow.tsx` per **D4.6-a**: card-per-transition. Top line = resultCode chip (color/glyph per Slice 6 **D6.3** once locked — fall back to current neutral chip in the meantime). Bottom line = full-width `→ target_scene_id` row that wraps cleanly in monospace. |
| S4-C | [x] | **Phase C.** Update `StoryboardTab/constants.ts`: **D4.7-c** `POPOVER_WIDTH` 340 → 420; **D4.8-a** `POPOVER_MAX_HEIGHT` 480 → 560. Re-verify side-picker math in `SceneInspectorPopover` against the wider footprint; adjust `POPOVER_EDGE_PADDING` if the picker flips sides too eagerly. |
| S4-D | [x] | **Phase C.** Build a small `useClampLines({ maxLines: 4 })` helper (or inline disclosure in `FieldRow`) per **D4.10-b**. Renders full value if it fits in 4 lines; otherwise clamps + exposes "Show more" / "Show less" toggle. |
| S4-E | [x] | **Phase C.** Add value-kind detection helper in `StoryboardTab/inspector/formatConfigValue.ts`. Returns a discriminated `{ kind: 'url' \| 'templating' \| 'plain' \| 'scalar' \| 'structured', value }` consumed by `ConfigRow`. URL detection covers `http` / `https`; does not special-case `mailto` / `tel`. |
| S4-F | [x] | **Phase C.** Apply copy affordance wrapping per **D4.9-b**: `CopyableValue` only on `url` and `templating` kinds. Plain long strings and short scalars render without copy. |
| S4-G | [x] | **Phase C.** Update `ui/src/test/workflow-scene-inspector-popover.test.tsx`: (1) short scalar → inline, (2) 80-char plain string → stacked with Show more, (3) templating string → monospace + copy button, (4) URL → stacked + copy button, no `<a>`, (5) card-per-transition structure with long resultCode/target, (6) width/max-height tokens updated. |
| S4-H | [ ] | **Phase C (optional).** Visual regression / screenshot sanity for a worst-case graph with long URLs + long transitions. |

### Slice 5 — scene inspector in right rail — **DROPPED** (2026-04-21, D5.1 = c)

Motivation: the shell already exposes an **Inspector region** (`src/app/shell/...`) as a first-class layout zone. Today it hosts `WorkflowVersionList` on the workflow detail page. Surfacing scene detail there would give users more real estate and match the "selection drives right rail" pattern seen in Figma / Notion / Linear — but it's a product call whether this is worth the extra layout complexity on top of a polished popover.

> **⚠️ DO NOT start implementation until UX decisions are made with the user.**
>
> This slice is optional and reshapes the shell. Walk through the decisions below before writing any code; several of them may collapse the slice to "skip".

#### Decisions required (collaborate with user before implementing)

| # | Question | Options to present | Decision |
| --- | --- | --- | --- |
| D5.1 | Is scene-detail-in-right-rail worth doing at all, given a polished Slice 4 popover? | (a) yes — right rail becomes the authoritative view, (b) yes — but only as an alternative, user toggles, (c) no — skip this slice entirely | **(c) — locked 2026-04-21.** Slice 4 popover v2 solved the long-text pain that motivated Slice 5; right-rail would be ergonomic-only with significant shell complexity. Revisit only if persistent-scene-detail becomes a real user ask. |
| D5.2 | If (a) or (b): how should the Inspector region handle both **Versions** and **Scene** content? | (a) tabbed (Versions / Scene), (b) swap — scene replaces versions while selected, versions return on deselect, (c) stacked — both visible, versions compressed, (d) accordion sections | _N/A — D5.1 = (c)_ |
| D5.3 | Should the **floating popover** coexist with the right-rail inspector? | (a) yes — popover = quick glance, rail = authoritative, (b) popover hides when rail shows the same scene, (c) popover removed entirely — rail is the only detail view | _N/A — D5.1 = (c)_ |
| D5.4 | What is the **entry affordance** for opening the right-rail inspector? | (a) single-click a scene (same as popover today), (b) double-click a scene, (c) dedicated "expand" button on the popover, (d) keyboard shortcut | _N/A — D5.1 = (c)_ |
| D5.5 | Should scene selection **persist in the URL** (so links are shareable)? | (a) yes — add `?scene=<id>` search param, (b) no — selection is transient, (c) yes but only when the right rail is open | _N/A — D5.1 = (c)_ |
| D5.6 | Does this pattern become **canonical** for all workflow detail surfaces going forward? | (a) yes — update `AGENTS.md` "UX decisions" to lock it, (b) no — scene inspector only, other surfaces stay feature-specific | _N/A — D5.1 = (c)_ |

After decisions are filled in, update the task table below (or mark the slice dropped) before implementing.

#### Implementation tasks (refine after decisions are recorded)

| id | status | item |
| --- | --- | --- |
| S5-A | [-] | Dropped — D5.1 = (c). |
| S5-B | [-] | Dropped — D5.1 = (c). |
| S5-C | [-] | Dropped — D5.1 = (c). |
| S5-D | [-] | Dropped — D5.1 = (c). |
| S5-E | [-] | Dropped — D5.1 = (c). |
| S5-F | [-] | Dropped — D5.1 = (c). |
| S5-G | [-] | Dropped — D5.1 = (c). |

### Slice 6 — storyboard visual polish (from 2026-04-21 review)

Motivation: two cosmetic regressions surfaced during Slice 3 decision walkthrough.
- **M10** — selected-scene outline reads as a harsh green, clashing with the storyboard palette.
- **M11** — every terminal pill looks identical regardless of kind (success / failure / skipped / noop / custom); no glanceable differentiation.

Standalone slice — does not depend on Slice 3/4/5.

#### Decisions required (collaborate with user before implementing)

| id | question | options | status |
| --- | --- | --- | --- |
| D6.1 | **Selected-scene ring** treatment? | (a) keep ring but swap to a calm neutral token (e.g. slate-500), (b) ring color matches the scene's category accent (trigger/side-effect/etc.), (c) drop the ring entirely; indicate selection via subtle lift + shadow intensification only, (d) double treatment — soft halo outside + 1px inner border using the accent | **(a)** — new `--color-storyboard-card-ring-selected` neutral token replaces `--color-brand` for selected border |
| D6.2 | **Terminal pill differentiation** — visual strategy? | (a) leading glyph only (✓ / ✕ / ◻ / ↷) + existing neutral pill, (b) color-per-kind via tokens + existing text, (c) both glyph and color, (d) shape-per-kind (pill / squared / notched) | **(c)** — glyph + colour (accessible across colour-blind + squint cases) |
| D6.3 | If D6.2 involves color — **mapping**? | (a) success=green, failure=red, skipped=amber, noop=slate, custom=accent-neutral; tokens added to `tokens.css`, (b) stay monochrome (accent-neutral) and rely on glyph alone, (c) user-supplied palette | **(a)** — semantic tokens; add `--color-storyboard-terminal-{success\|failure\|skipped\|noop}-{bg,border,text}` to `tokens.css` |
| D6.4 | Which **terminal kinds** are first-class? | (a) `success` / `failure` / `skipped` / `noop` only, (b) add `retryable` / `timed_out`, (c) whatever result codes the workflow's step types declare (dynamic) | **(a)** — fixed set; anything else falls back to the existing neutral chip (current behaviour) |

#### Implementation tasks (refine after decisions)

| id | status | task |
| --- | --- | --- |
| S6-A | [x] | Apply D6.1: add token(s) to `tokens.css`, update `Scene.tsx` selected-state styling. |
| S6-B | [x] | Apply D6.2/D6.3/D6.4: update `TerminalPill.tsx`; add kind → glyph/token lookup in a sibling module. |
| S6-C | [x] | Snapshot or screenshot-coverage test for each terminal kind; visual regression acceptable via DOM assertions on token class / glyph text. |

### Execution ordering

1. **Slice 1 — COMPLETE.**
2. **Slice 3 (recipes foundation)** — prerequisite for Slice 4. Small, no behavior change; low risk.
3. **Slice 4 (popover layout v2)** — the visible pain. Depends on Slice 3.
4. **Slice 6 (storyboard visual polish)** — standalone; can run in parallel with Slice 2/4 once D6.* are locked.
5. **Slice 2 (remaining mediums/lows)** — can interleave with or follow Slice 4; no dependency either way.
6. **Slice 5 (right-rail inspector)** — product decision; do last or skip if popover v2 is sufficient.

---

## Validation gates per slice

- `cd ui && npm run lint`
- `cd ui && npm run build`
- `cd ui && npm run test`
- New tests added for any behavior/component change (per AGENTS.md §Test and validation policy).
