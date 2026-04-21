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
| M5 | [ ] | M | `ValidationStrip.tsx:110-151` | `ToneSet` builder rebuilt on every render; collapsed list has no severity aria. | Module-level frozen map keyed by `state.mode`; add `aria-label` per severity. |
| M6 | [ ] | M | `RunsTab/index.tsx:155-189` | Filter row hand-built (`<label><Select/></label>` + buttons) instead of `shared/ui/FilterBar`. Comment says "FilterBar border removed". | Extend `FilterBar` with `bordered={false}` variant and consume. |
| M7 | [ ] | M | `WorkflowHeaderStrip.tsx:126-130` | `readTriggerType` duplicates logic in `graphAdapters.ts:66` + `cardFormatters`. | Single `lib/readTriggerType.ts` shared by adapters + header. |
| M8 | [ ] | M | `WorkflowDetailPage/index.tsx:147` | React `key` hack `${key}-${version}-${open/closed}` on `WorkflowEditModal` to force remount. | Move state reset inside modal via `useEffect` on open transition. |
| M9 | [x] | M | `ExitEdge.tsx:64` | Label rect literal `#ffffff` — breaks dark theme. | `style={{fill: 'var(--color-surface)'}}`. |

---

## LOW — nits

| id | status | sev | file:line | issue | fix |
| --- | --- | --- | --- | --- | --- |
| L1 | [ ] | L | `Scene.tsx:46` | Trigger label special-cased inline (`isTrigger ? 'trigger' …`). | Move to `cardFormatters`. |
| L2 | [ ] | L | `StoryboardViewer.tsx:60` | `const handleSelect = onSelectScene ?? (() => {})`. | Accept `undefined` on `Scene.onSelect`. |
| L3 | [ ] | L | `SceneInspectorPopover.tsx:459` | Literal `"on "` prefix not in `uiText`. | Add to `uiText`. |
| L4 | [ ] | L | `WorkflowBuilderPage.tsx:58,76-79,98,113` | Inline strings: "Builder info", "New workflow", "Start with a single entry step.", "Invalid graph", "Storyboard". | Route through `uiText`. |
| L5 | [x] | L | `WorkflowTabs.tsx:34` | `aria-label="Workflow detail sections"` not in `uiText`. | Done in Slice 1.1 — routed via `uiText.workflows.detailTabsAriaLabel`. |
| L6 | [x] | L | `SceneInspectorPopover.tsx:206` | `×` character instead of icon. | Done in Slice 1.1 — replaced with `CloseIcon` from `shared/ui/icons.tsx`. |
| L7 | [ ] | L | `ValidationStrip.tsx:23` | `showIssues` default `true` — not persisted across route changes. | Persist in URL or session if product wants. Track only. |

---

## Execution plan

Execute in order; each slice: code → new test → lint/build/test → commit.

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
| D3.1 | `Section` recipe — caption treatment? | (a) uppercase + letter-spacing (current style), (b) sentence-case bold, (c) small icon + label, (d) user picks per-use via prop | _TBD_ |
| D3.2 | `FieldRow` recipe — default layout? | (a) `auto` (infer from value type), (b) always stacked, (c) always inline, (d) caller must specify | _TBD_ |
| D3.3 | `CopyableValue` recipe — copy button visibility? | (a) always visible, (b) visible on hover only, (c) visible after click on the value, (d) caller picks | _TBD_ |
| D3.4 | `CopyableValue` recipe — feedback on copy? | (a) inline "Copied" swap for 1.5s (like `JsonViewer` today), (b) toast via `useNotify`, (c) icon change only | _TBD_ |
| D3.5 | `DefinitionCard` recipe — header slot? | (a) just a title, (b) title + optional action button, (c) title + badge + action, (d) fully slot-based | _TBD_ |
| D3.6 | `Skeleton` recipe — shapes supported? | (a) just line + block, (b) line + block + circle (avatar), (c) full set (line, block, circle, table-row, card), (d) punt — add shapes as needed | _TBD_ |
| D3.7 | `KeyValueList` — default layout? | (a) 2-col grid, (b) stacked, (c) responsive (grid on wide, stacked on narrow), (d) caller picks | _TBD_ |

After decisions are recorded, implement per the task table below. Items S3-A, S3-B, S3-C (barrel + READMEs) can proceed without decisions.

