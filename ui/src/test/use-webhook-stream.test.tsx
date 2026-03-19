import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '../app/portsContextValue'
import type { AppPorts } from '../platform/container'
import type { WebhookStreamCallbacks } from '../platform/ports/webhookStreamPort'
import { useWebhookStream } from '../platform/stream/useWebhookStream'
import type { WebhookStreamFilters } from '../shared/types/webhook'

type StreamCall = {
  filters: WebhookStreamFilters
  callbacks: WebhookStreamCallbacks
}

function setupStreamHook() {
  const closeFns: Array<() => void> = []
  const streamCalls: StreamCall[] = []
  const openWebhookStream = vi.fn((filters: WebhookStreamFilters, callbacks: WebhookStreamCallbacks) => {
    const close = vi.fn()
    closeFns.push(close)
    streamCalls.push({ filters, callbacks })
    return close
  })

  const ports = {
    adminWebhookPort: {
      listWebhooks: vi.fn(),
      getWebhookDetail: vi.fn(),
      buildWebhookStreamRequest: vi.fn(),
    },
    processedCallsPort: {
      listProcessedCalls: vi.fn(),
      replayProcessedCall: vi.fn(),
    },
    webhookStreamPort: {
      openWebhookStream,
    },
  } as unknown as AppPorts

  const wrapper = ({ children }: { children: ReactNode }) => <PortsContext.Provider value={ports}>{children}</PortsContext.Provider>
  return { wrapper, openWebhookStream, streamCalls, closeFns }
}

describe('useWebhookStream', () => {
  it('starts in connecting and opens subscription', () => {
    const { wrapper, openWebhookStream } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })

    expect(result.current.state).toBe('connecting')
    expect(result.current.events).toEqual([])
    expect(result.current.lastHeartbeatAt).toBeNull()
    expect(openWebhookStream).toHaveBeenCalledTimes(1)
  })

  it('transitions to open and records heartbeat time on heartbeat event', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      callbacks.onEvent('heartbeat', { serverTime: '2026-03-19T12:00:00Z' })
    })

    expect(result.current.state).toBe('open')
    expect(result.current.lastHeartbeatAt).not.toBeNull()
  })

  it('transitions to error when stream callback errors', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      callbacks.onError?.(new Event('error'))
    })

    expect(result.current.state).toBe('error')
  })

  it('deduplicates repeated webhook.received events by id', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks
    const payload = {
      id: 55,
      receivedAt: '2026-03-19T12:00:00Z',
      source: 'FUB' as const,
      eventType: 'callsUpdated',
      status: 'RECEIVED' as const,
    }

    act(() => {
      callbacks.onEvent('webhook.received', payload)
      callbacks.onEvent('webhook.received', payload)
    })

    expect(result.current.events).toHaveLength(1)
    expect(result.current.events[0]?.id).toBe('55')
  })

  it('prepends new webhook.received events', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      callbacks.onEvent('webhook.received', {
        id: 1,
        receivedAt: '2026-03-19T12:00:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
      callbacks.onEvent('webhook.received', {
        id: 2,
        receivedAt: '2026-03-19T12:01:00Z',
        source: 'FUB',
        eventType: 'callsCreated',
        status: 'RECEIVED',
      })
    })

    expect(result.current.events.map((event) => event.id)).toEqual(['2', '1'])
  })

  it('retains only the latest 100 unique events', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      for (let id = 1; id <= 101; id += 1) {
        callbacks.onEvent('webhook.received', {
          id,
          receivedAt: `2026-03-19T12:00:${String(id % 60).padStart(2, '0')}Z`,
          source: 'FUB',
          eventType: 'callsUpdated',
          status: 'RECEIVED',
        })
      }
    })

    expect(result.current.events).toHaveLength(100)
    expect(result.current.events[0]?.id).toBe('101')
    expect(result.current.events[result.current.events.length - 1]?.id).toBe('2')
  })

  it('accepts a previously evicted id once it falls out of the 100-event window', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      for (let id = 1; id <= 101; id += 1) {
        callbacks.onEvent('webhook.received', {
          id,
          receivedAt: `2026-03-19T12:00:${String(id % 60).padStart(2, '0')}Z`,
          source: 'FUB',
          eventType: 'callsUpdated',
          status: 'RECEIVED',
        })
      }
      callbacks.onEvent('webhook.received', {
        id: 1,
        receivedAt: '2026-03-19T12:02:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
    })

    expect(result.current.events).toHaveLength(100)
    expect(result.current.events[0]?.id).toBe('1')
    expect(result.current.events.some((event) => event.id === '2')).toBe(false)
  })

  it('ignores webhook payloads without a valid stable id shape', () => {
    const { wrapper, streamCalls } = setupStreamHook()
    const { result } = renderHook(() => useWebhookStream({}), { wrapper })
    const callbacks = streamCalls[0]?.callbacks

    act(() => {
      callbacks.onEvent('webhook.received', {
        receivedAt: '2026-03-19T12:00:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
    })

    expect(result.current.events).toHaveLength(0)
  })

  it('resets stream-derived state for new filters and reconnects', () => {
    const { wrapper, streamCalls, closeFns } = setupStreamHook()
    const { result, rerender } = renderHook(({ filters }) => useWebhookStream(filters), {
      initialProps: { filters: { eventType: 'callsUpdated' } as WebhookStreamFilters },
      wrapper,
    })

    act(() => {
      streamCalls[0]?.callbacks.onEvent('webhook.received', {
        id: 101,
        receivedAt: '2026-03-19T12:00:00Z',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
      })
      streamCalls[0]?.callbacks.onEvent('heartbeat', { serverTime: '2026-03-19T12:00:05Z' })
    })

    expect(result.current.state).toBe('open')
    expect(result.current.events).toHaveLength(1)
    expect(result.current.lastHeartbeatAt).not.toBeNull()

    act(() => {
      rerender({ filters: { eventType: 'callsCreated' } })
    })

    expect(closeFns[0]).toHaveBeenCalledTimes(1)
    expect(result.current.state).toBe('connecting')
    expect(result.current.events).toEqual([])
    expect(result.current.lastHeartbeatAt).toBeNull()
  })

  it('does not reconnect when filters object identity changes with same semantic values', () => {
    const { wrapper, openWebhookStream, closeFns } = setupStreamHook()
    const { rerender } = renderHook(({ filters }) => useWebhookStream(filters), {
      initialProps: { filters: {} as WebhookStreamFilters },
      wrapper,
    })

    rerender({ filters: {} })
    rerender({ filters: { source: undefined, status: undefined, eventType: undefined } })

    expect(openWebhookStream).toHaveBeenCalledTimes(1)
    expect(closeFns[0]).not.toHaveBeenCalled()
  })
})
