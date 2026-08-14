import type {
  AgentCounts,
  ContactCounts,
  SourceContactReport,
  SourceContactSourceRow,
} from '@platform/contracts/reportingSchemas'

export const outcomeKeys = ['spoke', 'attempted', 'nothing'] as const
export type OutcomeKey = (typeof outcomeKeys)[number]

export function pct(part: number, whole: number): string {
  return whole > 0 ? `${Math.round((part / whole) * 100)}%` : '—'
}

/** Segment widths for an outcome mix bar, as CSS percentage strings. */
export function mixSegments(counts: ContactCounts): Record<OutcomeKey, string> {
  const total = counts.leads || 1
  return {
    spoke: `${((counts.spoke / total) * 100).toFixed(1)}%`,
    attempted: `${((counts.attempted / total) * 100).toFixed(1)}%`,
    nothing: `${((counts.nothing / total) * 100).toFixed(1)}%`,
  }
}

export function addCounts(a: ContactCounts, b: ContactCounts): ContactCounts {
  return {
    leads: a.leads + b.leads,
    spoke: a.spoke + b.spoke,
    attempted: a.attempted + b.attempted,
    nothing: a.nothing + b.nothing,
  }
}

export const zeroCounts: ContactCounts = { leads: 0, spoke: 0, attempted: 0, nothing: 0 }

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('')
}

export type FlowDrill = {
  source: string | null
  agentId: number | null
}

/**
 * The report filtered to the current drill selection — the sankey, breadcrumb,
 * and drill narration all read this one view so they can never disagree.
 */
export type FlowView = {
  sources: SourceContactSourceRow[]
  /** Agents merged across the visible sources (an agent can hold leads from several). */
  agents: AgentCounts[]
  totals: ContactCounts
}

export function flowView(report: SourceContactReport, drill: FlowDrill): FlowView {
  const sources = report.sources
    .filter((row) => drill.source === null || row.source === drill.source)
    .map((row) => {
      const agents = row.agents.filter(
        (agent) => drill.agentId === null || agent.agentId === drill.agentId,
      )
      return {
        source: row.source,
        agents,
        counts: agents.reduce((sum, agent) => addCounts(sum, agent.counts), zeroCounts),
      }
    })
    .filter((row) => row.counts.leads > 0)

  const byAgent = new Map<number, AgentCounts>()
  for (const row of sources) {
    for (const agent of row.agents) {
      const existing = byAgent.get(agent.agentId)
      byAgent.set(
        agent.agentId,
        existing
          ? { ...existing, counts: addCounts(existing.counts, agent.counts) }
          : { ...agent },
      )
    }
  }
  const agents = [...byAgent.values()].sort((a, b) => b.counts.leads - a.counts.leads)
  const totals = sources.reduce((sum, row) => addCounts(sum, row.counts), zeroCounts)

  return { sources, agents, totals }
}

/**
 * Drop a drill selection that no longer exists in the data (window switched,
 * refetch changed the cohort) instead of rendering an empty diagram.
 */
export function pruneDrill(report: SourceContactReport, drill: FlowDrill): FlowDrill {
  const source =
    drill.source !== null && report.sources.some((row) => row.source === drill.source)
      ? drill.source
      : null
  const agentId =
    drill.agentId !== null &&
    report.sources.some(
      (row) =>
        (source === null || row.source === source) &&
        row.agents.some((agent) => agent.agentId === drill.agentId),
    )
      ? drill.agentId
      : null
  return { source, agentId }
}

export function agentDisplayName(agent: { agentName: string | null; agentId: number }): string {
  return agent.agentName ?? `#${agent.agentId}`
}

export function formatAge(
  arrivedAtIso: string,
  now: Date,
  text: {
    agedJustNow: string
    ageMinutes: (n: number) => string
    ageHours: (n: number) => string
    ageDays: (n: number) => string
  },
): string {
  const minutes = Math.floor((now.getTime() - new Date(arrivedAtIso).getTime()) / 60_000)
  if (minutes < 1) return text.agedJustNow
  if (minutes < 60) return text.ageMinutes(minutes)
  if (minutes < 24 * 60) return text.ageHours(Math.floor(minutes / 60))
  return text.ageDays(Math.floor(minutes / (24 * 60)))
}