| id | status | item |
| --- | --- | --- |
| S3-A | [ ] | Create `ui/src/shared/ui/index.ts` barrel re-exporting every primitive + recipe for ergonomic imports. Keep the explicit-path option working — just offer the barrel. |
| S3-B | [ ] | Create `ui/src/shared/ui/README.md` documenting the three tiers (primitives / recipes / feature compositions), how to pick where a new component goes, and the token rule. |
| S3-C | [ ] | Add `ui/src/styles/tokens.README.md` explaining the token categories (core surfaces, brand, status, storyboard, accent) and when to add a new one vs reusing an existing var. |
| S3-D | [ ] | Create `shared/ui/recipes/Section.tsx` — uppercase caption label + body container. Props: `title`, `children`, optional `action` slot. Used wherever the popover / header / inspector has an "CONFIG / TRANSITIONS / METADATA" heading. |
| S3-E | [ ] | Create `shared/ui/recipes/FieldRow.tsx` — value-type-aware row. Accepts `{ label, value, layout?: 'inline' \| 'stacked' \| 'auto' }`. `auto` picks layout from value (see S4 rules). |
| S3-F | [ ] | Create `shared/ui/recipes/KeyValueList.tsx` — wraps `FieldRow[]`, supports 2-col grid OR stacked list. |
| S3-G | [ ] | Create `shared/ui/recipes/CopyableValue.tsx` — monospace chip + copy button (pattern already in `JsonViewer`, extract + share). |
| S3-H | [ ] | Create `shared/ui/recipes/DefinitionCard.tsx` — surface + header + body composition used by popover body and any future detail card. |
| S3-I | [ ] | Create `shared/ui/recipes/Skeleton.tsx` — block/line/avatar skeleton placeholders for loading states (currently nothing exists; features roll their own). |
| S3-J | [ ] | Each new recipe gets at least one test in `ui/src/test/*.test.tsx` following the AGENTS.md policy. |

### Slice 4 — popover layout v2 (long-text + detail UX)

Motivation: in the current popover, long `transitions` and long config values (`context`, URLs, JSONata expressions) break mid-word due to the fixed 340px width + 2-col `110px \| 1fr` grid + `overflow-wrap: anywhere`. Result: visually broken rows, not a layout. Depends on Slice 3 recipes.

> **⚠️ DO NOT start implementation until UX decisions are made with the user.**
>
> When this slice is reached, the assistant MUST pause and walk the user through each decision below before writing code. Present the options with trade-offs; do not pick unilaterally. Once decisions are recorded (fill in the "Decision" columns below), proceed with implementation.

#### Decisions required (collaborate with user before implementing)

| # | Question | Options to present | Decision |
| --- | --- | --- | --- |
| D4.1 | How should **short scalar** config values render (booleans, numbers, short strings)? | (a) inline 2-col grid like today, (b) always stacked label-above-value, (c) pill/badge on the right side of a stacked row | _TBD_ |
| D4.2 | How should **templating strings** (containing `$`, `{{`, `}}`) render? | (a) monospace chip with horizontal scroll, (b) `CopyableValue` stacked block, (c) syntax-highlighted code block, (d) collapsed by default with "Show" toggle | _TBD_ |
| D4.3 | What is the **length threshold** for "short" vs "long" strings? | (a) 40 chars, (b) 60 chars, (c) container-width-based (measure), (d) no threshold — always stacked | _TBD_ |
| D4.4 | How should **long plain strings** render? | (a) stacked with `pre-wrap` + "Show more" at N lines, (b) full stacked always, (c) collapsed preview + modal on expand, (d) truncated + tooltip | _TBD_ |
| D4.5 | Should **URL-shaped** strings get special treatment? | (a) yes — link styling + copy button, (b) no — treat as normal string, (c) yes but behind a "copy URL" affordance only | _TBD_ |
| D4.6 | How should **transitions** render for long resultCodes + long targets? | (a) card-per-transition, stacked caption above target, (b) wider horizontal rows with better wrapping rules, (c) table with fixed columns + ellipsis + tooltip on overflow, (d) disclosure — collapsed summary, expand to see target | _TBD_ |
| D4.7 | Popover **default width**? | (a) keep 340px, (b) widen to 380px, (c) widen to 420px, (d) fully responsive min/max envelope | _TBD_ |
| D4.8 | Popover **max height** behavior for tall content? | (a) keep inner scroll (today), (b) remove max-height and let it grow, (c) split tall content across tabs inside the popover | _TBD_ |
| D4.9 | **Copy action** — on which field types? | (a) all long strings, (b) only URL + templating strings, (c) every field has a copy affordance, (d) none (keep clean) | _TBD_ |
| D4.10 | **"Show more" disclosure** — at how many lines? | (a) 3, (b) 4, (c) 6, (d) no cap — always show full | _TBD_ |

After decisions are filled in, update the task table below to reflect the chosen approach, then implement.

#### Implementation tasks (refine after decisions are recorded)

