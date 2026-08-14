import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '@app/ShellRegionsProvider'
import { PortsContext } from '@app/portsContextValue'
import { useShellRegions } from '@app/useShellRegions'
import type { AppPorts } from '@platform/container'
import type {
  AccountabilityReport,
  SourceContactReport,
  UnreachedLeads,
} from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { ReportsPage } from '@modules/reports/ui/ReportsPage'
import { sampleReport } from './reportingFixtures'

// No EChart mock needed: the wrapper detects jsdom's missing canvas and
// renders just its labelled host div; the chart itself is covered by the
// pure option-builder tests.

const t = uiText.reports

function emptyReport(eventsReceived: number): SourceContactReport {
  const base = sampleReport()
  return {
    window: { ...base.window, eventsReceived },
    totals: { leads: 0, spoke: 0, attempted: 0, nothing: 0 },
    sources: [],
  }
}

function agentsReport(): AccountabilityReport {
  const base = sampleReport()
  return {
    window: base.window,
    totals: { leads: 30, spoke: 8, attempted: 5, nothing: 17 },
    agents: [
      { agentId: 1, agentName: 'Mandeep', counts: { leads: 12, spoke: 6, attempted: 1, nothing: 5 } },
      { agentId: 31, agentName: 'Arjun', counts: { leads: 18, spoke: 2, attempted: 4, nothing: 12 } },
    ],
  }
}

function worklist(): UnreachedLeads {
  const base = sampleReport()
  return {
    window: base.window,
    agentId: 31,
    agentName: 'Arjun',
    limit: 200,
    truncated: true,
    leads: [
      {
        sourcePersonId: '123',
        name: 'Ava Whitfield',
        phone: '+14155550100',
        email: null,
        source: 'Facebook',
        arrivedAt: '2026-08-12T15:00:00Z',
      },
    ],
  }
}

/** The shell renders inspector content elsewhere; mount it here so it can be asserted. */
function InspectorOutlet() {
  const { inspectorContent } = useShellRegions()
  return inspectorContent ? <aside aria-label="test-inspector">{inspectorContent.body}</aside> : null
}

function renderReportsPage(options?: {
  initialEntry?: string
  lead?: () => Promise<SourceContactReport>
  agents?: () => Promise<AccountabilityReport>
  unreached?: () => Promise<UnreachedLeads>
}) {
  const reportingPort = {
    getSourceContactReport: vi.fn(options?.lead ?? (async () => sampleReport())),
    getAccountabilityReport: vi.fn(options?.agents ?? (async () => agentsReport())),
    getUnreachedLeads: vi.fn(options?.unreached ?? (async () => worklist())),
  }
  const ports = { reportingPort } as unknown as AppPorts
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[options?.initialEntry ?? '/admin-ui/reports']}>
          <ShellRegionsProvider>
            <Routes>
              <Route
                path="/admin-ui/reports"
                element={
                  <>
                    <ReportsPage />
                    <InspectorOutlet />
                  </>
                }
              />
            </Routes>
          </ShellRegionsProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { reportingPort }
}

describe('reports page — lead flow', () => {
  it('renders the narration with the three outcome chips', async () => {
    renderReportsPage()

    expect(await screen.findByText(t.narration.spokeChip(6))).toBeInTheDocument()
    expect(screen.getByText(t.narration.attemptedChip(9))).toBeInTheDocument()
    expect(screen.getByText(t.narration.nothingChip(10))).toBeInTheDocument()
    expect(screen.getByRole('img', { name: t.flow.chartAriaLabel })).toBeInTheDocument()
  })

  it('switches to the ledger view with its tree headers', async () => {
    renderReportsPage()
    await screen.findByText(t.narration.spokeChip(6))

    fireEvent.click(screen.getByRole('button', { name: t.flow.viewLedger }))

    expect(screen.getByText(t.ledger.sourceHeader)).toBeInTheDocument()
    expect(screen.getByText('Facebook')).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: t.flow.chartAriaLabel })).not.toBeInTheDocument()
  })

  it('a quiet empty window offers the This Week escape hatch', async () => {
    const { reportingPort } = renderReportsPage({ lead: async () => emptyReport(84) })

    expect(await screen.findByText(t.states.emptyKicker)).toBeInTheDocument()
    expect(screen.getByText(t.states.emptyMessage)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: t.states.emptyCta }))
    expect(reportingPort.getSourceContactReport).toHaveBeenCalledWith('this-week')
  })

  it('an empty window with zero events is called out as a possible feed outage', async () => {
    renderReportsPage({ lead: async () => emptyReport(0) })

    expect(await screen.findByText(t.states.emptyFeedDownMessage)).toBeInTheDocument()
  })
})

describe('reports page — agent accountability', () => {
  const entry = '/admin-ui/reports?report=agents'

  it('ranks agents by the red number by default', async () => {
    renderReportsPage({ initialEntry: entry })

    const rows = await screen.findAllByRole('button', { name: /Open worklist for/ })
    // Arjun has 12 untouched vs Mandeep's 5 — accountability lens puts him first.
    expect(rows[0]).toHaveAccessibleName(t.table.rowAriaLabel('Arjun'))
    expect(rows[1]).toHaveAccessibleName(t.table.rowAriaLabel('Mandeep'))
  })

  it('drops the zero clauses instead of praising a 0% caller or accusing 0 untouched', async () => {
    const base = agentsReport()
    renderReportsPage({
      initialEntry: entry,
      agents: async () => ({
        ...base,
        totals: { leads: 5, spoke: 0, attempted: 5, nothing: 0 },
        agents: [
          { agentId: 1, agentName: 'Mandeep', counts: { leads: 5, spoke: 0, attempted: 5, nothing: 0 } },
        ],
      }),
    })

    await screen.findByRole('button', { name: t.table.rowAriaLabel('Mandeep') })

    expect(screen.queryByText(t.narration.worstChip(0))).not.toBeInTheDocument()
    expect(screen.queryByText(/largest share of their book/)).not.toBeInTheDocument()
  })

  it('opens the worklist inspector with the leads and the truncation note', async () => {
    const { reportingPort } = renderReportsPage({ initialEntry: entry })

    fireEvent.click(await screen.findByRole('button', { name: t.table.rowAriaLabel('Arjun') }))

    const inspector = await screen.findByLabelText('test-inspector')
    expect(await within(inspector).findByText('Ava Whitfield')).toBeInTheDocument()
    expect(within(inspector).getByText(t.inspector.truncatedNote(200))).toBeInTheDocument()
    expect(reportingPort.getUnreachedLeads).toHaveBeenCalledWith(31, 'yesterday')
  })
})
