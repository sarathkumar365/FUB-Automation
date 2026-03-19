import { describe, expect, it } from 'vitest'
import { formatProcessedCallDateTime, formatProcessedCallStatus } from '../modules/processed-calls/lib/processedCallDisplay'

describe('processed call display formatting', () => {
  it('formats processed-call statuses to readable text', () => {
    expect(formatProcessedCallStatus('TASK_CREATED')).toBe('Task Created')
    expect(formatProcessedCallStatus('PROCESSING')).toBe('Processing')
  })

  it('formats processed-call timestamps to human-readable month/day/year and time', () => {
    const value = formatProcessedCallDateTime('2026-03-30T13:33:00Z')
    expect(value).toMatch(/^[A-Z][a-z]{2} \d{2}, \d{4}, \d{2}:\d{2} (am|pm)$/)
  })

  it('returns raw timestamp when date parsing fails', () => {
    expect(formatProcessedCallDateTime('not-a-date')).toBe('not-a-date')
  })
})
