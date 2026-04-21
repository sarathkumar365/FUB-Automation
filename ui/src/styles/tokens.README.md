# Design tokens (`tokens.css`)

Single source of truth for colors, spacing, radii, typography, and elevation. Every primitive, recipe, and feature TSX references tokens via `var(--token-name)` — **no inline hex / rgba / rgb literals in TSX**.

This file documents the categories in [`tokens.css`](./tokens.css), when to reuse an existing token vs. add a new one, and the naming convention.

---

## Categories

### Typography — `--font-*`
Font stacks for UI text (`--font-ui`) and monospace (`--font-mono`). Only add here if introducing a new font family.

### Core surfaces — `--color-bg`, `--color-surface`, `--color-surface-alt`, `--color-border`
Background tiers used by every screen. Reuse before adding. A new "card-ish" surface almost always maps to `--color-surface` or `--color-surface-alt`.

### Text — `--color-text`, `--color-text-muted`
Two text tones. Most text should be `--color-text`; supporting copy uses `--color-text-muted`. Resist adding a third — if you need it, you probably want `opacity` on one of these.

### Brand — `--color-brand`, `--color-brand-soft`, `--color-brand-2`
Primary brand color, its soft/translucent variant for subtle fills, and a secondary accent. Used by `Button` (default variant), `Badge` (default), focus rings.

### Semantic status — `--color-status-{ok|warn|bad}[-bg]`
Colors tied to semantic meaning: success (ok / teal), warning (amber), error (rose). `-bg` variants are the low-saturation fill used behind the `fg` text. **Always pair fg + bg** — never use `ok` without `ok-bg`, and vice versa.

### Live indicator — `--color-live`
Green used by the heartbeat/live pulse. Don't repurpose for generic success states — that's `--color-status-ok`.

### Storyboard accent palette — `--color-accent-*-{fg|bg|dot}`
Category-keyed palette for storyboard scene cards and related chips. Categories: `trigger`, `side-effect`, `wait`, `branch`, `compute`, `neutral`.

Each category has three shades:
- `fg` — text color
- `bg` — fill for the chip/card
- `dot` — solid swatch color (used for category dots on scene cards)

Accessed at runtime via `modules/workflows-builder/surfaces/storyboard/accentTokens.ts`. Adding a new category means updating both this file and that lookup.

### Storyboard surface hints — `--color-storyboard-*`
Storyboard-specific visual effects: grid dot, card border, card shadow (normal + selected), edge stroke colors, arrow marker, neutral chip styling. Used only by `surfaces/storyboard/*` TSX and the `.storyboard-canvas` CSS class in this file.

### Radius — `--radius-sm`, `--radius-md`, `--radius-pill`
8px / 12px / fully-round. Use the scale — don't hardcode arbitrary pixel values.

### Spacing — `--space-1` through `--space-5`
4 / 8 / 12 / 16 / 20 px. Most primitives use Tailwind utility classes for spacing (`gap-3`, `p-4`), so these tokens show up mostly in CSS or inline `style` where utility classes don't fit.

### Elevation — `--shadow-subtle`
Standard drop shadow for the handful of surfaces that need elevation. Scene cards have their own storyboard-specific shadows.

### Form controls — `--select-chevron`
Inline SVG used as the `<select>` chevron background. Not a color, but belongs to the form-control visual language.

---

## When to add a new token

Add a token when:

1. **The value will appear in more than one place.** A one-off doesn't need a token.
2. **The value has meaning that the name communicates.** `--color-accent-trigger-bg` says *why*; `--color-x5` doesn't.
3. **You can't express it as an adjustment to an existing token.** If the new value is "`--color-brand` at 8% opacity", use `color-mix(in srgb, var(--color-brand), transparent 92%)` instead of minting a new variable.

Don't add a token for:
- A single visual effect in one component (use a Tailwind utility or inline `style` with an existing token).
- Temporary debugging colors.
- Values tied to a specific component's internal layout (padding inside a particular popover) — those are component constants, not design tokens.

---

## Naming convention

- **Kebab-case.** `--color-storyboard-card-border`, not `--colorStoryboardCardBorder`.
- **Category prefix.** Start with `color-`, `font-`, `space-`, `radius-`, `shadow-`. Lets editors group them on autocomplete.
- **Qualifier next, modifier last.** `--color-accent-trigger-bg` reads as *accent palette, trigger category, background shade*. Consistent left-to-right narrowing.
- **Paired tokens share a stem.** `--color-status-ok` and `--color-status-ok-bg`. Makes it obvious they belong together.

---

## Dark mode

Not implemented yet. When it lands, overrides will be grouped under a `:root[data-theme='dark']` (or `@media (prefers-color-scheme: dark)`) block in this same file. Every token that needs a dark value gets overridden in one place; TSX does not branch on theme.

Writing primitives against tokens — *not* inline colors — is what makes this future migration a one-file change. The token rule in [`src/shared/ui/README.md`](../shared/ui/README.md) is the enforcement point.
