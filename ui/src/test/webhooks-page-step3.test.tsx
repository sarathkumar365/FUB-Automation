import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import type { AppPorts } from '../platform/container'
import type { WebhookStreamCallbacks } from '../platform/ports/webhookStreamPort'
import { uiText } from '../shared/constants/uiText'

function renderWebhooksPageStep3(initialPath = '/admin-ui/webhooks') {
  window.history.pushState({}, '', initialPath)

  const listWebhooks = vi.fn(async (filters: { cursor?: string }) => {
    const isNext = filters.cursor === 'cursor-2'
    return {
      items: [
        {
          id: isNext ? 2 : 1,
          eventId: isNext ? 'evt-2' : 'evt-1',
          source: 'FUB' as const,
          eventType: isNext ? 'callsCreated' : 'callsUpdated',
          status: 'RECEIVED' as const,
          receivedAt: isNext ? '2026-03-19T12:01:00Z' : '2026-03-19T12:00:00Z',
        },
      ],
      nextCursor: isNext ? null : 'cursor-2',
      serverTime: '2026-03-19T12:00:00Z',
    }
  })

  const getWebhookDetail = vi.fn(async (id: number) => ({
    id,
    eventId: `evt-${id}`,
    source: 'FUB' as const,
    eventType: 'callsUpdated',
    status: 'RECEIVED' as const,
    payloadHash: `hash-${id}`,
    payload: { id },
    receivedAt: '2026-03-19T12:00:00Z',
  }))

  const streamCalls: WebhookStreamCallbacks[] = []
  const closeFns: Array<ReturnType<typeof vi.fn>> = []
  const openWebhookStream = vi.fn((_filters: unknown, callbacks: WebhookStreamCallbacks) => {
    streamCalls.push(callbacks)
    const close = vi.fn()
    closeFns.push(close)
    return close
  })

  const ports = {
    adminWebhookPort: {
      listWebhooks,
      getWebhookDetail,
      buildWebhookStreamRequest: vi.fn(() => '/admin/webhooks/stream'),
    },
    processedCallsPort: {
      listProcessedCalls: vi.fn(),
      replayProcessedCall: vi.fn(),
    },
    webhookStreamPort: {
      openWebhookStream,
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ShellRegionsProvider>
            <Routes>
              <Route path="/admin-ui/webhooks" element={<WebhooksPage />} />
            </Routes>
          </ShellRegionsProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return {
    listWebhooks,
    openWebhookStream,
    getStreamCallbacks: () => streamCalls.at(-1),
    getLastCloseFn: () => closeFns.at(-1),
  }
}

describe('Webhooks page Step 3', () => {
  it('pauses by closing stream and applies filters on loaded rows only', async () => {
    const { listWebhooks, openWebhookStream, getLastCloseFn } = renderWebhooksPageStep3()
    const user = userEvent.setup()

    await screen.findByText('evt-1')
    expect(openWebhookStream).toHaveBeenCalledTimes(1)

    await user.click(screen.getByRole('button', { name: uiText.webhooks.pauseStreamAria }))
    expect(getLastCloseFn()).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('webhook-live-state')).toHaveTextContent(uiText.webhooks.liveStatePaused)

    await user.clear(screen.getByLabelText(uiText.webhooks.filterEventTypeLabel))
    await user.type(screen.getByLabelText(uiText.webhooks.filterEventTypeLabel), 'callsCreated')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() => {
      expect(listWebhooks).toHaveBeenCalledTimes(1)
      expect(openWebhookStream).toHaveBeenCalledTimes(1)
    })
    expect(screen.queryByText('evt-1')).not.toBeInTheDocument()
    expect(screen.getByText(uiText.webhooks.tableEmptyMessage)).toBeInTheDocument()
  })

  it('shows View latest before Apply and drains buffered rows', async () => {
    const { getStreamCallbacks } = renderWebhooksPageStep3('/admin-ui/webhooks?cursor=cursor-2')
    const user = userEvent.setup()

    await screen.findByText('evt-2')

    act(() => {
      getStreamCallbacks()?.onEvent('webhook.received', {
        id: 9,
        eventId: 'evt-9',
        source: 'FUB',
        eventType: 'callsCreated',
        status: 'RECEIVED',
        receivedAt: '2026-03-19T12:02:00Z',
      })
    })

    const viewLatestButton = await screen.findByRole('button', { name: uiText.webhooks.viewLatestAria })
    const applyButton = screen.getByRole('button', { name: uiText.filters.apply })
    expect(viewLatestButton.compareDocumentPosition(applyButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.queryByText('evt-9')).not.toBeInTheDocument()

    await user.click(viewLatestButton)
    expect(await screen.findByText('evt-9')).toBeInTheDocument()
  })

  it('renders color-coded stream states for connecting, live, and error', async () => {
    const { getStreamCallbacks } = renderWebhooksPageStep3()

    await screen.findByText('evt-1')
    expect(screen.getByTestId('webhook-live-state')).toHaveTextContent(uiText.webhooks.liveStateConnecting)

    act(() => {
      getStreamCallbacks()?.onEvent('heartbeat', { serverTime: '2026-03-19T12:00:00Z' })
    })
    expect(screen.getByTestId('webhook-live-state')).toHaveTextContent(uiText.webhooks.liveStateLive)

    act(() => {
      getStreamCallbacks()?.onError?.(new Event('error'))
    })
    expect(screen.getByTestId('webhook-live-state')).toHaveTextContent(uiText.webhooks.liveStateError)
  })

  it('always renders activity tick strip even before live events arrive', async () => {
    renderWebhooksPageStep3()

    await screen.findByText('evt-1')
    expect(screen.getByTestId('activity-tick-strip')).toBeInTheDocument()
  })

  it('renders activity strip across full available width', async () => {
    renderWebhooksPageStep3()

    await screen.findByText('evt-1')
    expect(screen.getByTestId('activity-tick-strip')).toHaveClass('w-full')
  })
})
