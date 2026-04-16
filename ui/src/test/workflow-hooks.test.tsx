import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { type PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '../app/portsContextValue'
import { useStepTypesQuery } from '../modules/workflows/data/useStepTypesQuery'
import { useTriggerTypesQuery } from '../modules/workflows/data/useTriggerTypesQuery'
import { useCreateWorkflowMutation } from '../modules/workflows/data/useCreateWorkflowMutation'
import { useWorkflowDetailQuery } from '../modules/workflows/data/useWorkflowDetailQuery'
import { useWorkflowVersionsQuery } from '../modules/workflows/data/useWorkflowVersionsQuery'
import { useWorkflowsQuery } from '../modules/workflows/data/useWorkflowsQuery'
import { queryKeys } from '../platform/query/queryKeys'
import type { AppPorts } from '../platform/container'

function createPortsMocks() {
  return {
    listWorkflows: vi.fn(async () => ({
      items: [],
      page: 0,
      size: 20,
      total: 0,
    })),
    createWorkflow: vi.fn(async (command: { key: string; name: string }) => ({
      id: 11,
      key: command.key,
      name: command.name,
      description: null,
      trigger: {},
      graph: {},
      status: 'DRAFT' as const,
      versionNumber: 1,
      version: 1,
    })),
    getWorkflowByKey: vi.fn(async (key: string) => ({
      id: 1,
      key,
      name: 'Workflow',
      description: null,
      trigger: {},
      graph: {},
      status: 'DRAFT' as const,
      versionNumber: 1,
      version: 1,
    })),
    listWorkflowVersions: vi.fn(async () => []),
    listStepTypes: vi.fn(async () => []),
    listTriggerTypes: vi.fn(async () => []),
  }
}

function createWrapper(queryClient: QueryClient, workflowMocks: ReturnType<typeof createPortsMocks>) {
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
      listWorkflows: workflowMocks.listWorkflows,
      getWorkflowByKey: workflowMocks.getWorkflowByKey,
      createWorkflow: workflowMocks.createWorkflow,
      updateWorkflow: vi.fn(),
      listWorkflowVersions: workflowMocks.listWorkflowVersions,
      validateWorkflow: vi.fn(),
      activateWorkflow: vi.fn(),
      deactivateWorkflow: vi.fn(),
      rollbackWorkflow: vi.fn(),
      archiveWorkflow: vi.fn(),
      listStepTypes: workflowMocks.listStepTypes,
      listTriggerTypes: workflowMocks.listTriggerTypes,
    },
    workflowRunPort: {
      listWorkflowRuns: vi.fn(),
      listWorkflowRunsForKey: vi.fn(),
      getWorkflowRunDetail: vi.fn(),
      cancelWorkflowRun: vi.fn(),
    },
  } as unknown as AppPorts

  return function Wrapper({ children }: PropsWithChildren) {
    return (
      <PortsContext.Provider value={ports}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </PortsContext.Provider>
    )
  }
}

describe('workflow hooks', () => {
  it('forwards list filters via useWorkflowsQuery', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const workflowMocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, workflowMocks)

    const filters = { status: 'ACTIVE' as const, page: 2, size: 50 }
    renderHook(() => useWorkflowsQuery(filters), { wrapper })

    await waitFor(() => {
      expect(workflowMocks.listWorkflows).toHaveBeenCalledWith(filters)
    })
  })

  it('uses detail/version query keys and enables only when key exists', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const workflowMocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, workflowMocks)

    renderHook(() => useWorkflowDetailQuery(undefined), { wrapper })
    renderHook(() => useWorkflowVersionsQuery(undefined), { wrapper })

    expect(workflowMocks.getWorkflowByKey).not.toHaveBeenCalled()
    expect(workflowMocks.listWorkflowVersions).not.toHaveBeenCalled()

    renderHook(() => useWorkflowDetailQuery('wf_test'), { wrapper })
    renderHook(() => useWorkflowVersionsQuery('wf_test'), { wrapper })

    await waitFor(() => {
      expect(workflowMocks.getWorkflowByKey).toHaveBeenCalledWith('wf_test')
      expect(workflowMocks.listWorkflowVersions).toHaveBeenCalledWith('wf_test')
    })

    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.workflows.detail('wf_test') })).toBeTruthy()
    expect(queryClient.getQueryCache().find({ queryKey: queryKeys.workflows.versions('wf_test') })).toBeTruthy()
  })

  it('applies Infinity stale time for step/trigger catalog hooks', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const workflowMocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, workflowMocks)

    renderHook(() => useStepTypesQuery(), { wrapper })
    renderHook(() => useTriggerTypesQuery(), { wrapper })

    await waitFor(() => {
      expect(workflowMocks.listStepTypes).toHaveBeenCalled()
      expect(workflowMocks.listTriggerTypes).toHaveBeenCalled()
    })

    const stepTypesQuery = queryClient.getQueryCache().find({ queryKey: queryKeys.workflows.stepTypes() })
    const triggerTypesQuery = queryClient.getQueryCache().find({ queryKey: queryKeys.workflows.triggerTypes() })

    expect((stepTypesQuery?.options as { staleTime?: number } | undefined)?.staleTime).toBe(Infinity)
    expect((triggerTypesQuery?.options as { staleTime?: number } | undefined)?.staleTime).toBe(Infinity)
  })

  it('invalidates workflow list and detail on create success', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const workflowMocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, workflowMocks)
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateWorkflowMutation(), { wrapper })
    await result.current.mutateAsync({
      key: 'wf_created',
      name: 'Created Workflow',
      description: undefined,
      status: 'DRAFT',
      trigger: {},
      graph: {},
    })

    expect(workflowMocks.createWorkflow).toHaveBeenCalledWith(
      expect.objectContaining({
        key: 'wf_created',
        name: 'Created Workflow',
      }),
    )
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: ['workflows', 'list'],
      }),
    )
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: queryKeys.workflows.detail('wf_created'),
      }),
    )
  })
})
