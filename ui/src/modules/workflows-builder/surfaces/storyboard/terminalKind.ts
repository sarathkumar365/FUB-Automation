/**
 * Terminal-pill kind resolution — glyph + colour-token lookup.
 *
 * Per D6.2-c + D6.3-a + D6.4-a: a fixed set of first-class terminal
 * kinds each get both a leading glyph and a semantic colour token
 * triple. Anything outside the set falls back to the pre-existing
 * neutral chip, preserving today's behaviour for custom/unknown
 * result codes.
 *
 * Kept as a tiny pure module so `TerminalPill` (renderer) and
 * `viewport.ts` (width estimator) share one source of truth — if
 * they drifted, the viewBox math would cut off pills whose glyph
 * prefix pushed them wider than the estimator expected.
 */

export type TerminalKind = 'success' | 'failure' | 'skipped' | 'noop' | 'neutral'

export interface TerminalKindTokens {
  /** Leading glyph character; empty string for `neutral`. */
  glyph: string
  /** CSS var() reference for pill background. */
  bg: string
  /** CSS var() reference for pill border. */
  border: string
  /** CSS var() reference for pill text colour. */
  text: string
}

const NEUTRAL_TOKENS: TerminalKindTokens = {
  glyph: '',
  bg: 'var(--color-storyboard-chip-neutral-bg)',
  border: 'var(--color-storyboard-chip-neutral-border)',
  text: 'var(--color-storyboard-chip-neutral-text)',
}

const KIND_TOKENS: Record<Exclude<TerminalKind, 'neutral'>, TerminalKindTokens> = {
  success: {
    glyph: '✓',
    bg: 'var(--color-storyboard-terminal-success-bg)',
    border: 'var(--color-storyboard-terminal-success-border)',
    text: 'var(--color-storyboard-terminal-success-text)',
  },
  failure: {
    glyph: '✕',
    bg: 'var(--color-storyboard-terminal-failure-bg)',
    border: 'var(--color-storyboard-terminal-failure-border)',
    text: 'var(--color-storyboard-terminal-failure-text)',
  },
  skipped: {
    glyph: '◻',
    bg: 'var(--color-storyboard-terminal-skipped-bg)',
    border: 'var(--color-storyboard-terminal-skipped-border)',
    text: 'var(--color-storyboard-terminal-skipped-text)',
  },
  noop: {
    glyph: '↷',
    bg: 'var(--color-storyboard-terminal-noop-bg)',
    border: 'var(--color-storyboard-terminal-noop-border)',
    text: 'var(--color-storyboard-terminal-noop-text)',
  },
}

/**
 * Resolve a transition's `resultCode` to a `TerminalKind`. The match is
 * case-insensitive on the exact canonical kind name; anything else —
 * including `default`, `timeout`, or custom step-declared codes — returns
 * `neutral` so it renders the existing chip (per D6.4-a).
 */
export function resolveTerminalKind(resultCode: string): TerminalKind {
  const key = resultCode.toLowerCase()
  if (key === 'success' || key === 'failure' || key === 'skipped' || key === 'noop') {
    return key
  }
  return 'neutral'
}

/** Tokens + glyph for a given kind. Safe for every `TerminalKind`. */
export function tokensForKind(kind: TerminalKind): TerminalKindTokens {
  return kind === 'neutral' ? NEUTRAL_TOKENS : KIND_TOKENS[kind]
}

/** Shortcut: get just the glyph prefix string for a resultCode. Returns
 *  the glyph plus a trailing space when present, else empty string — so
 *  callers can simply prepend the result to the label without branching. */
export function terminalGlyphPrefix(resultCode: string): string {
  const { glyph } = tokensForKind(resolveTerminalKind(resultCode))
  return glyph ? `${glyph} ` : ''
}
