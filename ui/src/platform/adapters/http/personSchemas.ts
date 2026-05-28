import { z } from 'zod'

export const personStatusSchema = z.enum(['ACTIVE', 'ARCHIVED', 'MERGED'])

export const personFeedItemSchema = z.object({
  id: z.number(),
  sourceSystem: z.string(),
  sourcePersonId: z.string(),
  status: personStatusSchema,
  snapshot: z.unknown().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string(),
  updatedAt: z.string(),
  lastSyncedAt: z.string(),
})

export const personFeedPageSchema = z.object({
  items: z.array(personFeedItemSchema),
  nextCursor: z.string().nullable().optional().transform((value) => value ?? null),
  serverTime: z.string(),
})

export const personActivityKindSchema = z.enum(['PROCESSED_CALL', 'WORKFLOW_RUN', 'WEBHOOK_EVENT'])

export const personActivityEventSchema = z.object({
  kind: personActivityKindSchema,
  refId: z.number(),
  occurredAt: z.string(),
  summary: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
})

export const personRecentCallSchema = z.object({
  id: z.number(),
  callId: z.number(),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  outcome: z.string().nullable().optional().transform((value) => value ?? null),
  isIncoming: z.boolean().nullable().optional().transform((value) => value ?? null),
  durationSeconds: z.number().nullable().optional().transform((value) => value ?? null),
  callStartedAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const personRecentWorkflowRunSchema = z.object({
  id: z.number(),
  workflowKey: z.string().nullable().optional().transform((value) => value ?? null),
  workflowVersion: z.number().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  reasonCode: z.string().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const personRecentWebhookEventSchema = z.object({
  id: z.number(),
  source: z.string().nullable().optional().transform((value) => value ?? null),
  eventType: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  receivedAt: z.string().nullable().optional().transform((value) => value ?? null),
})

export const personLiveStatusSchema = z.enum(['LIVE_OK', 'LIVE_FAILED', 'LIVE_SKIPPED'])

export const personSummarySchema = z.object({
  person: personFeedItemSchema,
  livePerson: z.unknown().nullable().optional().transform((value) => value ?? null),
  liveStatus: personLiveStatusSchema,
  liveMessage: z.string().nullable().optional().transform((value) => value ?? null),
  activity: z.array(personActivityEventSchema),
  recentCalls: z.array(personRecentCallSchema),
  recentWorkflowRuns: z.array(personRecentWorkflowRunSchema),
  recentWebhookEvents: z.array(personRecentWebhookEventSchema),
})
