import { z } from 'zod'

export const webhookFeedItemSchema = z.object({
  id: z.number(),
  eventId: z.string(),
  source: z.literal('FUB'),
  eventType: z.string(),
  status: z.literal('RECEIVED'),
  receivedAt: z.string(),
  payload: z.unknown().optional(),
})

export const webhookFeedPageSchema = z.object({
  items: z.array(webhookFeedItemSchema),
  nextCursor: z.string().nullable().optional().transform((value) => value ?? null),
  serverTime: z.string(),
})

export const webhookDetailSchema = z.object({
  id: z.number(),
  eventId: z.string(),
  source: z.literal('FUB'),
  eventType: z.string(),
  status: z.literal('RECEIVED'),
  payloadHash: z.string().nullable().optional(),
  payload: z.unknown(),
  receivedAt: z.string(),
})
