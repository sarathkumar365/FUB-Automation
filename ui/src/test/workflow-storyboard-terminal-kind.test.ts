import { describe, expect, it } from 'vitest'
import {
  resolveTerminalKind,
  terminalGlyphPrefix,
  tokensForKind,
} from '../modules/workflows-builder/surfaces/storyboard/terminalKind'

describe('resolveTerminalKind (D6.4-a)', () => {
  it('matches the four first-class kinds exactly', () => {
    expect(resolveTerminalKind('success')).toBe('success')
    expect(resolveTerminalKind('failure')).toBe('failure')
    expect(resolveTerminalKind('skipped')).toBe('skipped')
    expect(resolveTerminalKind('noop')).toBe('noop')
  })

  it('is case-insensitive on canonical kind names', () => {
    expect(resolveTerminalKind('SUCCESS')).toBe('success')
    expect(resolveTerminalKind('Failure')).toBe('failure')
  })

  it('returns neutral for unknown / custom / near-miss codes', () => {
    expect(resolveTerminalKind('default')).toBe('neutral')
    expect(resolveTerminalKind('gave_up')).toBe('neutral')
    expect(resolveTerminalKind('on_success')).toBe('neutral')
    expect(resolveTerminalKind('')).toBe('neutral')
    // Step-declared custom codes from the backend are intentionally neutral.
    expect(resolveTerminalKind('CONVERSATIONAL')).toBe('neutral')
    expect(resolveTerminalKind('COMM_NOT_FOUND')).toBe('neutral')
  })

  it('maps backend-emitted aliases onto the four first-class kinds', () => {
    // Backend vocabulary (uppercase) — this is what real graphs actually use.
    expect(resolveTerminalKind('SUCCESS')).toBe('success')
    expect(resolveTerminalKind('DONE')).toBe('success')
    expect(resolveTerminalKind('COMPLETED')).toBe('success')
    expect(resolveTerminalKind('OK')).toBe('success')

    expect(resolveTerminalKind('FAILED')).toBe('failure')
    expect(resolveTerminalKind('ERROR')).toBe('failure')
    // TIMEOUT is intentionally grouped with failure (D6.4-a ruled out a
    // dedicated timed_out kind; timeout is functionally a fail outcome).
    expect(resolveTerminalKind('TIMEOUT')).toBe('failure')

    expect(resolveTerminalKind('SKIPPED')).toBe('skipped')
    expect(resolveTerminalKind('NO_OP')).toBe('noop')
  })
})

describe('tokensForKind (D6.2-c / D6.3-a)', () => {
  it('returns a glyph + triple of var() references for each first-class kind', () => {
    for (const kind of ['success', 'failure', 'skipped', 'noop'] as const) {
      const t = tokensForKind(kind)
      expect(t.glyph.length).toBeGreaterThan(0)
      expect(t.bg).toMatch(/^var\(--color-storyboard-terminal-/)
      expect(t.border).toMatch(/^var\(--color-storyboard-terminal-/)
      expect(t.text).toMatch(/^var\(--color-storyboard-terminal-/)
    }
  })

  it('returns empty glyph + neutral chip tokens for the neutral kind', () => {
    const t = tokensForKind('neutral')
    expect(t.glyph).toBe('')
    expect(t.bg).toBe('var(--color-storyboard-chip-neutral-bg)')
    expect(t.border).toBe('var(--color-storyboard-chip-neutral-border)')
    expect(t.text).toBe('var(--color-storyboard-chip-neutral-text)')
  })

  it('uses distinct glyphs across first-class kinds', () => {
    const glyphs = (['success', 'failure', 'skipped', 'noop'] as const).map(
      (k) => tokensForKind(k).glyph,
    )
    expect(new Set(glyphs).size).toBe(glyphs.length)
  })
})

describe('terminalGlyphPrefix', () => {
  it('prepends glyph + space for first-class kinds', () => {
    expect(terminalGlyphPrefix('success')).toBe('✓ ')
    expect(terminalGlyphPrefix('failure')).toBe('✕ ')
  })

  it('returns empty string for neutral-bucket codes', () => {
    expect(terminalGlyphPrefix('default')).toBe('')
    expect(terminalGlyphPrefix('custom_code')).toBe('')
  })
})
