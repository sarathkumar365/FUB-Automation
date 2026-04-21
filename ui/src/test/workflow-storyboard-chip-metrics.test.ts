import { describe, expect, it } from 'vitest'
import {
  CHIP_CHAR_WIDTH,
  CHIP_PADDING_X,
  estimateChipWidth,
} from '../modules/workflows-builder/surfaces/storyboard/chipMetrics'

describe('estimateChipWidth', () => {
  it('is monotonic non-decreasing in text length', () => {
    let prev = estimateChipWidth('')
    for (let i = 1; i <= 40; i += 1) {
      const next = estimateChipWidth('x'.repeat(i))
      expect(next).toBeGreaterThanOrEqual(prev)
      prev = next
    }
  })

  it('respects the provided minWidth for short labels', () => {
    expect(estimateChipWidth('ok', { minWidth: 120 })).toBe(120)
    expect(estimateChipWidth('x', { minWidth: 48 })).toBe(48)
  })

  it('produces an integer width', () => {
    const value = estimateChipWidth('hello_world_long_label')
    expect(Number.isInteger(value)).toBe(true)
  })

  it('never renders narrower than the raw text would require for long labels', () => {
    const longLabel = 'communication_received → lead responded'
    const width = estimateChipWidth(longLabel, { minWidth: 60 })
    const naiveLowerBound = Math.ceil(longLabel.length * CHIP_CHAR_WIDTH + CHIP_PADDING_X)
    expect(width).toBeGreaterThanOrEqual(naiveLowerBound)
  })

  it('defaults minWidth to 60 when omitted', () => {
    expect(estimateChipWidth('a')).toBe(60)
  })

  it('uses the documented padding and char-width constants', () => {
    expect(CHIP_PADDING_X).toBe(20)
    expect(CHIP_CHAR_WIDTH).toBe(7.2)
  })

  it('for a 25-char label, returns width >= padding + 25 * char-width', () => {
    const label = 'x'.repeat(25)
    const width = estimateChipWidth(label, { minWidth: 60 })
    expect(width).toBeGreaterThanOrEqual(20 + Math.ceil(25 * 7.2))
  })

  it('handles empty string without going below minWidth', () => {
    expect(estimateChipWidth('')).toBe(60)
    expect(estimateChipWidth('', { minWidth: 48 })).toBe(48)
  })
})
