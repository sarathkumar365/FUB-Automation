import { describe, expect, it } from 'vitest'
import {
  agentDisplayName,
  flowView,
  formatAge,
  initials,
  mixSegments,
  pct,
  pruneDrill,
} from '@modules/reports/lib/reportMath'
import { uiText } from '@shared/constants/uiText'
import { sampleReport } from './reportingFixtures'

const counts = (leads: number, spoke: number, attempted: number, nothing: number) => ({
  leads,
  spoke,
  attempted,
  nothing,
})

describe('report math', () => {
  it('computes percentages and dashes out an empty denominator', () => {
    expect(pct(6, 25)).toBe('24%')
    expect(pct(0, 0)).toBe('—')
  })

  it('mix segments always cover the whole bar', () => {
    const segments = mixSegments(counts(25, 6, 9, 10))
    const total =
      parseFloat(segments.spoke) + parseFloat(segments.attempted) + parseFloat(segments.nothing)
    expect(total).toBeCloseTo(100, 0)
  })

  it('merges an agent holding leads from several sources into one flow node', () => {
    const view = flowView(sampleReport(), { source: null, agentId: null })

    const mandeep = view.agents.find((agent) => agent.agentId === 1)
    expect(mandeep?.counts.leads).toBe(18) // 11 Facebook + 7 Instagram
    expect(view.totals.leads).toBe(25)
  })

  it('drilling into a source narrows agents and totals to that source', () => {
    const view = flowView(sampleReport(), { source: 'Instagram', agentId: null })

    expect(view.sources).toHaveLength(1)
    expect(view.agents).toHaveLength(1)
    expect(view.totals.leads).toBe(7)
  })

  it('drilling into an agent keeps only sources that agent holds leads from', () => {
    const view = flowView(sampleReport(), { source: null, agentId: 31 })

    expect(view.sources.map((row) => row.source)).toEqual(['Facebook'])
    expect(view.totals.leads).toBe(7)
  })

  it('prunes a drill selection that no longer exists in the data', () => {
    const stale = pruneDrill(sampleReport(), { source: 'Zillow', agentId: 99 })
    expect(stale).toEqual({ source: null, agentId: null })

    const alive = pruneDrill(sampleReport(), { source: 'Facebook', agentId: 31 })
    expect(alive).toEqual({ source: 'Facebook', agentId: 31 })
  })

  it('an agent valid globally but absent from the drilled source is pruned', () => {
    // Arjun holds no Instagram leads, so Instagram+Arjun is an empty diagram.
    const pruned = pruneDrill(sampleReport(), { source: 'Instagram', agentId: 31 })
    expect(pruned).toEqual({ source: 'Instagram', agentId: null })
  })

  it('falls back to the agent id when FUB never sent a name', () => {
    expect(agentDisplayName({ agentName: null, agentId: 7 })).toBe('#7')
    expect(initials('Mandeep Dhesi')).toBe('MD')
  })

  it('formats worklist ages from minutes to days', () => {
    const text = uiText.reports.inspector
    const now = new Date('2026-08-14T12:00:00Z')
    expect(formatAge('2026-08-14T11:58:30Z', now, text)).toBe('1m ago')
    expect(formatAge('2026-08-14T07:00:00Z', now, text)).toBe('5h ago')
    expect(formatAge('2026-08-11T12:00:00Z', now, text)).toBe('3d ago')
  })
})
