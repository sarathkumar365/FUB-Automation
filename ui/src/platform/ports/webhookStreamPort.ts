import type { WebhookStreamFilters } from '../../shared/types/webhook'

export type WebhookStreamEventName = 'webhook.received' | 'heartbeat'

export type WebhookStreamCallbacks = {
  onEvent: (eventName: WebhookStreamEventName, data: unknown) => void
  onError?: (error: Event) => void
}

export interface WebhookStreamPort {
  openWebhookStream(filters: WebhookStreamFilters, callbacks: WebhookStreamCallbacks): () => void
}
