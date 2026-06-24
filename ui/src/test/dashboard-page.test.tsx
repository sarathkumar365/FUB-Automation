import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '@app/ShellRegionsProvider'
import { PortsContext } from '@app/portsContextValue'
import { DashboardPage } from '@modules/dashboard/ui/DashboardPage'
import type { AppPorts } from '@platform/container'
import type { DashboardSnapshot } from '@platform/contracts/dashboardSchemas'
import { uiText } from '@shared/constants/uiText'

function sampleSnapshot(overrides?: Partial<DashboardSnapshot>): DashboardSnapshot {
  return {
    window: { from: '2026-02-01T00:00:00Z', to: '2026-02-02T00:00:00Z', label: 'Last 24h' },
    hero: {
      state: 'HEALTHY',
      openFailures: 2,
      stats: {
        runs: { value: 20, delta: { value: 5, direction: 'UP' } },
        successRate: { value: 90, delta: { value: -1, direction: 'DOWN' } },
        openFailures: { value: 2, delta: { value: 2, direction: 'UP' } },
      },
    },
    throughput: { series: [1, 2, 3], peak: 3, avg: 2, perMin: 4 },
    funnel: {
      ingested: { value: 100, spark: [1, 2] },
      domainEvents: { value: 90, spark: [1, 2] },
      runs: { value: 20, spark: [1, 2] },
      failed: { value: 2, spark: [1, 2] },
    },
    recentRuns: [
      { id: 1, workflowKey: 'wf_1', status: 'COMPLETED', startedAt: '2026-02-01T10:00:00Z', completedAt: '2026-02-01T10:00:42Z', durationSec: 42 },
    ],
    needsAttention: [{ ref: '7', workflowKey: 'wf_b', status: 'FAILED', reason: 'SIDE_EFFECT_ERROR', ageSeconds: 300 }],
    ...overrides,
  }
}

function renderDashboardPage(getSnapshot?: () => Promise<DashboardSnapshot>) {
  const ports = {
    dashboardPort: { getSnapshot: getSnapshot ?? vi.fn(async () => sampleSnapshot()) },
  } as unknown as AppPorts

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin-ui']}>
          <ShellRegionsProvider>
            <Routes>
              <Route path="/admin-ui" element={<DashboardPage />} />
              <Route path="/admin-ui/workflow-runs/:runId" element={<div>run-detail-page</div>} />
            </Routes>
          </ShellRegionsProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )
}

describe('dashboard page', () => {
  it('renders the health headline, hero stats, and funnel counts', async () => {
    renderDashboardPage()

    expect(await screen.findByRole('heading', { name: uiText.dashboard.health.HEALTHY })).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.runsTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.successRateTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.openFailuresTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.funnelIngested)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.funnelFailed)).toBeInTheDocument()
    expect(screen.getByText('90.0%')).toBeInTheDocument()
  })

  it('navigates to run detail from a recent-run row', async () => {
    renderDashboardPage()

    const row = await screen.findByRole('button', { name: `${uiText.dashboard.runRowAriaLabelPrefix} 1` })
    fireEvent.click(row)

    expect(await screen.findByText('run-detail-page')).toBeInTheDocument()
  })

  it('navigates to run detail from a needs-attention row', async () => {
    renderDashboardPage()

    fireEvent.click(await screen.findByText('#7'))

    expect(await screen.findByText('run-detail-page')).toBeInTheDocument()
  })

  it('renders the loading state while the snapshot is pending', () => {
    renderDashboardPage(() => new Promise(() => {}))

    expect(screen.getByText(uiText.states.loadingTitle)).toBeInTheDocument()
  })

  it('renders the error state when the snapshot request fails', async () => {
    renderDashboardPage(async () => {
      throw new Error('snapshot failed')
    })

    expect(await screen.findByText(uiText.states.errorTitle)).toBeInTheDocument()
  })

  it('renders empty copy when there are no recent runs or failures', async () => {
    renderDashboardPage(async () => sampleSnapshot({ recentRuns: [], needsAttention: [] }))

    expect(await screen.findByText(uiText.dashboard.recentRunsEmpty)).toBeInTheDocument()
    expect(screen.getByText(uiText.dashboard.needsAttentionEmpty)).toBeInTheDocument()
  })
})
