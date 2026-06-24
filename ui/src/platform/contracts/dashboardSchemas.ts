import { z } from 'zod'
import { workflowRunStatusSchema } from './workflowSchemas'

export const healthStateSchema = z.enum(['HEALTHY', 'DEGRADED', 'UNHEALTHY'])

export const deltaDirectionSchema = z.enum(['UP', 'DOWN', 'FLAT'])

export const deltaSchema = z.object({
  value: z.number().nullable(),
  direction: deltaDirectionSchema,
})
export type Delta = z.infer<typeof deltaSchema>

const countStatSchema = z.object({ value: z.number(), delta: deltaSchema })
const rateStatSchema = z.object({ value: z.number().nullable(), delta: deltaSchema })

const stageSchema = z.object({ value: z.number(), spark: z.array(z.number()) })

export const dashboardRunRowSchema = z.object({
  id: z.number(),
  workflowKey: z.string(),
  status: workflowRunStatusSchema,
  startedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  durationSec: z.number().nullable(),
})
export type DashboardRunRow = z.infer<typeof dashboardRunRowSchema>

export const dashboardFailureRowSchema = z.object({
  ref: z.string(),
  workflowKey: z.string(),
  status: workflowRunStatusSchema,
  reason: z.string().nullable(),
  ageSeconds: z.number(),
})
export type DashboardFailureRow = z.infer<typeof dashboardFailureRowSchema>

export const dashboardSnapshotSchema = z.object({
  window: z.object({
    from: z.string(),
    to: z.string(),
    label: z.string(),
  }),
  hero: z.object({
    state: healthStateSchema,
    openFailures: z.number(),
    stats: z.object({
      runs: countStatSchema,
      successRate: rateStatSchema,
      openFailures: countStatSchema,
    }),
  }),
  throughput: z.object({
    series: z.array(z.number()),
    peak: z.number(),
    avg: z.number(),
    perMin: z.number(),
  }),
  funnel: z.object({
    ingested: stageSchema,
    domainEvents: stageSchema,
    runs: stageSchema,
    failed: stageSchema,
  }),
  recentRuns: z.array(dashboardRunRowSchema),
  needsAttention: z.array(dashboardFailureRowSchema),
})
export type DashboardSnapshot = z.infer<typeof dashboardSnapshotSchema>
