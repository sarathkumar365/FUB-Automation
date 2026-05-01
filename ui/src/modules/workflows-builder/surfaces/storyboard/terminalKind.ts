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
 * Canonical → backend-emitted aliases. The Java step registry doesn't emit
 * the literal strings `success` / `failure` / `skipped` / `noop`; it emits
 * `SUCCESS` / `FAILED` / `DONE` / `COMPLETED` / `TIMEOUT` etc. D6.4-a's
 * intent (four first-class kinds, everything else neutral) is preserved —
 * this table just captures the real vocabulary so the four categories
 * actually light up on real graphs.
 *
 * Matching is case-insensitive. To intentionally render as neutral, use a
 * step-declared custom code (e.g. `CONVERSATIONAL`, `COMM_NOT_FOUND`) —
 * those are not in this table and fall through to the neutral chip.
 */
const KIND_ALIASES: Record<Exclude<TerminalKind, 'neutral'>, readonly string[]> = {
  success: ['success', 'ok', 'done', 'completed'],
  // `timeout` is grouped with failure: D6.4-a ruled out a dedicated
  // timed_out kind, and a timeout is functionally a fail outcome. Flip to
  // neutral later if product calls it out as its own thing.
  failure: ['failure', 'failed', 'error', 'timeout'],
  skipped: ['skipped'],
  noop: ['noop', 'no_op'],
}

/**
 * Resolve a transition's `resultCode` to a `TerminalKind`. Matches against
 * the canonical + backend-alias table above; anything else — including
 * step-declared custom codes — returns `neutral` (per D6.4-a).
 */
export function resolveTerminalKind(resultCode: string): TerminalKind {
  const key = resultCode.toLowerCase()
  for (const [kind, aliases] of Object.entries(KIND_ALIASES) as [
    Exclude<TerminalKind, 'neutral'>,
    readonly string[],
  ][]) {
    if (aliases.includes(key)) return kind
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
