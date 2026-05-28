import { HttpJsonClient } from '../platform/adapters/http/httpJsonClient'
import { HttpPersonsAdapter } from '../platform/adapters/http/httpPersonsAdapter'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()

describe('HttpPersonsAdapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('serializes list query params and parses person feed response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            {
              id: 7,
              sourceSystem: 'FUB',
              sourcePersonId: '12345',
              status: 'ACTIVE',
              snapshot: { name: 'Jane Doe' },
              createdAt: '2026-04-10T10:00:00Z',
              updatedAt: '2026-04-20T10:00:00Z',
              lastSyncedAt: '2026-04-20T10:00:00Z',
            },
          ],
          nextCursor: 'CURSOR_ABC',
          serverTime: '2026-04-21T10:00:00Z',
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpPersonsAdapter(new HttpJsonClient())
    const result = await adapter.listPersons({
      sourceSystem: 'FUB',
      status: 'ACTIVE',
      sourcePersonIdPrefix: '123',
      from: '2026-04-01',
      to: '2026-04-21',
      limit: 50,
      cursor: 'CURSOR_XYZ',
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/persons?sourceSystem=FUB&status=ACTIVE&sourcePersonIdPrefix=123&from=2026-04-01&to=2026-04-21&limit=50&cursor=CURSOR_XYZ',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.items[0]?.sourcePersonId).toBe('12345')
    expect(result.nextCursor).toBe('CURSOR_ABC')
  })

  it('encodes sourcePersonId and forwards includeLive flag on summary', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          person: {
            id: 7,
            sourceSystem: 'FUB',
            sourcePersonId: 'weird id/?',
            status: 'ACTIVE',
            snapshot: null,
            createdAt: '2026-04-10T10:00:00Z',
            updatedAt: '2026-04-20T10:00:00Z',
            lastSyncedAt: '2026-04-20T10:00:00Z',
          },
          livePerson: null,
          liveStatus: 'LIVE_SKIPPED',
          liveMessage: null,
          activity: [],
          recentCalls: [],
          recentWorkflowRuns: [],
          recentWebhookEvents: [],
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpPersonsAdapter(new HttpJsonClient())
    const result = await adapter.getPersonSummary('weird id/?', {
      sourceSystem: 'FUB',
      includeLive: true,
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/persons/weird%20id%2F%3F/summary?sourceSystem=FUB&includeLive=true',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.liveStatus).toBe('LIVE_SKIPPED')
  })
})
