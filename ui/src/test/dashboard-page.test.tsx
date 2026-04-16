import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { DashboardPage } from '../modules/dashboard/ui/DashboardPage'
import type { AppPorts } from '../platform/container'
import { uiText } from '../shared/constants/uiText'

function renderDashboardPage(overrides?: {
  listWorkflows?: AppPorts['workflowPort']['listWorkflows']
  listWorkflowRuns?: AppPorts['workflowRunPort']['listWorkflowRuns']
  listWebhooks?: AppPorts['adminWebhookPort']['listWebhooks']
}) {
  const listWorkflows =
    overrides?.listWorkflows ??
    vi.fn(async () => ({
      items: [],
      page: 0,
      size: 1,
      total: 4,
    }))
  const listWorkflowRuns =
    overrides?.listWorkflowRuns ??
    vi.fn(async (filters) => ({
      items:
        filters?.status === 'FAILED'
          ? [buildRun(101, 'FAILED'), buildRun(102, 'FAILED'), buildRun(103, 'FAILED'), buildRun(104, 'FAILED'), buildRun(105, 'FAILED'), buildRun(106, 'FAILED')]
          : [buildRun(1, 'COMPLETED'), buildRun(2, 'FAILED'), buildRun(3, 'COMPLETED'), buildRun(4, 'BLOCKED'), buildRun(5, 'PENDING'), buildRun(6, 'FAILED')],
      page: 0,
      size: 5,
      total: filters?.status === 'FAILED' ? 12 : 20,
    }))
  const listWebhooks =
    overrides?.listWebhooks ??
    vi.fn(async () => ({
      items: [
        {
          id: 1,
          eventId: 'ev_1',
          source: 'FUB' as const,
          eventType: 'callsCreated',
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

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin-ui']}>
          <ShellRegionsProvider>
            <Routes>
              <Route path="/admin-ui" element={<DashboardPage />} />
            </Routes>
          </ShellRegionsProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )
}

describe('dashboard page', () => {
  it('renders dashboard cards, deep links, and recent lists limited to five rows', async () => {
    renderDashboardPage()

    expect(await screen.findByRole('heading', { name: uiText.dashboard.title })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: uiText.dashboard.activeWorkflowsTitle })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.dashboard.recentRunsTitle })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.dashboard.failedRunsTitle })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.dashboard.systemHealthTitle })).toBeInTheDocument()

    expect(screen.getByRole('link', { name: uiText.dashboard.openWorkflows })).toHaveAttribute('href', '/admin-ui/workflows?status=ACTIVE')
    expect(screen.getByRole('link', { name: uiText.dashboard.openRuns })).toHaveAttribute('href', '/admin-ui/workflow-runs')
    expect(screen.getByRole('link', { name: uiText.dashboard.openFailedRuns })).toHaveAttribute('href', '/admin-ui/workflow-runs?status=FAILED')

    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.queryByText('6')).not.toBeInTheDocument()
    expect(screen.queryByText('106')).not.toBeInTheDocument()
  })

  it('renders loading state while snapshot query is pending', () => {
    renderDashboardPage({
      listWorkflows: vi.fn<AppPorts['workflowPort']['listWorkflows']>(
        () =>
          new Promise(() => {
            /* pending */
          }),
      ),
    })

    expect(screen.getByText(uiText.states.loadingTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.states.loadingMessage)).toBeInTheDocument()
  })

  it('renders error state when snapshot aggregation fails', async () => {
    renderDashboardPage({
      listWorkflowRuns: vi.fn(async () => {
        throw new Error('runs failed')
      }),
    })

    expect(await screen.findByText(uiText.states.errorTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.states.errorMessage)).toBeInTheDocument()
  })

  it('renders empty-state copy for recent and failed runs cards when no runs exist', async () => {
    renderDashboardPage({
      listWorkflows: vi.fn(async () => ({
        items: [],
        page: 0,
        size: 1,
        total: 0,
      })),
      listWorkflowRuns: vi.fn(async () => ({
        items: [],
        page: 0,
        size: 5,
        total: 0,
      })),
      listWebhooks: vi.fn(async () => ({
        items: [],
        nextCursor: null,
        serverTime: '2026-04-16T10:00:00Z',
      })),
    })

    expect(await screen.findByText(uiText.dashboard.recentRunsEmpty)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.failedRunsEmpty)).toBeInTheDocument()
  })
})

function buildRun(id: number, status: 'PENDING' | 'BLOCKED' | 'DUPLICATE_IGNORED' | 'CANCELED' | 'COMPLETED' | 'FAILED') {
  return {
    id,
    workflowKey: `wf_${id}`,
    workflowVersionNumber: 1,
    status,
    reasonCode: null,
    startedAt: null,
    completedAt: null,
  }
}
