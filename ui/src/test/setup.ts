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