| id | status | item |
| --- | --- | --- |
| S4-A | [ ] | Rebuild `StoryboardTab/inspector/ConfigRow.tsx` on top of `FieldRow` recipe per decisions D4.1–D4.5. |
| S4-B | [ ] | Rebuild `StoryboardTab/inspector/TransitionRow.tsx` per decision D4.6. |
| S4-C | [ ] | Apply popover sizing per decisions D4.7–D4.8 in `StoryboardTab/constants.ts`; verify side-decision math. |
| S4-D | [ ] | If D4.4 or D4.10 selects disclosure, build the "Show more" helper (hook or small recipe). Skip otherwise. |
| S4-E | [ ] | If D4.5 enables URL handling, add URL-shape detection helper in `StoryboardTab/inspector/formatConfigValue.ts`. Skip otherwise. |
| S4-F | [ ] | Update `ui/src/test/workflow-scene-inspector-popover.test.tsx` to match the chosen rendering. |
| S4-G | [ ] | Optional: visual regression / screenshot sanity for a worst-case graph. |

### Slice 5 — scene inspector in right rail (optional, bigger lift)

Motivation: the shell already exposes an **Inspector region** (`src/app/shell/...`) as a first-class layout zone. Today it hosts `WorkflowVersionList` on the workflow detail page. Surfacing scene detail there would give users more real estate and match the "selection drives right rail" pattern seen in Figma / Notion / Linear — but it's a product call whether this is worth the extra layout complexity on top of a polished popover.

> **⚠️ DO NOT start implementation until UX decisions are made with the user.**
>
> This slice is optional and reshapes the shell. Walk through the decisions below before writing any code; several of them may collapse the slice to "skip".

#### Decisions required (collaborate with user before implementing)

| # | Question | Options to present | Decision |
| --- | --- | --- | --- |
| D5.1 | Is scene-detail-in-right-rail worth doing at all, given a polished Slice 4 popover? | (a) yes — right rail becomes the authoritative view, (b) yes — but only as an alternative, user toggles, (c) no — skip this slice entirely | _TBD_ |
| D5.2 | If (a) or (b): how should the Inspector region handle both **Versions** and **Scene** content? | (a) tabbed (Versions / Scene), (b) swap — scene replaces versions while selected, versions return on deselect, (c) stacked — both visible, versions compressed, (d) accordion sections | _TBD_ |
| D5.3 | Should the **floating popover** coexist with the right-rail inspector? | (a) yes — popover = quick glance, rail = authoritative, (b) popover hides when rail shows the same scene, (c) popover removed entirely — rail is the only detail view | _TBD_ |
| D5.4 | What is the **entry affordance** for opening the right-rail inspector? | (a) single-click a scene (same as popover today), (b) double-click a scene, (c) dedicated "expand" button on the popover, (d) keyboard shortcut | _TBD_ |
| D5.5 | Should scene selection **persist in the URL** (so links are shareable)? | (a) yes — add `?scene=<id>` search param, (b) no — selection is transient, (c) yes but only when the right rail is open | _TBD_ |
| D5.6 | Does this pattern become **canonical** for all workflow detail surfaces going forward? | (a) yes — update `AGENTS.md` "UX decisions" to lock it, (b) no — scene inspector only, other surfaces stay feature-specific | _TBD_ |

After decisions are filled in, update the task table below (or mark the slice dropped) before implementing.

#### Implementation tasks (refine after decisions are recorded)

| id | status | item |
| --- | --- | --- |
| S5-A | [ ] | If D5.1 is (a) or (b): extract `InspectorBody` into a width-aware component that mounts at popover-width or rail-width without branching layout logic. |
| S5-B | [ ] | Wire scene selection into the Inspector region per D5.2. |
| S5-C | [ ] | Popover/rail coexistence behavior per D5.3. |
| S5-D | [ ] | Entry affordance per D5.4. |
| S5-E | [ ] | URL persistence per D5.5. |
| S5-F | [ ] | Tests: shell-level test asserting Inspector switches content on scene selection; popover tests updated for coexistence behavior. |
| S5-G | [ ] | If D5.6 is (a): update `AGENTS.md` "UX decisions" section. |

### Execution ordering

1. **Slice 1 — COMPLETE.**
2. **Slice 3 (recipes foundation)** — prerequisite for Slice 4. Small, no behavior change; low risk.
3. **Slice 4 (popover layout v2)** — the visible pain. Depends on Slice 3.
4. **Slice 2 (remaining mediums/lows)** — can interleave with or follow Slice 4; no dependency either way.
5. **Slice 5 (right-rail inspector)** — product decision; do last or skip if popover v2 is sufficient.

---

## Validation gates per slice

- `cd ui && npm run lint`
- `cd ui && npm run build`
- `cd ui && npm run test`
- New tests added for any behavior/component change (per AGENTS.md §Test and validation policy).
