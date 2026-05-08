# `shared/ui`

Shared UI layer for the app. Everything in here is app-agnostic: no workflow, run, or lead logic. If a component knows about a domain concept, it belongs in a feature module (`src/modules/<feature>/ui/`), not here.

Organized into three tiers — pick the right tier when adding a new file.

---

## Tier 1 — Primitives (this directory)

Low-level building blocks. A primitive has no opinion about *what* it's displaying; it only knows *how* to render a generic thing (a button, a popover, a badge).

Two flavors coexist here:

- **shadcn-style** — `button.tsx`, `badge.tsx`, `input.tsx`, `select.tsx`, `Tabs.tsx`, `Popover.tsx`. Thin wrappers over Radix + `class-variance-authority`, following the shadcn file naming convention (`lowercase.tsx` for shadcn-generated atoms; `PascalCase.tsx` for hand-rolled).
- **layout/content shells** — `PageCard.tsx`, `PageHeader.tsx`, `EmptyState.tsx`, `ErrorState.tsx`, `LoadingState.tsx`, `FilterBar.tsx`, `DataTable.tsx`. Still app-agnostic, but they bake in spacing/structure conventions used across screens.

**When to add a primitive:**
- The behavior or markup is needed in more than one unrelated feature.
- It wraps a third-party lib (Radix, etc.) and we don't want that import leaking into feature modules.
- There's no domain vocabulary in the API — no "workflow," no "run," no "lead."

**When *not* to:**
- It's only used by one feature. Keep it in that feature's `ui/` folder.
- It's visual composition of existing primitives with a specific use case. That's a recipe (Tier 2).

---

## Tier 2 — Recipes (`./recipes/`)

> Status: landing in Slice 3 Phase B of the workflow-builder UI audit. The folder will exist once the first recipe ships.

An opinionated composition of primitives for a recurring pattern. Recipes make the "what does a label-above-value row look like" decision *once*, so features don't each answer it differently.

Planned recipes (see [`ui/Docs/workflow-builder-ui-audit.md`](../../../Docs/workflow-builder-ui-audit.md) Slice 3):

- `Section` — uppercase caption + body container.
- `FieldRow` — label/value row with auto inline-vs-stacked behavior based on value length/type.
- `KeyValueList` — list of `FieldRow`s in a 2-col grid or stacked (opt-in `dense`).
- `CopyableValue` — monospace value with a hover-revealed copy button.
- `DefinitionCard` — titled card with optional badge + action in the header.
- `Skeleton` — placeholder shapes for loading states.

**When to add a recipe:**
- You're about to write the same layout markup in a second feature.
- A primitive is too generic — you want to encode specific visual decisions (uppercase caption, 60-char auto-stack threshold, hover-to-copy).

**When *not* to:**
- The composition is specific to one feature's data shape. Keep it local.

---

## Tier 3 — Feature compositions (`src/modules/<feature>/ui/`)

Screens and feature-specific UI. These may consume primitives *and* recipes. They own domain logic, data hooks, and the layout of an actual screen. Examples:

- `modules/workflows/ui/WorkflowDetailPage/`
- `modules/workflows/ui/WorkflowDetailPage/StoryboardTab/inspector/`

If a piece of a feature starts looking generic, promote it — feature → recipe (Tier 2) or feature → primitive (Tier 1).

---

## Rules

### The token rule

**No inline hex, rgba, or raw color literals in TSX.** All colors, shadows, radii, and spacing come from CSS custom properties defined in [`../../styles/tokens.css`](../../styles/tokens.css). If you need a color that doesn't exist, add a token first — see [`../../styles/tokens.README.md`](../../styles/tokens.README.md) for categories and naming.

### The barrel

[`./index.ts`](./index.ts) re-exports every primitive + (eventually) every recipe. Callers may import either way:

```ts
// Via barrel (preferred for multi-import):
import { Badge, Button, Tabs, TabsContent } from '@/shared/ui'

// Via explicit path (fine too):
import { Badge } from '@/shared/ui/badge'
```

There is no hard rule. Pick whichever reads better at the call site.

### Strings

All user-visible strings go through [`src/shared/constants/uiText.ts`](../constants/uiText.ts) — no inline English in primitives or recipes.

### Tests

Each primitive and each recipe needs at least one test (per AGENTS.md §Test and validation policy). Tests live under [`src/test/`](../../test/) and follow the `<component-name>.test.tsx` naming convention.
