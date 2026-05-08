import type { WebhookEventDetail, WebhookFeedPage, WebhookListFilters, WebhookStreamFilters } from '../../shared/types/webhook'

export interface AdminWebhookPort {
  listWebhooks(filters: WebhookListFilters): Promise<WebhookFeedPage>
  getWebhookDetail(id: number): Promise<WebhookEventDetail>
  listEventTypes(): Promise<string[]>
  buildWebhookStreamRequest(filters: WebhookStreamFilters): string
}
