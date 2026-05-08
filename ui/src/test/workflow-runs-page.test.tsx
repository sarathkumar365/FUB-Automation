import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { WorkflowRunsPage } from '../modules/workflow-runs/ui/WorkflowRunsPage'
import type { AppPorts } from '../platform/container'
import type { WorkflowRunListFilters } from '../platform/ports/workflowRunPort'
import { uiText } from '../shared/constants/uiText'

function renderWorkflowRunsPage(initialPath = '/admin-ui/workflow-runs') {
  window.history.pushState({}, '', initialPath)

  const listWorkflowRuns = vi.fn(async (filters: WorkflowRunListFilters) => ({
    items: [
      {
        id: 44,
        workflowKey: 'wf_a',
        workflowVersionNumber: 2,
        status: 'FAILED' as const,
        reasonCode: 'STEP_TIMEOUT',
        startedAt: '2026-04-16T10:00:00Z',
        completedAt: '2026-04-16T10:01:00Z',
      },
      {
        id: 45,
        workflowKey: 'wf_b',
        workflowVersionNumber: 1,
        status: 'COMPLETED' as const,
        reasonCode: null,
        startedAt: '2026-04-16T11:00:00Z',
        completedAt: '2026-04-16T11:01:00Z',
      },
    ],
    page: filters.page ?? 0,
    size: filters.size ?? 20,
    total: 2,
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
      listWorkflowRunsForKey: vi.fn(),
      getWorkflowRunDetail: vi.fn(),
      cancelWorkflowRun: vi.fn(),
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ShellRegionsProvider>
            <Routes>
              <Route path="/admin-ui/workflow-runs" element={<WorkflowRunsPage />} />
              <Route path="/admin-ui/workflow-runs/:runId" element={<p>workflow run detail route</p>} />
            </Routes>
          </ShellRegionsProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { listWorkflowRuns }
}

describe('workflow runs page', () => {
  it('renders run rows and applies status filter with URL sync', async () => {
    const user = userEvent.setup()
    const { listWorkflowRuns } = renderWorkflowRunsPage()

    await screen.findByText('wf_a')
    await user.selectOptions(screen.getByLabelText(uiText.workflowRuns.filterStatusLabel), 'FAILED')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() => {
      expect(listWorkflowRuns).toHaveBeenLastCalledWith(
        expect.objectContaining({
          status: 'FAILED',
        }),
      )
    })
    expect(window.location.search).toContain('status=FAILED')
  })

  it('navigates to detail route on row click with backTo context', async () => {
    const user = userEvent.setup()
    renderWorkflowRunsPage('/admin-ui/workflow-runs?status=FAILED')

    const row = await screen.findByRole('button', { name: 'Open workflow run 44' })
    await user.click(row)

    expect(await screen.findByText('workflow run detail route')).toBeInTheDocument()
    expect(window.location.search).toContain('backTo=%2Fadmin-ui%2Fworkflow-runs%3Fstatus%3DFAILED%26selectedRunId%3D44')
  })
})
