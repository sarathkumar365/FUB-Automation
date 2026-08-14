import { useState } from 'react'
import type { AccountabilityReport, AgentCounts } from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { cn } from '@shared/lib/cn'
import { outcomeVars } from '../lib/outcomeStyles'
import { agentDisplayName, initials, pct } from '../lib/reportMath'
import { MixBar } from './ReportBits'

const t = uiText.reports

type SortKey = 'name' | 'held' | 'spoke' | 'attempted' | 'nothing' | 'engagement'
type SortDir = 'asc' | 'desc'

const GRID =
  'grid min-w-[880px] grid-cols-[minmax(210px,1.2fr)_100px_130px_130px_140px_minmax(170px,1fr)] items-center gap-3'

const sortValue: Record<SortKey, (agent: AgentCounts) => string | number> = {
  name: (agent) => agentDisplayName(agent).toLowerCase(),
  held: (agent) => agent.counts.leads,
  spoke: (agent) => agent.counts.spoke,
  attempted: (agent) => agent.counts.attempted,
  nothing: (agent) => agent.counts.nothing,
  engagement: (agent) => (agent.counts.leads > 0 ? agent.counts.spoke / agent.counts.leads : -1),
}

export function AgentTable({
  report,
  selectedAgentId,
  onSelectAgent,
}: {
  report: AccountabilityReport
  selectedAgentId: number | null
  onSelectAgent: (agentId: number) => void
}) {
  // Default lens: the red number, worst first — that's what the report is for.
  const [sortKey, setSortKey] = useState<SortKey>('nothing')
  const [sortDir, setSortDir] = useState<SortDir>('desc')

  const cycleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((dir) => (dir === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir(key === 'name' ? 'asc' : 'desc')
    }
  }

  const direction = sortDir === 'asc' ? 1 : -1
  const rows = [...report.agents].sort((a, b) => {
    const va = sortValue[sortKey](a)
    const vb = sortValue[sortKey](b)
    const cmp = typeof va === 'string' ? va.localeCompare(vb as string) : va - (vb as number)
    return cmp * direction
  })

  const headers: { key: SortKey; label: string }[] = [
    { key: 'name', label: t.table.agentHeader },
    { key: 'held', label: t.table.heldHeader },
    { key: 'spoke', label: t.table.spokeHeader },
    { key: 'attempted', label: t.table.attemptedHeader },
    { key: 'nothing', label: t.table.nothingHeader },
    { key: 'engagement', label: t.table.engagementHeader },
  ]

  return (
    <section className="overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-subtle)]">
      <div className={cn(GRID, 'border-b border-[var(--color-border)] bg-[var(--color-surface-alt)] px-5 py-1.5')}>
        {headers.map((header) => (
          <button
            key={header.key}
            type="button"
            aria-label={t.table.sortAriaLabel(header.label)}
            onClick={() => cycleSort(header.key)}
            className="cursor-pointer py-1.5 text-left text-[11px] font-bold uppercase tracking-[0.07em] text-[var(--color-text-muted)]"
          >
            {header.label}
            {sortKey === header.key ? (sortDir === 'asc' ? ' ↑' : ' ↓') : ''}
          </button>
        ))}
      </div>
      {rows.map((agent) => {
        const name = agentDisplayName(agent)
        const selected = agent.agentId === selectedAgentId
        return (
          <button
            key={agent.agentId}
            type="button"
            aria-label={t.table.rowAriaLabel(name)}
            onClick={() => onSelectAgent(agent.agentId)}
            className={cn(
              GRID,
              'w-full border-b border-[var(--color-border)] px-5 py-3 text-left transition-colors hover:bg-[var(--color-surface-alt)]',
              selected && 'bg-[var(--color-brand-soft)]',
            )}
          >
            <span className="inline-flex items-center gap-2.5">
              <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[var(--color-brand-soft)] text-[11px] font-extrabold text-[var(--color-brand)]">
                {initials(name)}
              </span>
              <span className="text-[13.5px] font-bold text-[var(--color-text)]">{name}</span>
            </span>
            <span className="font-bold text-[var(--color-text)]">{agent.counts.leads}</span>
            <span className="font-semibold" style={{ color: outcomeVars.spoke.fg }}>
              {agent.counts.spoke}
            </span>
            <span className="font-semibold" style={{ color: outcomeVars.attempted.fg }}>
              {agent.counts.attempted}
            </span>
            <span
              className={cn('font-bold', agent.counts.nothing === 0 && 'text-[var(--color-text-muted)]')}
              style={agent.counts.nothing > 0 ? { color: outcomeVars.nothing.fg } : undefined}
            >
              {agent.counts.nothing}
            </span>
            <span className="flex items-center gap-2.5">
              <MixBar counts={agent.counts} className="flex-1" />
              <span className="whitespace-nowrap text-xs font-semibold text-[var(--color-text-muted)]">
                {agent.counts.leads > 0
                  ? t.table.spokePct(pct(agent.counts.spoke, agent.counts.leads))
                  : t.table.noLeads}
              </span>
            </span>
          </button>
        )
      })}
    </section>
  )
}
