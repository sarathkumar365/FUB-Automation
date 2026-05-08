import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { useShellRegions } from '../app/useShellRegions'
import { PortsContext } from '../app/portsContextValue'
import { WorkflowRunDetailPage } from '../modules/workflow-runs/ui/WorkflowRunDetailPage'
import type { AppPorts } from '../platform/container'
import { notifyContext } from '../shared/notifications/notifyContext'
import { uiText } from '../shared/constants/uiText'

function createWorkflowRunDetailPayload(
  status: 'PENDING' | 'BLOCKED' | 'FAILED' | 'COMPLETED' | 'CANCELED' = 'FAILED',
) {
  return {
    id: 44,
    workflowKey: 'wf_a',
    workflowVersionNumber: 2,
    status,
    reasonCode: status === 'FAILED' ? 'STEP_TIMEOUT' : null,
    startedAt: '2026-04-16T10:00:00Z',
    completedAt: status === 'PENDING' || status === 'BLOCKED' ? null : '2026-04-16T10:01:00Z',
    triggerPayload: { event: 'assignment.updated' },
    sourceLeadId: 'lead-11',
    eventId: 'event-88',
    steps: [
      {
        id: 1,
        nodeId: 'n1',
        stepType: 'fub_add_tag',
        status: 'FAILED' as const,
        resultCode: 'HTTP_500',
        outputs: { attempts: 2 },
        errorMessage: 'failure',
        retryCount: 2,
        dueAt: null,
        startedAt: '2026-04-16T10:00:00Z',
        completedAt: '2026-04-16T10:01:00Z',
      },
    ],
  }
}

function InspectorHost() {
  const { inspectorContent } = useShellRegions()
  return <div data-testid="inspector-host">{inspectorContent?.body}</div>
}

function renderWorkflowRunDetailPage(
  status: 'PENDING' | 'BLOCKED' | 'FAILED' | 'COMPLETED' = 'FAILED',
  initialEntry = '/admin-ui/workflow-runs/44',
) {
  const getWorkflowRunDetail = vi.fn(async () => createWorkflowRunDetailPayload(status))
  const cancelWorkflowRun = vi.fn(async () => createWorkflowRunDetailPayload('CANCELED'))
  const notifySuccess = vi.fn()
  const notifyError = vi.fn()

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
      listWorkflowRuns: vi.fn(),
      listWorkflowRunsForKey: vi.fn(),
      getWorkflowRunDetail,
      cancelWorkflowRun,
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <notifyContext.Provider
          value={{
            success: notifySuccess,
            error: notifyError,
            warning: vi.fn(),
            info: vi.fn(),
          }}
        >
          <MemoryRouter initialEntries={[initialEntry]}>
            <ShellRegionsProvider>
              <Routes>
                <Route path="/admin-ui/workflow-runs/:runId" element={<WorkflowRunDetailPage />} />
                <Route path="/admin-ui/workflow-runs" element={<p>workflow runs route</p>} />
                <Route path="/admin-ui/workflows/:key" element={<p>workflow detail route</p>} />
              </Routes>
              <InspectorHost />
            </ShellRegionsProvider>
          </MemoryRouter>
        </notifyContext.Provider>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return {
    getWorkflowRunDetail,
    cancelWorkflowRun,
    notifySuccess,
    notifyError,
    invalidateQueriesSpy,
  }
}

describe('workflow run detail page', () => {
  it('renders metadata and timeline details', async () => {
    renderWorkflowRunDetailPage('FAILED')

    expect(await screen.findByText('Workflow Run Detail')).toBeInTheDocument()
    expect(await screen.findByText('wf_a')).toBeInTheDocument()
    expect(await screen.findByText('fub_add_tag')).toBeInTheDocument()
    expect(await screen.findByText('HTTP_500')).toBeInTheDocument()
    expect(await screen.findByText('Step details')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.workflowRuns.detailBackLabel })).toHaveAttribute('href', '/admin-ui/workflow-runs')
  })

  it('uses valid backTo query for header back link', async () => {
    renderWorkflowRunDetailPage('FAILED', '/admin-ui/workflow-runs/44?backTo=%2Fadmin-ui%2Fworkflows%2Fwf_a%3Ftab%3Druns')

    await screen.findByText('Workflow Run Detail')
    expect(screen.getByRole('link', { name: uiText.workflowRuns.detailBackLabel })).toHaveAttribute(
      'href',
      '/admin-ui/workflows/wf_a?tab=runs',
    )
  })

  it('falls back to workflow runs when backTo query is invalid', async () => {
    renderWorkflowRunDetailPage('FAILED', '/admin-ui/workflow-runs/44?backTo=https%3A%2F%2Fevil.example.com')

    await screen.findByText('Workflow Run Detail')
    expect(screen.getByRole('link', { name: uiText.workflowRuns.detailBackLabel })).toHaveAttribute('href', '/admin-ui/workflow-runs')
  })

  it('links the sourceLeadId to the lead detail page with a backTo', async () => {
    renderWorkflowRunDetailPage('FAILED')

    const link = await screen.findByTestId('workflow-run-source-lead-link')
    expect(link).toHaveTextContent('lead-11')
    expect(link).toHaveAttribute(
      'href',
      `/admin-ui/leads/lead-11?backTo=${encodeURIComponent('/admin-ui/workflow-runs/44')}`,
    )
  })

  it('shows cancel action for pending run', async () => {
    renderWorkflowRunDetailPage('PENDING')
    expect(await screen.findByRole('button', { name: 'Cancel Run' })).toBeInTheDocument()
  })

  it('shows cancel action for blocked run', async () => {
    renderWorkflowRunDetailPage('BLOCKED')
    expect(await screen.findByRole('button', { name: 'Cancel Run' })).toBeInTheDocument()
  })

  it('hides cancel action for non-cancelable statuses', async () => {
    renderWorkflowRunDetailPage('FAILED')
    await screen.findByText('Workflow Run Detail')
    expect(screen.queryByRole('button', { name: 'Cancel Run' })).not.toBeInTheDocument()
  })

  it('runs cancel confirm flow, sends payload, and triggers notifications + invalidation', async () => {
    const user = userEvent.setup()
    const { cancelWorkflowRun, notifySuccess, notifyError, invalidateQueriesSpy } = renderWorkflowRunDetailPage('PENDING')

    await screen.findByText('Workflow Run Detail')
    await user.click(screen.getByRole('button', { name: 'Cancel Run' }))
    expect(screen.getByRole('heading', { name: 'Confirm Run Cancel' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await waitFor(() => {
      expect(cancelWorkflowRun).toHaveBeenCalledWith(44)
    })
    expect(notifySuccess).toHaveBeenCalledWith('Workflow run canceled.')
    expect(notifyError).not.toHaveBeenCalled()
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['workflow-runs', 'detail', 44] }))
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['workflow-runs', 'list'] }))
    expect(invalidateQueriesSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['workflow-runs', 'key', 'wf_a'] }))
  })

  it('shows cancel error notification when mutation fails', async () => {
    const user = userEvent.setup()
    const { cancelWorkflowRun, notifySuccess, notifyError } = renderWorkflowRunDetailPage('PENDING')
    cancelWorkflowRun.mockRejectedValueOnce(new Error('cancel failed'))

    await screen.findByText('Workflow Run Detail')
    await user.click(screen.getByRole('button', { name: 'Cancel Run' }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await waitFor(() => {
      expect(cancelWorkflowRun).toHaveBeenCalledWith(44)
    })
    expect(notifyError).toHaveBeenCalledWith('Unable to cancel workflow run.')
    expect(notifySuccess).not.toHaveBeenCalled()
  })
})
