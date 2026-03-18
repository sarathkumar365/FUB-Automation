import { webhookDetailSchema } from '../platform/adapters/http/webhookSchemas'
import { describe, expect, it } from 'vitest'

describe('webhookDetailSchema', () => {
  it('accepts valid response shape', () => {
    const parsed = webhookDetailSchema.parse({
      id: 1,
      eventId: 'evt-1',
      source: 'FUB',
      eventType: 'callsCreated',
      status: 'RECEIVED',
      payloadHash: 'abc123',
      payload: { eventType: 'callsCreated' },
      receivedAt: '2026-03-18T10:00:00Z',
    })

    expect(parsed.id).toBe(1)
  })

  it('fails invalid source values', () => {
    expect(() =>
      webhookDetailSchema.parse({
        id: 1,
        eventId: 'evt-1',
        source: 'BAD',
        eventType: 'callsCreated',
        status: 'RECEIVED',
        payload: {},
        receivedAt: '2026-03-18T10:00:00Z',
      }),
    ).toThrow()
  })
})
