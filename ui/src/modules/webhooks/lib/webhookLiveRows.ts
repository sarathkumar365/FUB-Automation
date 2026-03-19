import type { WebhookFeedItem, WebhookStreamEvent } from '../../../shared/types/webhook'
import type { WebhookPageSearchState } from './webhookSearchParams'
import { uiText } from '../../../shared/constants/uiText'

export function mergeWebhookRows(incoming: WebhookFeedItem[], existing: WebhookFeedItem[]): WebhookFeedItem[] {
  const byId = new Map<number, WebhookFeedItem>()

  for (const row of existing) {
    byId.set(row.id, row)
  }

  for (const row of incoming) {
    byId.set(row.id, row)
  }

  const consumed = new Set<number>()
  const ordered: WebhookFeedItem[] = []
  const inOrder = [...incoming, ...existing]

  for (const row of inOrder) {
    if (consumed.has(row.id)) {
      continue
    }
    const merged = byId.get(row.id)
    if (!merged) {
      continue
    }
    ordered.push(merged)
    consumed.add(row.id)
  }

  return ordered
}

export function toWebhookFeedItems(events: WebhookStreamEvent[]): WebhookFeedItem[] {
  const rows: WebhookFeedItem[] = []

  for (const event of events) {
    const parsedId = Number(event.id)
    if (!Number.isFinite(parsedId)) {
      continue
    }

    rows.push({
      id: parsedId,
      eventId: event.eventId?.trim() || uiText.webhooks.missingEventId,
      source: event.source,
      eventType: event.eventType,
      status: event.status,
      receivedAt: event.receivedAt,
      payload: event.payload,
    })
  }

  return rows
}

export function filterWebhookRows(rows: WebhookFeedItem[], filters: WebhookPageSearchState): WebhookFeedItem[] {
  return rows.filter((row) => {
    if (filters.source && row.source !== filters.source) {
      return false
    }
    if (filters.status && row.status !== filters.status) {
      return false
    }
    if (filters.eventType && row.eventType !== filters.eventType) {
      return false
    }
    if (filters.from && !matchesFrom(row.receivedAt, filters.from)) {
      return false
    }
    if (filters.to && !matchesTo(row.receivedAt, filters.to)) {
      return false
    }

    return true
  })
}

function matchesFrom(value: string, from: string): boolean {
  const eventDate = new Date(value)
  const fromDate = new Date(from)
  if (Number.isNaN(eventDate.getTime()) || Number.isNaN(fromDate.getTime())) {
    return true
  }

  // NOTE: `from` comes from a date-only input (YYYY-MM-DD); this local-time normalization
  // can drift from UTC/server boundaries in non-UTC timezones and should be revisited.
  fromDate.setHours(0, 0, 0, 0)
  return eventDate.getTime() >= fromDate.getTime()
}

function matchesTo(value: string, to: string): boolean {
  const eventDate = new Date(value)
  const toDate = new Date(to)
  if (Number.isNaN(eventDate.getTime()) || Number.isNaN(toDate.getTime())) {
    return true
  }

  // NOTE: Same caveat as `matchesFrom`; end-of-day local normalization may diverge from
  // UTC/server date filtering semantics.
  toDate.setHours(23, 59, 59, 999)
  return eventDate.getTime() <= toDate.getTime()
}
