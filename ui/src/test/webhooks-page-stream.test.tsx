import { act, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { PortsContext } from '../app/portsContextValue'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import type { WebhookStreamCallbacks } from '../platform/ports/webhookStreamPort'
import type { AppPorts } from '../platform/container'
import type { WebhookStreamFilters } from '../shared/types/webhook'

type StreamCall = {
  filters: WebhookStreamFilters
  callbacks: WebhookStreamCallbacks
}

function renderWebhooksPage() {
  const streamCalls: StreamCall[] = []
  const openWebhookStream = vi.fn((filters: WebhookStreamFilters, callbacks: WebhookStreamCallbacks) => {
    streamCalls.push({ filters, callbacks })
    return vi.fn()
  })

  const ports = {
    adminWebhookPort: {
      listWebhooks: vi.fn(),
      getWebhookDetail: vi.fn(),
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

  const wrapper = ({ children }: { children: ReactNode }) => (
    <PortsContext.Provider value={ports}>
      <ShellRegionsProvider>{children}</ShellRegionsProvider>
    </PortsContext.Provider>
  )

  const rendered = render(<WebhooksPage />, { wrapper })
  return { ...rendered, streamCalls, openWebhookStream }
}

describe('WebhooksPage stream integration', () => {
  it('prepends new events and ignores duplicate stream ids', () => {
    const { streamCalls } = renderWebhooksPage()
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      callbacks.onEvent('webhook.received', {
        id: 'evt-1',
        receivedAt: '2026-03-19T12:00:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
      callbacks.onEvent('webhook.received', {
        id: 'evt-1',
        receivedAt: '2026-03-19T12:00:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
      callbacks.onEvent('webhook.received', {
        id: 'evt-2',
        receivedAt: '2026-03-19T12:01:00Z',
        source: 'FUB',
        eventType: 'callsCreated',
        status: 'RECEIVED',
      })
    })

    expect(screen.getAllByText('evt-1')).toHaveLength(1)
    expect(screen.getAllByText('evt-2')).toHaveLength(1)

    const streamRows = screen
      .getAllByRole('row')
      .map((row) => row.textContent ?? '')
      .filter((text) => text.includes('evt-'))

    expect(streamRows[0]).toContain('evt-2')
    expect(streamRows[1]).toContain('evt-1')
  })

  it('keeps a single stream subscription across component rerenders', () => {
    const { openWebhookStream, rerender } = renderWebhooksPage()

    rerender(<WebhooksPage />)
    rerender(<WebhooksPage />)

    expect(openWebhookStream).toHaveBeenCalledTimes(1)
  })
})
