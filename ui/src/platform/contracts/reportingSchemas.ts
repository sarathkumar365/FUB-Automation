import { z } from 'zod'

/**
 * Reporting Phase 2 read models (`GET /admin/reporting/*`). Mirrors the
 * backend DTOs 1:1 — see `controller/dto/{SourceContactReportDto,
 * AccountabilityReportDto,ReportWindowDto,ContactCountsDto}.java`.
 *
 * Contact states: SPOKE = a conversation happened (outbound call over the
 * threshold by the holder at that moment); ATTEMPTED = dialled but not
 * reached; NOTHING = untouched. Attempted is NOT contact — never present it
 * as "reached".
 */

/** Known keys today; the backend may grow more (month/year) without a UI break. */
export const reportWindowKeys = ['yesterday', 'today', 'this-week'] as const
export type ReportWindowKey = (typeof reportWindowKeys)[number]

export const reportWindowSchema = z.object({
  key: z.string(),
  from: z.string(),
  to: z.string(),
  timezone: z.string(),
  /** Still accumulating (today / this week) vs a finished day. */
  open: z.boolean(),
  /** Webhook events received in the window — 0 with 0 leads means the feed was down, not a quiet day. */
  eventsReceived: z.number(),
})

export const contactCountsSchema = z.object({
  leads: z.number(),
  spoke: z.number(),
  attempted: z.number(),
  nothing: z.number(),
})
export type ContactCounts = z.infer<typeof contactCountsSchema>

const agentCountsSchema = z.object({
  agentId: z.number(),
  agentName: z.string().nullable(),
  counts: contactCountsSchema,
})
export type AgentCounts = z.infer<typeof agentCountsSchema>

export const sourceContactReportSchema = z.object({
  window: reportWindowSchema,
  totals: contactCountsSchema,
  sources: z.array(
    z.object({
      source: z.string(),
      counts: contactCountsSchema,
      agents: z.array(agentCountsSchema),
    }),
  ),
})
export type SourceContactReport = z.infer<typeof sourceContactReportSchema>
export type SourceContactSourceRow = SourceContactReport['sources'][number]

export const accountabilityReportSchema = z.object({
  window: reportWindowSchema,
  totals: contactCountsSchema,
  agents: z.array(agentCountsSchema),
})
export type AccountabilityReport = z.infer<typeof accountabilityReportSchema>

export const unreachedLeadsSchema = z.object({
  window: reportWindowSchema,
  agentId: z.number(),
  agentName: z.string().nullable(),
  limit: z.number(),
  /** More leads exist than `limit` — say so instead of passing a cut-off list as complete. */
  truncated: z.boolean(),
  leads: z.array(
    z.object({
      sourcePersonId: z.string(),
      name: z.string().nullable(),
      phone: z.string().nullable(),
      email: z.string().nullable(),
      source: z.string().nullable(),
      arrivedAt: z.string(),
    }),
  ),
})
export type UnreachedLeads = z.infer<typeof unreachedLeadsSchema>
