import { HttpJsonClient } from '../platform/adapters/http/httpJsonClient'
import { HttpLeadsAdapter } from '../platform/adapters/http/httpLeadsAdapter'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()

describe('HttpLeadsAdapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('serializes list query params and parses lead feed response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            {
              id: 7,
              sourceSystem: 'FUB',
              sourceLeadId: '12345',
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

    const adapter = new HttpLeadsAdapter(new HttpJsonClient())
    const result = await adapter.listLeads({
      sourceSystem: 'FUB',
      status: 'ACTIVE',
      sourceLeadIdPrefix: '123',
      from: '2026-04-01',
      to: '2026-04-21',
      limit: 50,
      cursor: 'CURSOR_XYZ',
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/leads?sourceSystem=FUB&status=ACTIVE&sourceLeadIdPrefix=123&from=2026-04-01&to=2026-04-21&limit=50&cursor=CURSOR_XYZ',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.items[0]?.sourceLeadId).toBe('12345')
    expect(result.nextCursor).toBe('CURSOR_ABC')
  })

  it('encodes sourceLeadId and forwards includeLive flag on summary', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          lead: {
            id: 7,
            sourceSystem: 'FUB',
            sourceLeadId: 'weird id/?',
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

    const adapter = new HttpLeadsAdapter(new HttpJsonClient())
    const result = await adapter.getLeadSummary('weird id/?', {
      sourceSystem: 'FUB',
      includeLive: true,
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/leads/weird%20id%2F%3F/summary?sourceSystem=FUB&includeLive=true',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.liveStatus).toBe('LIVE_SKIPPED')
  })
})
