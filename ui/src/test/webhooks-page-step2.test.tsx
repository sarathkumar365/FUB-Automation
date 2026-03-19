import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import type { AppPorts } from '../platform/container'
import { uiText } from '../shared/constants/uiText'

function renderWebhooksPage(initialPath = '/admin-ui/webhooks') {
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
      openWebhookStream: vi.fn(),
    },
  } as unknown as AppPorts

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  const rendered = render(
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

  return { ...rendered, listWebhooks, getWebhookDetail }
}

describe('Webhooks page Step 2', () => {
  it('loads default list using limit=25', async () => {
    const { listWebhooks } = renderWebhooksPage()

    await waitFor(() => {
      expect(listWebhooks).toHaveBeenCalled()
    })

    expect(listWebhooks.mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({
        limit: 25,
      }),
    )
  })

  it('applies filters with YYYY-MM-DD dates and resets URL state', async () => {
    const { listWebhooks } = renderWebhooksPage()
    const user = userEvent.setup()

    await screen.findByText('evt-1')

    await user.clear(screen.getByLabelText(uiText.webhooks.filterEventTypeLabel))
    await user.type(screen.getByLabelText(uiText.webhooks.filterEventTypeLabel), 'callsCreated')
    await user.type(screen.getByLabelText(uiText.webhooks.filterFromLabel), '2026-03-01')
    await user.type(screen.getByLabelText(uiText.webhooks.filterToLabel), '2026-03-19')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() => {
      const lastCall = listWebhooks.mock.calls.at(-1)?.[0]
      expect(lastCall).toEqual(
        expect.objectContaining({
          eventType: 'callsCreated',
          from: '2026-03-01',
          to: '2026-03-19',
        }),
      )
    })

    expect(window.location.search).toContain('eventType=callsCreated')
    expect(window.location.search).toContain('from=2026-03-01')
    expect(window.location.search).toContain('to=2026-03-19')

    await user.click(screen.getByRole('button', { name: uiText.filters.reset }))

    await waitFor(() => {
      expect(window.location.search).toBe('')
    })
  })

  it('requests next cursor page and replaces rows', async () => {
    const { listWebhooks } = renderWebhooksPage()
    const user = userEvent.setup()

    await screen.findByText('evt-1')
    await user.click(screen.getByRole('button', { name: uiText.webhooks.paginationNextAria }))

    await waitFor(() => {
      const lastCall = listWebhooks.mock.calls.at(-1)?.[0]
      expect(lastCall).toEqual(expect.objectContaining({ cursor: 'cursor-2' }))
    })

    expect(await screen.findByText('evt-2')).toBeInTheDocument()
  })

  it('selects a row and loads detail into inspector', async () => {
    const { getWebhookDetail } = renderWebhooksPage()
    const user = userEvent.setup()

    const eventCell = await screen.findByText('evt-1')
    const row = eventCell.closest('tr')
    if (!row) {
      throw new Error('Expected row for evt-1')
    }

    await user.click(row)

    await waitFor(() => {
      expect(getWebhookDetail).toHaveBeenCalledWith(1)
    })
    expect(window.location.search).toContain('selectedId=1')
  })

  it('supports keyboard row selection using Enter', async () => {
    const { getWebhookDetail } = renderWebhooksPage()

    const eventCell = await screen.findByText('evt-1')
    const row = eventCell.closest('tr')
    if (!row) {
      throw new Error('Expected row for evt-1')
    }

    row.focus()
    fireEvent.keyDown(row, { key: 'Enter' })

    await waitFor(() => {
      expect(getWebhookDetail).toHaveBeenCalledWith(1)
    })
  })

  it('renders a single filter icon at the start of the filter bar', async () => {
    const { container } = renderWebhooksPage()

    await screen.findByText('evt-1')

    expect(container.querySelectorAll('[data-testid="webhook-filter-icon"]')).toHaveLength(1)
  })
})
