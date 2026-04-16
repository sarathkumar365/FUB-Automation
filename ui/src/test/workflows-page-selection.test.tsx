import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { WorkflowsPage } from '../modules/workflows/ui/WorkflowsPage'
import type { AppPorts } from '../platform/container'

function renderWorkflowsPage(initialPath = '/admin-ui/workflows?selectedKey=wf_a') {
  window.history.pushState({}, '', initialPath)

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
      listWorkflows: vi.fn(async () => ({
        items: [
          {
            id: 1,
            key: 'wf_a',
            name: 'Workflow A',
            description: null,
            trigger: {},
            graph: {},
            status: 'DRAFT' as const,
            versionNumber: 1,
            version: 1,
          },
        ],
        page: 0,
        size: 20,
        total: 1,
      })),
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
              <Route path="/admin-ui/workflows" element={<WorkflowsPage />} />
            </Routes>
          </ShellRegionsProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )
}

describe('workflows page selection', () => {
  it('applies selected state when selectedKey matches row key', async () => {
    renderWorkflowsPage()

    const row = await screen.findByRole('button', { name: 'Open workflow wf_a' })
    expect(row).toHaveAttribute('aria-pressed', 'true')
  })
})
