import type { AdminWebhookPort } from '../../ports/adminWebhookPort'
import type { WebhookEventDetail, WebhookFeedPage, WebhookListFilters, WebhookStreamFilters } from '../../../shared/types/webhook'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'
import { webhookDetailSchema, webhookFeedPageSchema } from './webhookSchemas'

export class HttpAdminWebhookAdapter implements AdminWebhookPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listWebhooks(filters: WebhookListFilters): Promise<WebhookFeedPage> {
    const query = toQueryString({
      source: filters.source,
      status: filters.status,
      eventType: filters.eventType,
      from: filters.from,
      to: filters.to,
      limit: filters.limit,
      cursor: filters.cursor,
      includePayload: filters.includePayload,
    })

    return this.httpClient.get(`/admin/webhooks${query}`, webhookFeedPageSchema)
  }

  getWebhookDetail(id: number): Promise<WebhookEventDetail> {
    return this.httpClient.get(`/admin/webhooks/${id}`, webhookDetailSchema)
  }

  buildWebhookStreamRequest(filters: WebhookStreamFilters): string {
    const query = toQueryString({
      source: filters.source,
      status: filters.status,
      eventType: filters.eventType,
    })

    return `/admin/webhooks/stream${query}`
  }
}
