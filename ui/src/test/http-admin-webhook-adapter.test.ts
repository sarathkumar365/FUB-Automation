import { HttpAdminWebhookAdapter } from '../platform/adapters/http/httpAdminWebhookAdapter'
import { HttpJsonClient } from '../platform/adapters/http/httpJsonClient'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()

describe('HttpAdminWebhookAdapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('serializes list query params and parses response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            {
              id: 10,
              eventId: 'evt-1',
              source: 'FUB',
              eventType: 'callsCreated',
              status: 'RECEIVED',
              receivedAt: '2026-03-18T12:00:00Z',
            },
          ],
          nextCursor: null,
          serverTime: '2026-03-18T12:01:00Z',
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpAdminWebhookAdapter(new HttpJsonClient())
    const result = await adapter.listWebhooks({
      source: 'FUB',
      status: 'RECEIVED',
      eventType: 'callsCreated',
      limit: 25,
      includePayload: false,
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/webhooks?source=FUB&status=RECEIVED&eventType=callsCreated&limit=25&includePayload=false',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.items[0]?.eventId).toBe('evt-1')
  })

  it('builds the stream endpoint path from filters', () => {
    const adapter = new HttpAdminWebhookAdapter(new HttpJsonClient())

    expect(
      adapter.buildWebhookStreamRequest({
        source: 'FUB',
        status: 'RECEIVED',
        eventType: 'callsCreated',
      }),
    ).toBe('/admin/webhooks/stream?source=FUB&status=RECEIVED&eventType=callsCreated')
  })

  it('fetches distinct event types as a string array', async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(['callsCreated', 'callsUpdated']), { status: 200 }),
    )

    const adapter = new HttpAdminWebhookAdapter(new HttpJsonClient())
    const result = await adapter.listEventTypes()

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/webhooks/event-types',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result).toEqual(['callsCreated', 'callsUpdated'])
  })

  it('rejects non-array event type payloads via zod', async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ items: [] }), { status: 200 }),
    )

    const adapter = new HttpAdminWebhookAdapter(new HttpJsonClient())

    await expect(adapter.listEventTypes()).rejects.toThrow()
  })

  it('rejects malformed payloads via zod', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [{ id: 'bad-id' }],
          nextCursor: null,
          serverTime: '2026-03-18T12:01:00Z',
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpAdminWebhookAdapter(new HttpJsonClient())

    await expect(adapter.listWebhooks({})).rejects.toThrow()
  })
})
