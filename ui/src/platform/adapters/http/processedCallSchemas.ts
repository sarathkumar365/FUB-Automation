import { z } from 'zod'

export const processedCallStatusSchema = z.enum(['RECEIVED', 'PROCESSING', 'SKIPPED', 'TASK_CREATED', 'FAILED'])

export const processedCallSummarySchema = z.object({
  callId: z.number(),
  status: processedCallStatusSchema,
  ruleApplied: z.string().nullable().optional(),
  taskId: z.number().nullable().optional(),
  failureReason: z.string().nullable().optional(),
  retryCount: z.number(),
  updatedAt: z.string(),
})

export const processedCallSummaryListSchema = z.array(processedCallSummarySchema)

export const replayProcessedCallResponseSchema = z.object({
  message: z.string(),
})
