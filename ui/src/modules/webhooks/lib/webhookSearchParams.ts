import type { WebhookEventStatus, WebhookSource } from '../../../shared/types/webhook'

const SOURCE_VALUES: WebhookSource[] = ['FUB']
const STATUS_VALUES: WebhookEventStatus[] = ['RECEIVED']

export type WebhookPageSearchState = {
  source?: WebhookSource
  status?: WebhookEventStatus
  eventType?: string
  from?: string
  to?: string
  cursor?: string
  selectedId?: number
}

export type WebhookFilterDraft = {
  source: WebhookSource | 'ALL'
  status: WebhookEventStatus | 'ALL'
  eventType: string
  from: string
  to: string
}

export function parseWebhookSearchParams(searchParams: URLSearchParams): WebhookPageSearchState {
  const sourceValue = searchParams.get('source') ?? undefined
  const statusValue = searchParams.get('status') ?? undefined
  const selectedIdRaw = searchParams.get('selectedId')
  const selectedId = selectedIdRaw ? Number(selectedIdRaw) : undefined

  return {
    source: isWebhookSource(sourceValue) ? sourceValue : undefined,
    status: isWebhookStatus(statusValue) ? statusValue : undefined,
    eventType: normalizeString(searchParams.get('eventType')),
    from: normalizeString(searchParams.get('from')),
    to: normalizeString(searchParams.get('to')),
    cursor: normalizeString(searchParams.get('cursor')),
    selectedId: Number.isFinite(selectedId) ? selectedId : undefined,
  }
}

export function toDraftFilters(state: WebhookPageSearchState): WebhookFilterDraft {
  return {
    source: state.source ?? 'ALL',
    status: state.status ?? 'ALL',
    eventType: state.eventType ?? '',
    from: state.from ?? '',
    to: state.to ?? '',
  }
}

export function createSearchParamsFromState(state: WebhookPageSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.source) {
    params.set('source', state.source)
  }
  if (state.status) {
    params.set('status', state.status)
  }
  if (state.eventType) {
    params.set('eventType', state.eventType)
  }
  if (state.from) {
    params.set('from', state.from)
  }
  if (state.to) {
    params.set('to', state.to)
  }
  if (state.cursor) {
    params.set('cursor', state.cursor)
  }
  if (state.selectedId !== undefined) {
    params.set('selectedId', String(state.selectedId))
  }

  return params
}

function normalizeString(value: string | null): string | undefined {
  if (!value) {
    return undefined
  }

  const normalized = value.trim()
  return normalized.length > 0 ? normalized : undefined
}

function isWebhookSource(value: string | undefined): value is WebhookSource {
  return value ? SOURCE_VALUES.includes(value as WebhookSource) : false
}

function isWebhookStatus(value: string | undefined): value is WebhookEventStatus {
  return value ? STATUS_VALUES.includes(value as WebhookEventStatus) : false
}
