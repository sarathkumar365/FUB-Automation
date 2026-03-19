import { describe, expect, it } from 'vitest'
import { toProcessedCallsApiDateFilters } from '../modules/processed-calls/lib/processedCallDateFilters'

describe('toProcessedCallsApiDateFilters', () => {
  it('converts date-only filters to local offset datetime boundaries', () => {
    const result = toProcessedCallsApiDateFilters({
      from: '2026-03-01',
      to: '2026-03-19',
    })

    expect(result.from).toMatch(/^2026-03-01T00:00:00[+-]\d{2}:\d{2}$/)
    expect(result.to).toMatch(/^2026-03-19T23:59:59\.999[+-]\d{2}:\d{2}$/)
    expect(result.from?.endsWith('Z')).toBe(false)
    expect(result.to?.endsWith('Z')).toBe(false)
  })

  it('keeps local calendar day boundaries after parsing', () => {
    const result = toProcessedCallsApiDateFilters({
      from: '2026-03-01',
      to: '2026-03-19',
    })

    const from = new Date(result.from ?? '')
    const to = new Date(result.to ?? '')

    expect(from.getFullYear()).toBe(2026)
    expect(from.getMonth()).toBe(2)
    expect(from.getDate()).toBe(1)
    expect(from.getHours()).toBe(0)
    expect(from.getMinutes()).toBe(0)
    expect(from.getSeconds()).toBe(0)
    expect(from.getMilliseconds()).toBe(0)

    expect(to.getFullYear()).toBe(2026)
    expect(to.getMonth()).toBe(2)
    expect(to.getDate()).toBe(19)
    expect(to.getHours()).toBe(23)
    expect(to.getMinutes()).toBe(59)
    expect(to.getSeconds()).toBe(59)
    expect(to.getMilliseconds()).toBe(999)
  })

  it('preserves non date-only values as-is', () => {
    expect(
      toProcessedCallsApiDateFilters({
        from: '2026-03-01T09:00:00Z',
        to: '2026-03-19T18:30:00+00:00',
      }),
    ).toEqual({
      from: '2026-03-01T09:00:00Z',
      to: '2026-03-19T18:30:00+00:00',
    })
  })
})
