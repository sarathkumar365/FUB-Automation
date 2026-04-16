import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { type PropsWithChildren } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { useShellRegions } from '../app/useShellRegions'
import { PortsContext } from '../app/portsContextValue'
import { WorkflowDetailPage } from '../modules/workflows/ui/WorkflowDetailPage'
import type { AppPorts } from '../platform/container'
import { notifyContext } from '../shared/notifications/notifyContext'

function InspectorHost() {
  const { inspectorContent } = useShellRegions()
  return <div data-testid="inspector-host">{inspectorContent?.body}</div>
}

function createWorkflowPorts() {
  return {
    getWorkflowByKey: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A',
      description: 'A workflow',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'DRAFT' as const,
      versionNumber: 2,
      version: 2,
    })),
    listWorkflowVersions: vi.fn(async () => [
      {
        versionNumber: 2,
        status: 'DRAFT' as const,
        createdAt: '2026-04-16T10:00:00Z',
        updatedAt: '2026-04-16T10:00:00Z',
      },
      {
        versionNumber: 1,
        status: 'ACTIVE' as const,
        createdAt: '2026-04-15T10:00:00Z',
        updatedAt: '2026-04-15T10:00:00Z',
      },
    ]),
    updateWorkflow: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A Updated',
      description: 'Updated',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'DRAFT' as const,
      versionNumber: 3,
      version: 3,
    })),
    validateWorkflow: vi.fn(async () => ({
      valid: false,
      errors: ['Missing node transition'],
    })),
    activateWorkflow: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A',
      description: 'A workflow',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'ACTIVE' as const,
      versionNumber: 2,
      version: 2,
    })),
    deactivateWorkflow: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A',
      description: 'A workflow',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'INACTIVE' as const,
      versionNumber: 2,
      version: 2,
    })),
    rollbackWorkflow: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A',
      description: 'A workflow',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'DRAFT' as const,
      versionNumber: 1,
      version: 4,
    })),
    archiveWorkflow: vi.fn(async () => ({
      id: 1,
      key: 'wf_a',
      name: 'Workflow A',
      description: 'A workflow',
      trigger: { source: 'fub' },
      graph: { nodes: [] },
      status: 'ARCHIVED' as const,
      versionNumber: 2,
      version: 5,
    })),
    listWorkflowRunsForKey: vi.fn(async (_key: string, filters: { status?: string; page?: number; size?: number }) => ({
      items: [
        {
          id: 44,
          workflowKey: 'wf_a',
          workflowVersionNumber: 2,
          status: filters.status === 'FAILED' ? 'FAILED' : 'COMPLETED',
          reasonCode: filters.status === 'FAILED' ? 'STEP_TIMEOUT' : null,
          startedAt: '2026-04-16T10:00:00Z',
          completedAt: '2026-04-16T10:01:00Z',
        },
      ],
      page: filters.page ?? 0,
      size: filters.size ?? 20,
      total: 1,
    })),
  }
}

function renderDetailPage() {
  const workflowMocks = createWorkflowPorts()
  const notifySuccess = vi.fn()
  const notifyError = vi.fn()
  const notifyWarning = vi.fn()
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })

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
      getWorkflowByKey: workflowMocks.getWorkflowByKey,
      updateWorkflow: workflowMocks.updateWorkflow,
      listWorkflowVersions: workflowMocks.listWorkflowVersions,
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
      listWorkflowRunsForKey: workflowMocks.listWorkflowRunsForKey,
      getWorkflowRunDetail: vi.fn(),
      cancelWorkflowRun: vi.fn(),
    },
  } as unknown as AppPorts

  const Wrapper = ({ children }: PropsWithChildren) => (
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <notifyContext.Provider
          value={{
            success: notifySuccess,
            error: notifyError,
            warning: notifyWarning,
            info: vi.fn(),
          }}
        >
          <MemoryRouter initialEntries={['/admin-ui/workflows/wf_a']}>
            <ShellRegionsProvider>
              <Routes>
                <Route path="/admin-ui/workflows/:key" element={children} />
                <Route path="/admin-ui/workflow-runs/:runId" element={<p>run detail route</p>} />
              </Routes>
              <InspectorHost />
            </ShellRegionsProvider>
          </MemoryRouter>
        </notifyContext.Provider>
      </QueryClientProvider>
    </PortsContext.Provider>
  )

  const rendered = render(<WorkflowDetailPage />, { wrapper: Wrapper })
  return { ...rendered, workflowMocks, notifySuccess, notifyError, notifyWarning }
}

describe('workflow detail page actions', () => {
  it('runs validate action and renders validation errors', async () => {
    const user = userEvent.setup()
    const { workflowMocks, notifySuccess, notifyWarning } = renderDetailPage()

    await screen.findByText('Workflow A')
    await user.click(screen.getByRole('button', { name: 'Validate' }))

    await waitFor(() => {
      expect(workflowMocks.validateWorkflow).toHaveBeenCalled()
    })
    expect(await screen.findByText('Missing node transition')).toBeInTheDocument()
    expect(notifySuccess).not.toHaveBeenCalled()
    expect(notifyWarning).toHaveBeenCalledWith('Workflow definition has validation errors.')
  })

  it('confirms activate action and rollback request from inspector', async () => {
    const user = userEvent.setup()
    const { workflowMocks } = renderDetailPage()

    await screen.findByText('Workflow A')

    await user.click(screen.getByRole('button', { name: 'Activate' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => {
      expect(workflowMocks.activateWorkflow).toHaveBeenCalledWith('wf_a')
    })

    const inspector = screen.getByTestId('inspector-host')
    const rollbackButtons = within(inspector).getAllByRole('button', { name: 'Rollback' })
    await user.click(rollbackButtons[1])
    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => {
      expect(workflowMocks.rollbackWorkflow).toHaveBeenCalledWith('wf_a', { toVersion: 1 })
    })
  })

  it('renders scoped runs tab with URL-backed filters and row navigation', async () => {
    const user = userEvent.setup()
    const { workflowMocks } = renderDetailPage()

    await screen.findByText('Workflow A')
    expect(workflowMocks.listWorkflowRunsForKey).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Runs' }))
    expect(screen.getByLabelText('Run Status')).toBeInTheDocument()

    await screen.findByText('44')
    expect(workflowMocks.listWorkflowRunsForKey).toHaveBeenCalledWith(
      'wf_a',
      expect.objectContaining({ page: 0, size: 20 }),
    )

    await user.selectOptions(screen.getByLabelText('Run Status'), 'FAILED')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    expect(workflowMocks.listWorkflowRunsForKey).toHaveBeenLastCalledWith(
      'wf_a',
      expect.objectContaining({ status: 'FAILED', page: 0 }),
    )

    await user.click(screen.getByRole('button', { name: 'Reset' }))
    expect(workflowMocks.listWorkflowRunsForKey).toHaveBeenLastCalledWith(
      'wf_a',
      expect.objectContaining({ status: undefined, page: 0 }),
    )

    await user.click(screen.getByRole('button', { name: 'Open workflow run 44' }))
    expect(await screen.findByText('run detail route')).toBeInTheDocument()
  })
})
