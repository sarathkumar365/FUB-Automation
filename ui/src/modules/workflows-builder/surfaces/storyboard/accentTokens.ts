/**
 * Storyboard accent token lookup.
 *
 * `FormatterAccent` categories are mapped to CSS custom properties defined in
 * `ui/src/styles/tokens.css`. This file is the single source of truth for
 * resolving a category to its `fg` / `bg` / `dot` tones — both SVG siblings
 * (Scene.tsx) and HTML siblings (SceneInspectorPopover.tsx) read from here so
 * we never drift colors across surfaces.
 *
 * Keep this list in sync with the `--color-accent-*` tokens; no other file
 * should hardcode these hex/rgba values.
 */
import type { FormatterAccent } from '../../model/cardFormatters'

export interface AccentTone {
  /** CSS var reference for the foreground/text color. */
  fg: string
  /** CSS var reference for the pill/chip background fill. */
  bg: string
  /** CSS var reference for the solid dot swatch color. */
  dot: string
}

export const ACCENT_TONES: Record<FormatterAccent, AccentTone> = {
  trigger: {
    fg: 'var(--color-accent-trigger-fg)',
    bg: 'var(--color-accent-trigger-bg)',
    dot: 'var(--color-accent-trigger-dot)',
  },
  'side-effect': {
    fg: 'var(--color-accent-side-effect-fg)',
    bg: 'var(--color-accent-side-effect-bg)',
    dot: 'var(--color-accent-side-effect-dot)',
  },
  wait: {
    fg: 'var(--color-accent-wait-fg)',
    bg: 'var(--color-accent-wait-bg)',
    dot: 'var(--color-accent-wait-dot)',
  },
  branch: {
    fg: 'var(--color-accent-branch-fg)',
    bg: 'var(--color-accent-branch-bg)',
    dot: 'var(--color-accent-branch-dot)',
  },
  compute: {
    fg: 'var(--color-accent-compute-fg)',
    bg: 'var(--color-accent-compute-bg)',
    dot: 'var(--color-accent-compute-dot)',
  },
  neutral: {
    fg: 'var(--color-accent-neutral-fg)',
    bg: 'var(--color-accent-neutral-bg)',
    dot: 'var(--color-accent-neutral-dot)',
  },
}

/** Resolve an accent tone for an accent category. */
export function getAccentTone(accent: FormatterAccent): AccentTone {
  return ACCENT_TONES[accent]
}
