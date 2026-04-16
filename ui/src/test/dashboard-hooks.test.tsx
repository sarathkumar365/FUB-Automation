import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { type PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '../app/portsContextValue'
import { useDashboardSnapshotQuery } from '../modules/dashboard/data/useDashboardSnapshotQuery'
import { queryKeys } from '../platform/query/queryKeys'
import type { AppPorts } from '../platform/container'

function createWrapper(queryClient: QueryClient) {
  const listWorkflows = vi.fn(async () => ({
    items: [],
    page: 0,
    size: 1,
    total: 4,
  }))
  const listWorkflowRuns = vi.fn(async (filters?: { status?: string; page?: number; size?: number }) => ({
    items:
      filters?.status === 'FAILED'
        ? [buildRun(9), buildRun(10)]
        : [buildRun(1), buildRun(2), buildRun(3)],
    page: filters?.page ?? 0,
    size: filters?.size ?? 5,
    total: filters?.status === 'FAILED' ? 2 : 3,
  }))
  const listWebhooks = vi.fn(async () => ({
    items: [
      {
        id: 12,
        eventId: 'ev_12',
        source: 'FUB' as const,
        eventType: 'peopleUpdated',
        status: 'RECEIVED' as const,
        receivedAt: '2026-04-16T10:00:00Z',
      },
    ],
    nextCursor: null,
    serverTime: '2026-04-16T10:00:00Z',
  }))

  const ports = {
    adminWebhookPort: {
      listWebhooks,
      getWebhookDetail: vi.fn(),
      listEventTypes: vi.fn(),
      buildWebhookStreamRequest: vi.fn(),
    },
    processedCallsPort: {
      listProcessedCalls: vi.fn(),
      replayProcessedCall: vi.fn(),
    },
    webhookStreamPort: {
      openWebhookStream: vi.fn(),
    },
    workflowPort: {
      listWorkflows,
      createWorkflow: vi.fn(),
      getWorkflowByKey: vi.fn(),
      updateWorkflow: vi.fn(),
      listWorkflowVersions: vi.fn(),
      validateWorkflow: vi.fn(),
      activateWorkflow: vi.fn(),
      deactivateWorkflow: vi.fn(),
      rollbackWorkflow: vi.fn(),
      archiveWorkflow: vi.fn(),
      listStepTypes: vi.fn(),
      listTriggerTypes: vi.fn(),
    },
    workflowRunPort: {
      listWorkflowRuns,
      listWorkflowRunsForKey: vi.fn(),
      getWorkflowRunDetail: vi.fn(),
      cancelWorkflowRun: vi.fn(),
    },
  } as unknown as AppPorts

  return {
    listWorkflows,
    listWorkflowRuns,
    listWebhooks,
    Wrapper: function Wrapper({ children }: PropsWithChildren) {
      return (
        <PortsContext.Provider value={ports}>
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </PortsContext.Provider>
      )
    },
  }
}

describe('dashboard snapshot query hook', () => {
  it('aggregates dashboard data from workflow/runs/webhooks endpoints', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { listWorkflows, listWorkflowRuns, listWebhooks, Wrapper } = createWrapper(queryClient)

    const { result } = renderHook(() => useDashboardSnapshotQuery(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(listWorkflows).toHaveBeenCalledWith({
      status: 'ACTIVE',
      page: 0,
      size: 1,
    })
    expect(listWorkflowRuns).toHaveBeenCalledWith({
      page: 0,
      size: 5,
    })
    expect(listWorkflowRuns).toHaveBeenCalledWith({
      status: 'FAILED',
      page: 0,
      size: 5,
    })
    expect(listWebhooks).toHaveBeenCalledWith({
      limit: 5,
    })
    expect(result.current.data?.activeWorkflows.count).toBe(4)
    expect(result.current.data?.failedRuns.count).toBe(2)
    expect(result.current.data?.systemHealth.recentWebhookCount).toBe(1)
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.dashboard.snapshot() })).toBeTruthy()
  })

  it('returns query error when a dependent endpoint fails', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { Wrapper, listWorkflowRuns } = createWrapper(queryClient)
    listWorkflowRuns.mockRejectedValueOnce(new Error('runs endpoint failure'))

    const { result } = renderHook(() => useDashboardSnapshotQuery(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })
})

function buildRun(id: number) {
  return {
    id,
    workflowKey: `wf_${id}`,
    workflowVersionNumber: 1,
    status: 'COMPLETED' as const,
    reasonCode: null,
    startedAt: null,
    completedAt: null,
  }
}
