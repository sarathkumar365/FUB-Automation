import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '@app/ShellRegionsProvider'
import { PortsContext } from '@app/portsContextValue'
import { WorkflowsPage } from '@modules/workflows/ui/WorkflowsPage'
import type { AppPorts } from '@platform/container'
import { uiText } from '@shared/constants/uiText'

function setup(initialPath = '/admin-ui/workflows') {
  window.history.pushState({}, '', initialPath)

  const listWorkflows = vi.fn(async () => ({ items: [], page: 0, size: 20, total: 0 }))

  const ports = {
    adminWebhookPort: {},
    processedCallsPort: {},
    webhookStreamPort: {},
    workflowPort: {
      listWorkflows,
      createWorkflow: vi.fn(),
      listStepTypes: vi.fn(async () => []),
      listTriggerTypes: vi.fn(async () => ({ shape: '', eventKinds: [] })),
    },
    workflowRunPort: {},
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
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

  return { listWorkflows }
}

describe('workflows page filters', () => {
  it('applies the selected status and resets the page to 0', async () => {
    const user = userEvent.setup()
    const { listWorkflows } = setup('/admin-ui/workflows?page=2')

    await user.selectOptions(screen.getByLabelText(uiText.workflows.filterStatusLabel), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() =>
      expect(listWorkflows).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ACTIVE', page: 0 })),
    )
  })

  it('reset clears the status filter back to all', async () => {
    const user = userEvent.setup()
    const { listWorkflows } = setup('/admin-ui/workflows?status=ACTIVE')

    await user.click(screen.getByRole('button', { name: uiText.filters.reset }))

    await waitFor(() =>
      expect(listWorkflows).toHaveBeenLastCalledWith(expect.objectContaining({ status: undefined, page: 0 })),
    )
    expect(screen.getByLabelText(uiText.workflows.filterStatusLabel)).toHaveValue('ALL')
  })
})
