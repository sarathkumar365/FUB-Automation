import { z } from 'zod'

export const workflowStatusSchema = z.enum(['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'])
export type WorkflowStatus = z.infer<typeof workflowStatusSchema>

export const workflowRunStatusSchema = z.enum([
  'PENDING',
  'BLOCKED',
  'DUPLICATE_IGNORED',
  'CANCELED',
  'COMPLETED',
  'FAILED',
])
export type WorkflowRunStatus = z.infer<typeof workflowRunStatusSchema>

export const workflowRunStepStatusSchema = z.enum([
  'PENDING',
  'WAITING_DEPENDENCY',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'SKIPPED',
])
export type WorkflowRunStepStatus = z.infer<typeof workflowRunStepStatusSchema>

export const workflowResponseSchema = z.object({
  id: z.number(),
  key: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  trigger: z.record(z.string(), z.unknown()).nullable(),
  graph: z.record(z.string(), z.unknown()),
  status: workflowStatusSchema.nullable(),
  versionNumber: z.number().nullable(),
  version: z.number().nullable(),
})
export type WorkflowResponse = z.infer<typeof workflowResponseSchema>

export function pageResponseSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    page: z.number(),
    size: z.number(),
    total: z.number(),
  })
}

export const workflowPageResponseSchema = pageResponseSchema(workflowResponseSchema)
export type WorkflowPageResponse = z.infer<typeof workflowPageResponseSchema>

export const validateWorkflowResponseSchema = z.object({
  valid: z.boolean(),
  errors: z.array(z.string()),
})
export type ValidateWorkflowResponse = z.infer<typeof validateWorkflowResponseSchema>

export const stepTypeCatalogEntrySchema = z.object({
  id: z.string(),
  displayName: z.string(),
  description: z.string(),
  configSchema: z.record(z.string(), z.unknown()),
  declaredResultCodes: z.array(z.string()),
  defaultRetryPolicy: z.record(z.string(), z.unknown()),
})
export type StepTypeCatalogEntry = z.infer<typeof stepTypeCatalogEntrySchema>

export const triggerTypeCatalogEntrySchema = z.object({
  id: z.string(),
  displayName: z.string(),
  description: z.string(),
  configSchema: z.record(z.string(), z.unknown()),
})
export type TriggerTypeCatalogEntry = z.infer<typeof triggerTypeCatalogEntrySchema>

export const workflowVersionSummarySchema = z.object({
  versionNumber: z.number().nullable(),
  status: workflowStatusSchema.nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type WorkflowVersionSummary = z.infer<typeof workflowVersionSummarySchema>

export const workflowRunSummarySchema = z.object({
  id: z.number(),
  workflowKey: z.string(),
  workflowVersionNumber: z.number(),
  status: workflowRunStatusSchema,
  reasonCode: z.string().nullable(),
  startedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
})
export type WorkflowRunSummary = z.infer<typeof workflowRunSummarySchema>

export const workflowRunStepDetailSchema = z.object({
  id: z.number(),
  nodeId: z.string(),
  stepType: z.string(),
  status: workflowRunStepStatusSchema,
  resultCode: z.string().nullable(),
  outputs: z.record(z.string(), z.unknown()).nullable(),
  errorMessage: z.string().nullable(),
  retryCount: z.number().nullable(),
  dueAt: z.string().nullable(),
  startedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
})
export type WorkflowRunStepDetail = z.infer<typeof workflowRunStepDetailSchema>

export const workflowRunDetailResponseSchema = z.object({
  id: z.number(),
  workflowKey: z.string(),
  workflowVersionNumber: z.number(),
  status: workflowRunStatusSchema,
  reasonCode: z.string().nullable(),
  startedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  triggerPayload: z.record(z.string(), z.unknown()).nullable(),
  sourceLeadId: z.string().nullable(),
  eventId: z.string().nullable(),
  steps: z.array(workflowRunStepDetailSchema),
})
export type WorkflowRunDetailResponse = z.infer<typeof workflowRunDetailResponseSchema>

export const workflowRunPageResponseSchema = pageResponseSchema(workflowRunSummarySchema)
export type WorkflowRunPageResponse = z.infer<typeof workflowRunPageResponseSchema>
