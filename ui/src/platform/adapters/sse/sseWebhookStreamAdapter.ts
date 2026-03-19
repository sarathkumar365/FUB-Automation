import type { WebhookStreamCallbacks, WebhookStreamPort } from '../../ports/webhookStreamPort'
import type { WebhookStreamFilters } from '../../../shared/types/webhook'
import type { AdminWebhookPort } from '../../ports/adminWebhookPort'

export class SseWebhookStreamAdapter implements WebhookStreamPort {
  private readonly adminWebhookPort: AdminWebhookPort

  constructor(adminWebhookPort: AdminWebhookPort) {
    this.adminWebhookPort = adminWebhookPort
  }

  openWebhookStream(filters: WebhookStreamFilters, callbacks: WebhookStreamCallbacks): () => void {
    const streamUrl = this.adminWebhookPort.buildWebhookStreamRequest(filters)
    const eventSource = new EventSource(streamUrl)

    eventSource.addEventListener('webhook.received', (event) => {
      const messageEvent = event as MessageEvent<string>
      callbacks.onEvent('webhook.received', parseData(messageEvent.data))
    })

    eventSource.addEventListener('heartbeat', (event) => {
      const messageEvent = event as MessageEvent<string>
      callbacks.onEvent('heartbeat', parseData(messageEvent.data))
    })

    if (callbacks.onError) {
      eventSource.onerror = callbacks.onError
    }

    return () => {
      eventSource.close()
    }
  }
}

function parseData(data: string): unknown {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}
