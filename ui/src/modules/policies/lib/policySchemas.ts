import { z } from 'zod'

// --- Enums ---

export const policyStatusSchema = z.enum(['ACTIVE', 'INACTIVE'])
export type PolicyStatus = z.infer<typeof policyStatusSchema>

export const policyExecutionRunStatusSchema = z.enum([
  'PENDING',
  'BLOCKED_POLICY',
  'DUPLICATE_IGNORED',
  'COMPLETED',
  'FAILED',
])
export type PolicyExecutionRunStatus = z.infer<typeof policyExecutionRunStatusSchema>

export const policyExecutionStepStatusSchema = z.enum([
  'PENDING',
  'WAITING_DEPENDENCY',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'SKIPPED',
])
export type PolicyExecutionStepStatus = z.infer<typeof policyExecutionStepStatusSchema>

export const policyStepTypeSchema = z.enum([
  'WAIT_AND_CHECK_CLAIM',
  'WAIT_AND_CHECK_COMMUNICATION',
  'ON_FAILURE_EXECUTE_ACTION',
])
export type PolicyStepType = z.infer<typeof policyStepTypeSchema>

export const webhookSourceSchema = z.enum(['FUB', 'INTERNAL'])
export type WebhookSource = z.infer<typeof webhookSourceSchema>

// --- Policy ---

export const policyResponseSchema = z.object({
  id: z.number(),
  domain: z.string(),
  policyKey: z.string(),
  enabled: z.boolean(),
  blueprint: z.record(z.string(), z.unknown()),
  status: policyStatusSchema,
  version: z.number(),
})
export type PolicyResponse = z.infer<typeof policyResponseSchema>

export const policyListSchema = z.array(policyResponseSchema)

// --- Policy Execution Steps ---

export const policyExecutionStepSchema = z.object({
  id: z.number(),
  stepOrder: z.number(),
  stepType: policyStepTypeSchema,
  status: policyExecutionStepStatusSchema,
  dueAt: z.string().nullable(),
  dependsOnStepOrder: z.number().nullable(),
  resultCode: z.string().nullable(),
  errorMessage: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type PolicyExecutionStep = z.infer<typeof policyExecutionStepSchema>

// --- Policy Execution Runs ---

export const policyExecutionRunListItemSchema = z.object({
  id: z.number(),
  source: webhookSourceSchema,
  eventId: z.string().nullable(),
  sourceLeadId: z.string().nullable(),
  domain: z.string(),
  policyKey: z.string(),
  policyVersion: z.number(),
  status: policyExecutionRunStatusSchema,
  reasonCode: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type PolicyExecutionRunListItem = z.infer<typeof policyExecutionRunListItemSchema>

export const policyExecutionRunPageSchema = z.object({
  items: z.array(policyExecutionRunListItemSchema),
  nextCursor: z.string().nullable().optional().transform((v) => v ?? null),
  serverTime: z.string(),
})
export type PolicyExecutionRunPage = z.infer<typeof policyExecutionRunPageSchema>

export const policyExecutionRunDetailSchema = z.object({
  id: z.number(),
  source: webhookSourceSchema,
  eventId: z.string().nullable(),
  webhookEventId: z.number().nullable(),
  sourceLeadId: z.string().nullable(),
  domain: z.string(),
  policyKey: z.string(),
  policyVersion: z.number(),
  policyBlueprintSnapshot: z.record(z.string(), z.unknown()),
  status: policyExecutionRunStatusSchema,
  reasonCode: z.string().nullable(),
  idempotencyKey: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  steps: z.array(policyExecutionStepSchema),
})
export type PolicyExecutionRunDetail = z.infer<typeof policyExecutionRunDetailSchema>
