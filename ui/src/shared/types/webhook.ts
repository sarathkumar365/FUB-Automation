export type WebhookSource = 'FUB'

export type WebhookEventStatus = 'RECEIVED'

export type WebhookFeedItem = {
  id: number
  eventId: string
  source: WebhookSource
  eventType: string
  status: WebhookEventStatus
  receivedAt: string
  payload?: unknown
}

export type WebhookFeedPage = {
  items: WebhookFeedItem[]
  nextCursor: string | null
  serverTime: string
}

export type WebhookEventDetail = {
  id: number
  eventId: string
  source: WebhookSource
  eventType: string
  status: WebhookEventStatus
  payloadHash?: string | null
  payload: unknown
  receivedAt: string
}

export type WebhookListFilters = {
  source?: WebhookSource
  status?: WebhookEventStatus
  eventType?: string
  from?: string
  to?: string
  limit?: number
  cursor?: string
  includePayload?: boolean
}

export type WebhookStreamFilters = Pick<WebhookListFilters, 'source' | 'status' | 'eventType'>

export type WebhookStreamEvent = {
  id: string
  eventId?: string | null
  receivedAt: string
  source: WebhookSource
  eventType: string
  status: WebhookEventStatus
  payload: unknown
}
