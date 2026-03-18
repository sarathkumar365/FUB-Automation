import type { WebhookEventDetail, WebhookFeedPage, WebhookListFilters, WebhookStreamFilters } from '../../shared/types/webhook'

export interface AdminWebhookPort {
  listWebhooks(filters: WebhookListFilters): Promise<WebhookFeedPage>
  getWebhookDetail(id: number): Promise<WebhookEventDetail>
  buildWebhookStreamRequest(filters: WebhookStreamFilters): string
}
