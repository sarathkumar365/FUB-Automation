import '@testing-library/jest-dom/vitest'
import { vi } from 'vitest'

class TestEventSource implements EventSource {
  readonly CONNECTING = 0
  readonly OPEN = 1
  readonly CLOSED = 2
  readonly readyState = 0
  readonly url: string
  readonly withCredentials = false
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null
  onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null
  onopen: ((this: EventSource, ev: Event) => unknown) | null = null

  constructor(url: string | URL) {
    this.url = String(url)
  }

  addEventListener() {
    return
  }

  removeEventListener() {
    return
  }

  dispatchEvent(): boolean {
    return true
  }

  close() {
    return
  }
}

if (typeof globalThis.EventSource === 'undefined') {
  vi.stubGlobal('EventSource', TestEventSource)
}

const defaultFetchMock = vi.fn(async (input: RequestInfo | URL) => {
  const url = typeof input === 'string' ? input : input.toString()

  if (url.startsWith('/admin/webhooks/')) {
    return new Response(
      JSON.stringify({
        id: 1,
        eventId: 'evt-default',
        source: 'FUB',
        eventType: 'callsUpdated',
        status: 'RECEIVED',
        payloadHash: null,
        payload: {},
        receivedAt: '2026-03-19T00:00:00Z',
      }),
      { status: 200 },
    )
  }

  if (url.startsWith('/admin/webhooks')) {
    return new Response(
      JSON.stringify({
        items: [],
        nextCursor: null,
        serverTime: '2026-03-19T00:00:00Z',
      }),
      { status: 200 },
    )
  }

  return new Response(JSON.stringify({}), { status: 200 })
})

vi.stubGlobal('fetch', defaultFetchMock)
