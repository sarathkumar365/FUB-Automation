import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { useShellRegions } from '../app/useShellRegions'
import { PortsContext } from '../app/portsContextValue'
import { WorkflowsPage } from '../modules/workflows/ui/WorkflowsPage'
import type { AppPorts } from '../platform/container'
import type { StepTypeCatalogEntry, TriggerTypeCatalogEntry } from '../modules/workflows/lib/workflowSchemas'
import { uiText } from '../shared/constants/uiText'

function PanelHost() {
  const { panelContent } = useShellRegions()
  return <div data-testid="panel-host">{panelContent?.body}</div>
}

function renderWorkflowsPage(
  initialPath = '/admin-ui/workflows?selectedKey=wf_a',
  overrides?: {
    listStepTypes?: AppPorts['workflowPort']['listStepTypes']
    listTriggerTypes?: AppPorts['workflowPort']['listTriggerTypes']
  },
) {
  window.history.pushState({}, '', initialPath)

  const defaultStepTypes: StepTypeCatalogEntry[] = [
    {
      id: 'step_z',
      displayName: 'Zeta Step',
      description: 'z',
      configSchema: {},
      declaredResultCodes: [],
      defaultRetryPolicy: {},
    },
    {
      id: 'step_a',
      displayName: 'Alpha Step',
      description: 'a',
      configSchema: {},
      declaredResultCodes: [],
      defaultRetryPolicy: {},
    },
  ]
  const defaultTriggerTypes: TriggerTypeCatalogEntry[] = [
    {
      id: 'trigger_b',
      displayName: 'Beta Trigger',
      description: 'b',
      configSchema: {},
    },
    {
      id: 'trigger_a',
      displayName: 'Alpha Trigger',
      description: 'a',
      configSchema: {},
    },
  ]

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
      listStepTypes: overrides?.listStepTypes ?? vi.fn(async () => defaultStepTypes),
      listTriggerTypes: overrides?.listTriggerTypes ?? vi.fn(async () => defaultTriggerTypes),
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
            <PanelHost />
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

  it('renders sorted step and trigger catalogs in the shell panel', async () => {
    renderWorkflowsPage()

    const panelHost = await screen.findByTestId('panel-host')
    await within(panelHost).findByText('Alpha Step')
    await within(panelHost).findByText('Alpha Trigger')
    const panelText = panelHost.textContent ?? ''

    expect(panelText.indexOf('Alpha Step')).toBeLessThan(panelText.indexOf('Zeta Step'))
    expect(panelText.indexOf('Alpha Trigger')).toBeLessThan(panelText.indexOf('Beta Trigger'))
  })

  it('renders catalog loading state while catalog queries are pending', async () => {
    renderWorkflowsPage('/admin-ui/workflows', {
      listStepTypes: vi.fn<AppPorts['workflowPort']['listStepTypes']>(
        () =>
          new Promise(() => {
            /* pending */
          }),
      ),
      listTriggerTypes: vi.fn<AppPorts['workflowPort']['listTriggerTypes']>(
        () =>
          new Promise(() => {
            /* pending */
          }),
      ),
    })

    expect(await screen.findAllByText(uiText.states.loadingMessage)).not.toHaveLength(0)
  })

  it('renders catalog error state when a catalog query fails', async () => {
    renderWorkflowsPage('/admin-ui/workflows', {
      listStepTypes: vi.fn(async () => {
        throw new Error('step catalog failed')
      }),
    })

    const panelHost = await screen.findByTestId('panel-host')
    expect(await within(panelHost).findByText(uiText.states.errorMessage)).toBeInTheDocument()
  })
})
