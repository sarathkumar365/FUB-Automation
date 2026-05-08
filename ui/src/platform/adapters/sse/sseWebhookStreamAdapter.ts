import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { WebhookStreamCallbacks, WebhookStreamPort } from '../../ports/webhookStreamPort'
import type { WebhookStreamFilters } from '../../../shared/types/webhook'
import type { AdminWebhookPort } from '../../ports/adminWebhookPort'
import { getToken } from '../../../modules/auth/state/tokenStore'

/**
 * SSE consumer for the admin live-feed.
 *
 * Uses {@link fetchEventSource} (Microsoft's fetch-based SSE client) instead of the
 * native browser `EventSource` so we can authenticate with the standard
 * `Authorization: Bearer <jwt>` header. The native API cannot send custom headers,
 * which would force the JWT into the URL query string and leak it into the hosting
 * platform's edge access logs (see RD-004 and the security checklist's B0 entry
 * — both now resolved by this migration).
 */
export class SseWebhookStreamAdapter implements WebhookStreamPort {
  private readonly adminWebhookPort: AdminWebhookPort

  constructor(adminWebhookPort: AdminWebhookPort) {
    this.adminWebhookPort = adminWebhookPort
  }

  openWebhookStream(filters: WebhookStreamFilters, callbacks: WebhookStreamCallbacks): () => void {
    const streamUrl = this.adminWebhookPort.buildWebhookStreamRequest(filters)
    const controller = new AbortController()
    const token = getToken()?.token ?? null
    const headers: Record<string, string> = { Accept: 'text/event-stream' }
    if (token !== null) {
      headers.Authorization = `Bearer ${token}`
    }

    // fetchEventSource returns a Promise that resolves when the connection ends.
    // We don't await it — the lifecycle is managed by the AbortController returned
    // to the caller via the close() function.
    fetchEventSource(streamUrl, {
      headers,
      signal: controller.signal,
      // Keep the connection open when the tab is hidden; matches native EventSource
      // behaviour and avoids surprise reconnects during tab-switching.
      openWhenHidden: true,
      onmessage(event) {
        if (event.event === 'webhook.received' || event.event === 'heartbeat') {
          callbacks.onEvent(event.event, parseData(event.data))
        }
      },
      onerror(err) {
        callbacks.onError?.(err instanceof Event ? err : new Event('error'))
        // Re-throw so fetchEventSource stops retrying; the caller's teardown
        // (close()) is what controls reconnect, not the library's default.
        throw err
      },
    }).catch(() => {
      // Swallow the rejection that follows our onerror re-throw; the error has
      // already been delivered to the caller via callbacks.onError.
    })

    // Returned to the caller (a useEffect cleanup in useWebhookStream). Note that
    // in dev mode React StrictMode intentionally double-runs effects, which causes
    // the first connection to be aborted (visible as a canceled request in the
    // network tab) before the second one opens. That is expected and does not
    // happen in production builds.
    return () => controller.abort()
  }
}

function parseData(data: string): unknown {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}
