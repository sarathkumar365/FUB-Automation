import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { type PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '../app/portsContextValue'
import { useActivateWorkflowMutation } from '../modules/workflows/data/useActivateWorkflowMutation'
import { useArchiveWorkflowMutation } from '../modules/workflows/data/useArchiveWorkflowMutation'
import { useDeactivateWorkflowMutation } from '../modules/workflows/data/useDeactivateWorkflowMutation'
import { useRollbackWorkflowMutation } from '../modules/workflows/data/useRollbackWorkflowMutation'
import { useUpdateWorkflowMutation } from '../modules/workflows/data/useUpdateWorkflowMutation'
import { useValidateWorkflowMutation } from '../modules/workflows/data/useValidateWorkflowMutation'
import { queryKeys } from '../platform/query/queryKeys'
import type { AppPorts } from '../platform/container'

function createPortsMocks() {
  return {
    updateWorkflow: vi.fn(async (key: string, command: { name: string }) => ({
      id: 1,
      key,
      name: command.name,
      description: null,
      trigger: {},
      graph: {},
      status: 'DRAFT' as const,
      versionNumber: 2,
      version: 2,
    })),
    validateWorkflow: vi.fn(async () => ({ valid: true, errors: [] })),
    activateWorkflow: vi.fn(async (key: string) => ({
      id: 1,
      key,
      name: 'Workflow',
      description: null,
      trigger: {},
      graph: {},
      status: 'ACTIVE' as const,
      versionNumber: 2,
      version: 2,
    })),
    deactivateWorkflow: vi.fn(async (key: string) => ({
      id: 1,
      key,
      name: 'Workflow',
      description: null,
      trigger: {},
      graph: {},
      status: 'INACTIVE' as const,
      versionNumber: 2,
      version: 2,
    })),
    rollbackWorkflow: vi.fn(async (key: string) => ({
      id: 1,
      key,
      name: 'Workflow',
      description: null,
      trigger: {},
      graph: {},
      status: 'DRAFT' as const,
      versionNumber: 1,
      version: 3,
    })),
    archiveWorkflow: vi.fn(async (key: string) => ({
      id: 1,
      key,
      name: 'Workflow',
      description: null,
      trigger: {},
      graph: {},
      status: 'ARCHIVED' as const,
      versionNumber: 2,
      version: 4,
    })),
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
      listWorkflows: vi.fn(),
      createWorkflow: vi.fn(),
      getWorkflowByKey: vi.fn(),
      updateWorkflow: workflowMocks.updateWorkflow,
      listWorkflowVersions: vi.fn(),
      validateWorkflow: workflowMocks.validateWorkflow,
      activateWorkflow: workflowMocks.activateWorkflow,
      deactivateWorkflow: workflowMocks.deactivateWorkflow,
      rollbackWorkflow: workflowMocks.rollbackWorkflow,
      archiveWorkflow: workflowMocks.archiveWorkflow,
      listStepTypes: vi.fn(),
      listTriggerTypes: vi.fn(),
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

describe('workflow mutation hooks', () => {
  it('forwards payloads for update/validate/rollback', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const mocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, mocks)

    const updateHook = renderHook(() => useUpdateWorkflowMutation('wf_a'), { wrapper })
    const validateHook = renderHook(() => useValidateWorkflowMutation(), { wrapper })
    const rollbackHook = renderHook(() => useRollbackWorkflowMutation('wf_a'), { wrapper })

    await updateHook.result.current.mutateAsync({
      name: 'Updated',
      description: undefined,
      trigger: {},
      graph: {},
    })
    await validateHook.result.current.mutateAsync({
      trigger: { source: 'fub' },
      graph: { nodes: [] },
    })
    await rollbackHook.result.current.mutateAsync(1)

    expect(mocks.updateWorkflow).toHaveBeenCalledWith(
      'wf_a',
      expect.objectContaining({ name: 'Updated' }),
    )
    expect(mocks.validateWorkflow).toHaveBeenCalledWith(
      expect.objectContaining({ trigger: { source: 'fub' } }),
    )
    expect(mocks.rollbackWorkflow).toHaveBeenCalledWith('wf_a', { toVersion: 1 })
  })

  it('invalidates detail/versions/list queries on lifecycle success', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const mocks = createPortsMocks()
    const wrapper = createWrapper(queryClient, mocks)
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const activateHook = renderHook(() => useActivateWorkflowMutation('wf_a'), { wrapper })
    const deactivateHook = renderHook(() => useDeactivateWorkflowMutation('wf_a'), { wrapper })
    const archiveHook = renderHook(() => useArchiveWorkflowMutation('wf_a'), { wrapper })

    await activateHook.result.current.mutateAsync()
    await deactivateHook.result.current.mutateAsync()
    await archiveHook.result.current.mutateAsync()

    expect(invalidateQueriesSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['workflows', 'list'] }))
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: queryKeys.workflows.detail('wf_a') }),
    )
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: queryKeys.workflows.versions('wf_a') }),
    )
  })

  it('does not invalidate when update mutation fails', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const mocks = createPortsMocks()
    mocks.updateWorkflow.mockRejectedValueOnce(new Error('update failed'))
    const wrapper = createWrapper(queryClient, mocks)
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const hook = renderHook(() => useUpdateWorkflowMutation('wf_a'), { wrapper })
    await expect(
      hook.result.current.mutateAsync({
        name: 'Broken',
        description: undefined,
        trigger: {},
        graph: {},
      }),
    ).rejects.toThrow('update failed')

    await waitFor(() => {
      expect(invalidateQueriesSpy).not.toHaveBeenCalled()
    })
  })
})

