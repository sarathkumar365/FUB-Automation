import { describe, expect, it } from 'vitest'
import { formatWebhookEventType, formatWebhookReceivedAt } from '../shared/lib/webhookDisplay'

describe('webhook display formatting', () => {
  it('formats event types to readable title text', () => {
    expect(formatWebhookEventType('callsCreated')).toBe('Calls Created')
    expect(formatWebhookEventType('contact_updated')).toBe('Contact Updated')
    expect(formatWebhookEventType('API_EVENT')).toBe('API EVENT')
  })

  it('formats received-at timestamps to month/day and time with am/pm', () => {
    const value = formatWebhookReceivedAt('2026-03-30T13:33:00Z')
    expect(value).toMatch(/^[A-Z][a-z]{2} \d{2}, \d{2}:\d{2} (am|pm)$/)
  })

  it('returns raw received-at value when date parsing fails', () => {
    expect(formatWebhookReceivedAt('not-a-date')).toBe('not-a-date')
  })
})
