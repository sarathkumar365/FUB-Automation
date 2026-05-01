import { SseWebhookStreamAdapter } from '../platform/adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from '../platform/ports/adminWebhookPort'
import { beforeEach, describe, expect, it, vi } from 'vitest'

type Listener = (event: Event) => void

class FakeEventSource {
  public listeners = new Map<string, Listener[]>()
  public onerror: ((event: Event) => void) | null = null
  public closed = false
  public url: string

  constructor(url: string) {
    this.url = url
  }

  addEventListener(type: string, listener: Listener) {
    const current = this.listeners.get(type) ?? []
    current.push(listener)
    this.listeners.set(type, current)
  }

  emit(type: string, data: string) {
    const event = new MessageEvent(type, { data })
    const listeners = this.listeners.get(type) ?? []
    listeners.forEach((listener) => listener(event))
  }

  close() {
    this.closed = true
  }
}

describe('SseWebhookStreamAdapter', () => {
  let latestSource: FakeEventSource | null

  beforeEach(() => {
    latestSource = null
    const recordSource = (source: FakeEventSource) => {
      latestSource = source
    }

    class EventSourceMock extends FakeEventSource {
      constructor(url: string) {
        super(url)
        recordSource(this)
      }
    }

    vi.stubGlobal('EventSource', EventSourceMock)
  })

  it('handles webhook and heartbeat events and tears down stream', () => {
    const adminWebhookPort: AdminWebhookPort = {
      listWebhooks: vi.fn(),
      getWebhookDetail: vi.fn(),
      listEventTypes: vi.fn(async () => []),
      buildWebhookStreamRequest: vi.fn(() => '/admin/webhooks/stream?source=FUB'),
    }

    const adapter = new SseWebhookStreamAdapter(adminWebhookPort)
    const received: Array<{ name: string; data: unknown }> = []

    const close = adapter.openWebhookStream(
      { source: 'FUB' },
      {
        onEvent: (name, data) => received.push({ name, data }),
      },
    )

    expect(latestSource).not.toBeNull()
    const fakeSource = latestSource as FakeEventSource

    fakeSource.emit('webhook.received', '{"id":1}')
    fakeSource.emit('heartbeat', '{"serverTime":"2026-03-18T12:00:00Z"}')

    expect(received).toEqual([
      { name: 'webhook.received', data: { id: 1 } },
      { name: 'heartbeat', data: { serverTime: '2026-03-18T12:00:00Z' } },
    ])

    close()
    expect(fakeSource.closed).toBe(true)
  })
})
