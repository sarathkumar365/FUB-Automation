import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpJsonClient } from '@platform/adapters/http/httpJsonClient'
import { HttpReportingAdapter } from '@platform/adapters/http/httpReportingAdapter'

const mockFetch = vi.fn()

const window = {
  key: 'yesterday',
  from: '2026-08-12T04:00:00Z',
  to: '2026-08-13T04:00:00Z',
  timezone: 'America/Toronto',
  open: false,
  eventsReceived: 84,
}

const counts = { leads: 2, spoke: 1, attempted: 0, nothing: 1 }

describe('http reporting adapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('GETs the source-contact report with the window as a query parameter', async () => {
    const payload = {
      window,
      totals: counts,
      sources: [
        {
          source: 'Facebook',
          counts,
          agents: [{ agentId: 1, agentName: 'Mandeep', counts }],
        },
      ],
    }
    mockFetch.mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 }))

    const adapter = new HttpReportingAdapter(new HttpJsonClient())
    const result = await adapter.getSourceContactReport('yesterday')

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/reporting/source-contact?window=yesterday',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.sources[0]?.agents[0]?.agentName).toBe('Mandeep')
  })

  it('GETs the accountability report and the per-agent worklist', async () => {
    const report = { window, totals: counts, agents: [{ agentId: 31, agentName: null, counts }] }
    const worklist = {
      window,
      agentId: 31,
      agentName: null,
      limit: 200,
      truncated: false,
      leads: [
        {
          sourcePersonId: '123',
          name: 'Ava Whitfield',
          phone: '+14155550100',
          email: null,
          source: 'Facebook',
          arrivedAt: '2026-08-12T15:00:00Z',
        },
      ],
    }
    mockFetch
      .mockResolvedValueOnce(new Response(JSON.stringify(report), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(worklist), { status: 200 }))

    const adapter = new HttpReportingAdapter(new HttpJsonClient())
    const reportResult = await adapter.getAccountabilityReport('this-week')
    const worklistResult = await adapter.getUnreachedLeads(31, 'this-week')

    expect(mockFetch).toHaveBeenNthCalledWith(
      1,
      '/admin/reporting/accountability?window=this-week',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(mockFetch).toHaveBeenNthCalledWith(
      2,
      '/admin/reporting/accountability/31/unreached?window=this-week',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(reportResult.agents[0]?.agentName).toBeNull()
    expect(worklistResult.leads[0]?.name).toBe('Ava Whitfield')
  })

  it('rejects a payload whose counts are missing a state', async () => {
    const malformed = {
      window,
      totals: { leads: 2, spoke: 1, attempted: 0 }, // nothing missing
      sources: [],
    }
    mockFetch.mockResolvedValue(new Response(JSON.stringify(malformed), { status: 200 }))

    const adapter = new HttpReportingAdapter(new HttpJsonClient())

    await expect(adapter.getSourceContactReport('yesterday')).rejects.toThrow()
  })
})
