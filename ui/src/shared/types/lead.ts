export type LeadStatus = 'ACTIVE' | 'ARCHIVED' | 'MERGED'

export type LeadLiveStatus = 'LIVE_OK' | 'LIVE_FAILED' | 'LIVE_SKIPPED'

export type LeadActivityKind = 'PROCESSED_CALL' | 'WORKFLOW_RUN' | 'WEBHOOK_EVENT'

export type LeadFeedItem = {
  id: number
  sourceSystem: string
  sourceLeadId: string
  status: LeadStatus
  snapshot: unknown
  createdAt: string
  updatedAt: string
  lastSyncedAt: string
}

export type LeadFeedPage = {
  items: LeadFeedItem[]
  nextCursor: string | null
  serverTime: string
}

export type LeadActivityEvent = {
  kind: LeadActivityKind
  refId: number
  occurredAt: string
  summary: string | null
  status: string | null
}

export type LeadRecentCall = {
  id: number
  callId: number
  status: string | null
  outcome: string | null
  isIncoming: boolean | null
  durationSeconds: number | null
  callStartedAt: string | null
}

export type LeadRecentWorkflowRun = {
  id: number
  workflowKey: string | null
  workflowVersion: number | null
  status: string | null
  reasonCode: string | null
  createdAt: string | null
}

export type LeadRecentWebhookEvent = {
  id: number
  source: string | null
  eventType: string | null
  status: string | null
  receivedAt: string | null
}

export type LeadSummary = {
  lead: LeadFeedItem
  livePerson: unknown | null
  liveStatus: LeadLiveStatus
  liveMessage: string | null
  activity: LeadActivityEvent[]
  recentCalls: LeadRecentCall[]
  recentWorkflowRuns: LeadRecentWorkflowRun[]
  recentWebhookEvents: LeadRecentWebhookEvent[]
}

export type LeadListFilters = {
  sourceSystem?: string
  status?: LeadStatus
  sourceLeadIdPrefix?: string
  from?: string
  to?: string
  limit?: number
  cursor?: string
}

export type LeadSummaryFilters = {
  sourceSystem?: string
  includeLive?: boolean
}
