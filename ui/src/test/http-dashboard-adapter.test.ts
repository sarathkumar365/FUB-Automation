import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpDashboardAdapter } from '@platform/adapters/http/httpDashboardAdapter'
import { HttpJsonClient } from '@platform/adapters/http/httpJsonClient'

const mockFetch = vi.fn()

const validSnapshot = {
  window: { from: '2026-02-01T00:00:00Z', to: '2026-02-02T00:00:00Z', label: 'Last 24h' },
  hero: {
    state: 'HEALTHY',
    openFailures: 2,
    stats: {
      runs: { value: 20, delta: { value: 5, direction: 'UP' } },
      successRate: { value: 90, delta: { value: -1, direction: 'DOWN' } },
      openFailures: { value: 2, delta: { value: 2, direction: 'UP' } },
    },
  },
  throughput: { series: [1, 2, 3], peak: 3, avg: 2, perMin: 4 },
  funnel: {
    ingested: { value: 100, spark: [1, 2] },
    domainEvents: { value: 90, spark: [1, 2] },
    runs: { value: 20, spark: [1, 2] },
    failed: { value: 2, spark: [1, 2] },
  },
  recentRuns: [
    { id: 1, workflowKey: 'wf_1', status: 'COMPLETED', startedAt: '2026-02-01T10:00:00Z', completedAt: '2026-02-01T10:00:42Z', durationSec: 42 },
  ],
  needsAttention: [{ ref: '7', workflowKey: 'wf_b', status: 'FAILED', reason: null, ageSeconds: 300 }],
}

describe('http dashboard adapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('GETs the snapshot endpoint and parses the payload', async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify(validSnapshot), { status: 200 }))

    const adapter = new HttpDashboardAdapter(new HttpJsonClient())
    const result = await adapter.getSnapshot()

    expect(mockFetch).toHaveBeenCalledWith('/admin/dashboard/snapshot', expect.objectContaining({ method: 'GET' }))
    expect(result.hero.openFailures).toBe(2)
    expect(result.hero.stats.successRate.value).toBe(90)
    expect(result.funnel.failed.value).toBe(2)
    expect(result.needsAttention[0]?.reason).toBeNull()
  })

  it('rejects a malformed snapshot payload', async () => {
    const malformed = { ...validSnapshot, hero: { ...validSnapshot.hero, state: 'GREAT' } }
    mockFetch.mockResolvedValue(new Response(JSON.stringify(malformed), { status: 200 }))

    const adapter = new HttpDashboardAdapter(new HttpJsonClient())

    await expect(adapter.getSnapshot()).rejects.toThrow()
  })
})
