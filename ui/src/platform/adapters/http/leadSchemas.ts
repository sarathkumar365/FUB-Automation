import { z } from 'zod'

export const leadStatusSchema = z.enum(['ACTIVE', 'ARCHIVED', 'MERGED'])

export const leadFeedItemSchema = z.object({
  id: z.number(),
  sourceSystem: z.string(),
  sourceLeadId: z.string(),
  status: leadStatusSchema,
  snapshot: z.unknown().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string(),
  updatedAt: z.string(),
  lastSyncedAt: z.string(),
})

export const leadFeedPageSchema = z.object({
  items: z.array(leadFeedItemSchema),
  nextCursor: z.string().nullable().optional().transform((value) => value ?? null),
  serverTime: z.string(),
})

export const leadActivityKindSchema = z.enum(['PROCESSED_CALL', 'WORKFLOW_RUN', 'WEBHOOK_EVENT'])

export const leadActivityEventSchema = z.object({
  kind: leadActivityKindSchema,
  refId: z.number(),
  occurredAt: z.string(),
  summary: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
})

export const leadRecentCallSchema = z.object({
  id: z.number(),
  callId: z.number(),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  outcome: z.string().nullable().optional().transform((value) => value ?? null),
  isIncoming: z.boolean().nullable().optional().transform((value) => value ?? null),
  durationSeconds: z.number().nullable().optional().transform((value) => value ?? null),
  callStartedAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const leadRecentWorkflowRunSchema = z.object({
  id: z.number(),
  workflowKey: z.string().nullable().optional().transform((value) => value ?? null),
  workflowVersion: z.number().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  reasonCode: z.string().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const leadRecentWebhookEventSchema = z.object({
  id: z.number(),
  source: z.string().nullable().optional().transform((value) => value ?? null),
  eventType: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  receivedAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const leadLiveStatusSchema = z.enum(['LIVE_OK', 'LIVE_FAILED', 'LIVE_SKIPPED'])

export const leadSummarySchema = z.object({
  lead: leadFeedItemSchema,
  livePerson: z.unknown().nullable().optional().transform((value) => value ?? null),
  liveStatus: leadLiveStatusSchema,
  liveMessage: z.string().nullable().optional().transform((value) => value ?? null),
  activity: z.array(leadActivityEventSchema),
  recentCalls: z.array(leadRecentCallSchema),
  recentWorkflowRuns: z.array(leadRecentWorkflowRunSchema),
  recentWebhookEvents: z.array(leadRecentWebhookEventSchema),
})
