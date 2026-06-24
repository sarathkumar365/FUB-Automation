import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { type PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '@app/portsContextValue'
import { useDashboardSnapshotQuery } from '@modules/dashboard/data/useDashboardSnapshotQuery'
import { queryKeys } from '@platform/query/queryKeys'
import type { AppPorts } from '@platform/container'
import type { DashboardSnapshot } from '@platform/contracts/dashboardSchemas'

function sampleSnapshot(): DashboardSnapshot {
  return {
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
    recentRuns: [],
    needsAttention: [],
  }
}

function createWrapper(queryClient: QueryClient, getSnapshot: () => Promise<DashboardSnapshot>) {
  const ports = { dashboardPort: { getSnapshot } } as unknown as AppPorts
  return function Wrapper({ children }: PropsWithChildren) {
    return (
      <PortsContext.Provider value={ports}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </PortsContext.Provider>
    )
  }
}

describe('dashboard snapshot query hook', () => {
  it('fetches the snapshot from the dashboard port in one call', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const getSnapshot = vi.fn(async () => sampleSnapshot())
    const Wrapper = createWrapper(queryClient, getSnapshot)

    const { result } = renderHook(() => useDashboardSnapshotQuery(), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getSnapshot).toHaveBeenCalledTimes(1)
    expect(result.current.data?.hero.openFailures).toBe(2)
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.dashboard.snapshot() })).toBeTruthy()
  })

  it('surfaces a query error when the snapshot request fails', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const getSnapshot = vi.fn(async () => {
      throw new Error('snapshot failure')
    })
    const Wrapper = createWrapper(queryClient, getSnapshot)

    const { result } = renderHook(() => useDashboardSnapshotQuery(), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
