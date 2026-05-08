import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { type PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '../app/portsContextValue'
import { useCancelWorkflowRunMutation } from '../modules/workflow-runs/data/useCancelWorkflowRunMutation'
import { useWorkflowRunDetailQuery } from '../modules/workflow-runs/data/useWorkflowRunDetailQuery'
import { useWorkflowRunsForKeyQuery } from '../modules/workflow-runs/data/useWorkflowRunsForKeyQuery'
import { useWorkflowRunsQuery } from '../modules/workflow-runs/data/useWorkflowRunsQuery'
import { queryKeys } from '../platform/query/queryKeys'
import type { AppPorts } from '../platform/container'

function createWrapper(queryClient: QueryClient) {
  const listWorkflowRuns = vi.fn(async () => ({
    items: [],
    page: 0,
    size: 20,
    total: 0,
  }))
  const listWorkflowRunsForKey = vi.fn(async () => ({
    items: [],
    page: 0,
    size: 20,
    total: 0,
  }))
  const getWorkflowRunDetail = vi.fn(async (runId: number) => ({
    id: runId,
    workflowKey: 'wf_a',
    workflowVersionNumber: 2,
    status: 'COMPLETED' as const,
    reasonCode: null,
    startedAt: null,
    completedAt: null,
    triggerPayload: {},
    sourceLeadId: null,
    eventId: null,
    steps: [],
  }))
  const cancelWorkflowRun = vi.fn(async (runId: number) => ({
    id: runId,
    workflowKey: 'wf_a',
    workflowVersionNumber: 2,
    status: 'CANCELED' as const,
    reasonCode: 'OPERATOR_CANCELED',
    startedAt: null,
    completedAt: null,
    triggerPayload: {},
    sourceLeadId: null,
    eventId: null,
    steps: [],
  }))

  const ports = {
    adminWebhookPort: {
      listWebhooks: vi.fn(),
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
      listWorkflows: vi.fn(),
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
      listWorkflowRunsForKey,
      getWorkflowRunDetail,
      cancelWorkflowRun,
    },
  } as unknown as AppPorts

  return {
    listWorkflowRuns,
    listWorkflowRunsForKey,
    getWorkflowRunDetail,
    cancelWorkflowRun,
    Wrapper: function Wrapper({ children }: PropsWithChildren) {
      return (
        <PortsContext.Provider value={ports}>
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </PortsContext.Provider>
      )
    },
  }
}

describe('workflow runs hooks', () => {
  it('forwards filters for global run list query', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { listWorkflowRuns, Wrapper } = createWrapper(queryClient)

    const filters = { status: 'FAILED' as const, page: 2, size: 50 }
    renderHook(() => useWorkflowRunsQuery(filters), { wrapper: Wrapper })

    await waitFor(() => {
      expect(listWorkflowRuns).toHaveBeenCalledWith(filters)
    })
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.workflowRuns.list(filters) })).toBeTruthy()
  })

  it('enables detail query only when run id is present', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { getWorkflowRunDetail, Wrapper } = createWrapper(queryClient)

    renderHook(() => useWorkflowRunDetailQuery(undefined), { wrapper: Wrapper })
    expect(getWorkflowRunDetail).not.toHaveBeenCalled()

    renderHook(() => useWorkflowRunDetailQuery(44), { wrapper: Wrapper })
    await waitFor(() => {
      expect(getWorkflowRunDetail).toHaveBeenCalledWith(44)
    })
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.workflowRuns.detail(44) })).toBeTruthy()
  })

  it('forwards key filters and query key for scoped run list', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { listWorkflowRunsForKey, Wrapper } = createWrapper(queryClient)

    const filters = { status: 'FAILED' as const, page: 1, size: 25 }
    renderHook(() => useWorkflowRunsForKeyQuery('wf_a', filters), { wrapper: Wrapper })

    await waitFor(() => {
      expect(listWorkflowRunsForKey).toHaveBeenCalledWith('wf_a', filters)
    })
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.workflowRuns.listForKey('wf_a', filters) })).toBeTruthy()
  })

  it('does not call scoped run list query without workflow key', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const { listWorkflowRunsForKey, Wrapper } = createWrapper(queryClient)

    renderHook(() => useWorkflowRunsForKeyQuery(undefined, { page: 0, size: 20 }), { wrapper: Wrapper })
    expect(listWorkflowRunsForKey).not.toHaveBeenCalled()
  })

  it('cancels run and invalidates run detail/global/scoped queries', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const { cancelWorkflowRun, Wrapper } = createWrapper(queryClient)
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const hook = renderHook(() => useCancelWorkflowRunMutation(44), { wrapper: Wrapper })
    await hook.result.current.mutateAsync()

    expect(cancelWorkflowRun).toHaveBeenCalledWith(44)
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: queryKeys.workflowRuns.detail(44) }),
    )
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['workflow-runs', 'list'] }))
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['workflow-runs', 'key', 'wf_a'] }),
    )
  })
})
