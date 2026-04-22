import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { LeadDetailPage } from '../modules/leads/ui/LeadDetailPage'
import type { AppPorts } from '../platform/container'
import type { LeadSummary } from '../shared/types/lead'
import { NotifyProvider } from '../shared/notifications/NotifyProvider'

function buildSummary(overrides: Partial<LeadSummary> = {}): LeadSummary {
  return {
    lead: {
      id: 7,
      sourceSystem: 'FUB',
      sourceLeadId: '12345',
      status: 'ACTIVE',
      snapshot: { name: 'Jane Doe', email: 'jane@example.com' },
      createdAt: '2026-04-10T10:00:00Z',
      updatedAt: '2026-04-20T10:00:00Z',
      lastSyncedAt: '2026-04-20T10:00:00Z',
    },
    livePerson: null,
    liveStatus: 'LIVE_SKIPPED',
    liveMessage: null,
    activity: [
      {
        kind: 'PROCESSED_CALL',
        refId: 901,
        occurredAt: '2026-04-19T08:00:00Z',
        summary: 'Inbound call connected',
        status: 'PROCESSED',
      },
      {
        kind: 'WORKFLOW_RUN',
        refId: 702,
        occurredAt: '2026-04-18T09:00:00Z',
        summary: 'Assignment follow-up started',
        status: 'RUNNING',
      },
      {
        kind: 'WEBHOOK_EVENT',
        refId: 501,
        occurredAt: '2026-04-17T10:00:00Z',
        summary: 'peopleCreated',
        status: 'ACCEPTED',
      },
    ],
    recentCalls: [],
    recentWorkflowRuns: [],
    recentWebhookEvents: [],
    ...overrides,
  }
}

function renderLeadDetail({
  summary = buildSummary(),
  initialPath = '/admin-ui/leads/12345',
}: { summary?: LeadSummary; initialPath?: string } = {}) {
  const getLeadSummary = vi.fn(async () => summary)
  const ports = {
    leadsPort: {
      listLeads: vi.fn(),
      getLeadSummary,
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  const rendered = render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <NotifyProvider>
          <MemoryRouter initialEntries={[initialPath]}>
            <ShellRegionsProvider>
              <Routes>
                <Route path="/admin-ui/leads/:sourceLeadId" element={<LeadDetailPage />} />
              </Routes>
            </ShellRegionsProvider>
          </MemoryRouter>
        </NotifyProvider>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { ...rendered, getLeadSummary }
}

describe('LeadDetailPage', () => {
  it('renders the hero, rail, and unified activity timeline', async () => {
    renderLeadDetail()

    await waitFor(() => expect(screen.getByText('Jane Doe')).toBeInTheDocument())
    expect(screen.getByText('FUB · 12345')).toBeInTheDocument()
    expect(screen.getByText('Local only')).toBeInTheDocument()

    const rows = await screen.findAllByTestId('lead-activity-row')
    expect(rows).toHaveLength(3)
  })

  it('filters activity by kind when a filter chip is selected', async () => {
    const user = userEvent.setup()
    renderLeadDetail()

    await screen.findAllByTestId('lead-activity-row')

    await user.click(screen.getByRole('tab', { name: 'Calls' }))

    const visibleRows = screen.getAllByTestId('lead-activity-row')
    expect(visibleRows).toHaveLength(1)
    expect(within(visibleRows[0]!).getByText('Inbound call connected')).toBeInTheDocument()
  })

  it('triggers a live refresh request when the refresh button is clicked', async () => {
    const user = userEvent.setup()
    const { getLeadSummary } = renderLeadDetail()

    await screen.findAllByTestId('lead-activity-row')

    const initialCalls = getLeadSummary.mock.calls.length
    await user.click(screen.getByRole('button', { name: /Refresh lead from FUB/i }))

    await waitFor(() => {
      const latest = getLeadSummary.mock.calls.at(-1) as unknown[] | undefined
      expect(latest?.[1]).toMatchObject({ includeLive: true })
      expect(getLeadSummary.mock.calls.length).toBeGreaterThan(initialCalls)
    })
  })
})
