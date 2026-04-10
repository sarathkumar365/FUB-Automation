import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { ProcessedCallsPage } from '../modules/processed-calls/ui/ProcessedCallsPage'
import { toProcessedCallsApiDateFilters } from '../modules/processed-calls/lib/processedCallDateFilters'
import type { AppPorts } from '../platform/container'
import type { ProcessedCallFilters } from '../platform/ports/processedCallsPort'
import { uiText } from '../shared/constants/uiText'
import { NotifyProvider } from '../shared/notifications/NotifyProvider'

function renderProcessedCallsPage(initialPath = '/admin-ui/processed-calls') {
  window.history.pushState({}, '', initialPath)

  const listProcessedCalls = vi.fn(async (filters: ProcessedCallFilters) => {
    const rows = [
      {
        callId: 2001,
        status: 'FAILED' as const,
        ruleApplied: null,
        taskId: null,
        failureReason: 'TRANSIENT_FETCH_FAILURE:503',
        retryCount: 2,
        updatedAt: '2026-03-19T12:00:00Z',
      },
      {
        callId: 2002,
        status: 'SKIPPED' as const,
        ruleApplied: 'CONNECTED_NO_FOLLOWUP',
        taskId: null,
        failureReason: null,
        retryCount: 0,
        updatedAt: '2026-03-19T12:01:00Z',
      },
    ]

    return filters.status ? rows.filter((row) => row.status === filters.status) : rows
  })
  const replayProcessedCall = vi.fn(async () => ({ message: 'Replay accepted' }))

  const ports = {
    adminWebhookPort: {
      listWebhooks: vi.fn(),
      getWebhookDetail: vi.fn(),
      listEventTypes: vi.fn(async () => []),
      buildWebhookStreamRequest: vi.fn(() => '/admin/webhooks/stream'),
    },
    processedCallsPort: {
      listProcessedCalls,
      replayProcessedCall,
    },
    webhookStreamPort: {
      openWebhookStream: vi.fn(),
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  const rendered = render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <NotifyProvider>
          <BrowserRouter>
            <ShellRegionsProvider>
              <Routes>
                <Route path="/admin-ui/processed-calls" element={<ProcessedCallsPage />} />
              </Routes>
            </ShellRegionsProvider>
          </BrowserRouter>
        </NotifyProvider>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { ...rendered, listProcessedCalls, replayProcessedCall }
}

describe('Processed Calls page Step 4', () => {
  it('loads default list using limit=25', async () => {
    const { listProcessedCalls } = renderProcessedCallsPage()

    await waitFor(() => {
      expect(listProcessedCalls).toHaveBeenCalled()
    })

    expect(listProcessedCalls.mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({
        limit: 25,
      }),
    )
  })

  it('applies and resets status/date filters with URL sync', async () => {
    const { listProcessedCalls } = renderProcessedCallsPage()
    const user = userEvent.setup()
    const expectedDateFilters = toProcessedCallsApiDateFilters({
      from: '2026-03-01',
      to: '2026-03-19',
    })

    await screen.findByText('2001')

    await user.selectOptions(screen.getByLabelText(uiText.processedCalls.filterStatusLabel), 'FAILED')
    await user.type(screen.getByLabelText(uiText.processedCalls.filterFromLabel), '2026-03-01')
    await user.type(screen.getByLabelText(uiText.processedCalls.filterToLabel), '2026-03-19')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() => {
      const lastCall = listProcessedCalls.mock.calls.at(-1)?.[0]
      expect(lastCall).toEqual(
        expect.objectContaining({
          status: 'FAILED',
          from: expectedDateFilters.from,
          to: expectedDateFilters.to,
        }),
      )
    })

    expect(window.location.search).toContain('status=FAILED')
    expect(window.location.search).toContain('from=2026-03-01')
    expect(window.location.search).toContain('to=2026-03-19')

    await user.click(screen.getByRole('button', { name: uiText.filters.reset }))

    await waitFor(() => {
      expect(window.location.search).toBe('')
    })
  })

  it('enables replay only for FAILED rows and keeps icon-first accessibility labels', async () => {
    renderProcessedCallsPage()

    const failedReplayButton = await screen.findByTestId('processed-calls-replay-2001')
    const skippedReplayButton = screen.getByTestId('processed-calls-replay-2002')

    expect(failedReplayButton).toBeEnabled()
    expect(failedReplayButton).toHaveAttribute('aria-label', `${uiText.processedCalls.replayAriaPrefix} 2001`)
    expect(failedReplayButton).toHaveAttribute('title', uiText.processedCalls.replayTooltip)
    expect(failedReplayButton.querySelector('svg')).not.toBeNull()

    expect(skippedReplayButton).toBeDisabled()
    expect(skippedReplayButton).toHaveAttribute('title', uiText.processedCalls.replayDisabledTooltip)
  })

  it('requires confirmation before replay execution', async () => {
    const { replayProcessedCall } = renderProcessedCallsPage()
    const user = userEvent.setup()

    await screen.findByText('2001')

    await user.click(screen.getByTestId('processed-calls-replay-2001'))
    expect(screen.getByRole('heading', { name: uiText.processedCalls.replayConfirmTitle })).toBeInTheDocument()
    expect(replayProcessedCall).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: uiText.processedCalls.replayConfirmAction }))

    await waitFor(() => {
      expect(replayProcessedCall).toHaveBeenCalledWith(2001)
    })
  })

  it('shows success feedback and refetches list on replay 202 response', async () => {
    const { listProcessedCalls } = renderProcessedCallsPage()
    const user = userEvent.setup()

    await screen.findByText('2001')
    await user.click(screen.getByTestId('processed-calls-replay-2001'))
    await user.click(screen.getByRole('button', { name: uiText.processedCalls.replayConfirmAction }))

    expect(await screen.findByText(uiText.processedCalls.replayAcceptedMessage)).toBeInTheDocument()
    await waitFor(() => {
      expect(listProcessedCalls.mock.calls.length).toBeGreaterThan(1)
    })
  })

  it('maps replay 404 responses to explicit user feedback', async () => {
    const user = userEvent.setup()
    const { replayProcessedCall } = renderProcessedCallsPage()

    replayProcessedCall.mockRejectedValueOnce({ status: 404 })
    await screen.findByText('2001')
    await user.click(screen.getByTestId('processed-calls-replay-2001'))
    await user.click(screen.getByRole('button', { name: uiText.processedCalls.replayConfirmAction }))
    expect(await screen.findByText(uiText.processedCalls.replayNotFoundMessage)).toBeInTheDocument()
  })

  it('maps replay 409 responses to explicit user feedback', async () => {
    const user = userEvent.setup()
    const { replayProcessedCall } = renderProcessedCallsPage()

    replayProcessedCall.mockRejectedValueOnce({ status: 409 })
    await screen.findByText('2001')
    await user.click(screen.getByTestId('processed-calls-replay-2001'))
    await user.click(screen.getByRole('button', { name: uiText.processedCalls.replayConfirmAction }))
    expect(await screen.findByText(uiText.processedCalls.replayNotReplayableMessage)).toBeInTheDocument()
  })

  it('keeps page layout height-bounded and scrolls only history region', async () => {
    renderProcessedCallsPage()

    await screen.findByText('2001')

    expect(screen.getByTestId('processed-calls-page-layout').className).toContain('h-full')
    expect(screen.getByTestId('processed-calls-page-layout').className).toContain('min-h-0')
    expect(screen.getByTestId('processed-calls-history-scroll').className).toContain('overflow-y-auto')
  })
})
