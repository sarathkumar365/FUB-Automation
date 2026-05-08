import { SseWebhookStreamAdapter } from '../platform/adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from '../platform/ports/adminWebhookPort'
import {
  __resetTokenStoreCacheForTests,
  setToken,
} from '../modules/auth/state/tokenStore'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Builds a fake fetch response that streams the given SSE-formatted body.
 * Each event in `events` becomes one `event: ...\ndata: ...\n\n` block.
 */
function sseResponse(events: Array<{ name: string; data: string }>): Response {
  const encoder = new TextEncoder()
  const body = events
    .map((event) => `event: ${event.name}\ndata: ${event.data}\n\n`)
    .join('')
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(body))
      controller.close()
    },
  })
  return new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

describe('SseWebhookStreamAdapter', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => sseResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  function buildPort(streamUrl = '/admin/webhooks/stream?source=FUB'): AdminWebhookPort {
    return {
      listWebhooks: vi.fn(),
      getWebhookDetail: vi.fn(),
      listEventTypes: vi.fn(async () => []),
      buildWebhookStreamRequest: vi.fn(() => streamUrl),
    }
  }

  it('attaches Authorization: Bearer header when a token is in the store', async () => {
    setToken({
      token: 'jwt.value',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })

    const adapter = new SseWebhookStreamAdapter(buildPort())
    const close = adapter.openWebhookStream({ source: 'FUB' }, { onEvent: () => {} })

    // Wait one microtask tick for fetchEventSource to invoke fetch.
    await Promise.resolve()
    await Promise.resolve()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer jwt.value')
    // Token must NOT appear in the URL — that was the whole point of the migration.
    const requestedUrl = String(fetchMock.mock.calls[0][0])
    expect(requestedUrl).not.toContain('token=')
    expect(requestedUrl).toBe('/admin/webhooks/stream?source=FUB')

    close()
  })

  it('omits Authorization when no token is set', async () => {
    const adapter = new SseWebhookStreamAdapter(buildPort())
    const close = adapter.openWebhookStream({ source: 'FUB' }, { onEvent: () => {} })

    await Promise.resolve()
    await Promise.resolve()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()

    close()
  })

  it('parses webhook.received and heartbeat events into onEvent callbacks', async () => {
    setToken({
      token: 'jwt.value',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    fetchMock.mockResolvedValue(
      sseResponse([
        { name: 'webhook.received', data: '{"id":1}' },
        { name: 'heartbeat', data: '{"serverTime":"2026-03-18T12:00:00Z"}' },
      ]),
    )

    const adapter = new SseWebhookStreamAdapter(buildPort())
    const received: Array<{ name: string; data: unknown }> = []
    adapter.openWebhookStream(
      { source: 'FUB' },
      { onEvent: (name, data) => received.push({ name, data }) },
    )

    // Allow the stream to drain.
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(received).toEqual([
      { name: 'webhook.received', data: { id: 1 } },
      { name: 'heartbeat', data: { serverTime: '2026-03-18T12:00:00Z' } },
    ])
  })

  it('teardown via close() aborts the underlying fetch', async () => {
    setToken({
      token: 'jwt.value',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })

    const adapter = new SseWebhookStreamAdapter(buildPort())
    const close = adapter.openWebhookStream({ source: 'FUB' }, { onEvent: () => {} })

    await Promise.resolve()
    await Promise.resolve()

    const init = fetchMock.mock.calls[0][1] as RequestInit
    const signal = init.signal as AbortSignal
    expect(signal.aborted).toBe(false)

    close()
    expect(signal.aborted).toBe(true)
  })
})
