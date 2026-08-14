export type PersonStatus = 'ACTIVE' | 'ARCHIVED' | 'MERGED'

export type PersonLiveStatus = 'LIVE_OK' | 'LIVE_FAILED' | 'LIVE_SKIPPED'

export type PersonActivityKind = 'PROCESSED_CALL' | 'WORKFLOW_RUN' | 'WEBHOOK_EVENT'

export type PersonFeedItem = {
  id: number
  sourceSystem: string
  sourcePersonId: string
  status: PersonStatus
  snapshot: unknown
  createdAt: string
  updatedAt: string
  lastSyncedAt: string
}

export type PersonFeedPage = {
  items: PersonFeedItem[]
  nextCursor: string | null
  serverTime: string
}

export type PersonActivityEvent = {
  kind: PersonActivityKind
  refId: number
  occurredAt: string
  summary: string | null
  status: string | null
}

export type PersonRecentCall = {
  id: number
  callId: number
  status: string | null
  outcome: string | null
  isIncoming: boolean | null
  durationSeconds: number | null
  callStartedAt: string | null
}

export type PersonRecentWorkflowRun = {
  id: number
  workflowKey: string | null
  workflowVersion: number | null
  status: string | null
  reasonCode: string | null
  createdAt: string | null
}

export type PersonRecentWebhookEvent = {
  id: number
  source: string | null
  eventType: string | null
  status: string | null
  receivedAt: string | null
}

export type PersonSummary = {
  person: PersonFeedItem
  livePerson: unknown | null
  liveStatus: PersonLiveStatus
  liveMessage: string | null
  activity: PersonActivityEvent[]
  recentCalls: PersonRecentCall[]
  recentWorkflowRuns: PersonRecentWorkflowRun[]
  recentWebhookEvents: PersonRecentWebhookEvent[]
}

export type PersonListFilters = {
  sourceSystem?: string
  status?: PersonStatus
  sourcePersonIdPrefix?: string
  from?: string
  to?: string
  limit?: number
  cursor?: string
}

export type PersonSummaryFilters = {
  sourceSystem?: string
  includeLive?: boolean
}
