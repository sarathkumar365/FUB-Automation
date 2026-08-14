import { useState } from 'react'
import type { SourceContactReport, SourceContactSourceRow } from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { cn } from '@shared/lib/cn'
import { NextIcon } from '@shared/ui'
import { outcomeVars } from '../lib/outcomeStyles'
import { agentDisplayName, outcomeKeys, pct } from '../lib/reportMath'
import { MixBar, OutcomeDot } from './ReportBits'

const t = uiText.reports

const GRID = 'grid grid-cols-[minmax(230px,1.3fr)_90px_110px_minmax(170px,1fr)_110px] items-center gap-3'

export function LedgerView({ report }: { report: SourceContactReport }) {
  const [openSources, setOpenSources] = useState<ReadonlySet<string>>(new Set())
  const [openAgents, setOpenAgents] = useState<ReadonlySet<string>>(new Set())

  const toggleSource = (source: string) =>
    setOpenSources((previous) => toggleIn(previous, source))
  const toggleAgent = (key: string) => setOpenAgents((previous) => toggleIn(previous, key))

  return (
    <section className="mt-3.5 overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-subtle)]">
      <div
        className={cn(
          GRID,
          'border-b border-[var(--color-border)] bg-[var(--color-surface-alt)] px-5 py-2.5 text-[11px] font-bold uppercase tracking-[0.07em] text-[var(--color-text-muted)]',
        )}
      >
        <span>{t.ledger.sourceHeader}</span>
        <span>{t.ledger.leadsHeader}</span>
        <span>{t.ledger.shareHeader}</span>
        <span>{t.ledger.mixHeader}</span>
        <span>{t.ledger.spokeHeader}</span>
      </div>
      {report.sources.map((row) => (
        <SourceRow
          key={row.source}
          row={row}
          totalLeads={report.totals.leads}
          open={openSources.has(row.source)}
          onToggle={() => toggleSource(row.source)}
          openAgents={openAgents}
          onToggleAgent={toggleAgent}
        />
      ))}
      <p className="px-5 py-2.5 text-[12.5px] text-[var(--color-text-muted)]">{t.ledger.footerHint}</p>
    </section>
  )
}

function SourceRow({
  row,
  totalLeads,
  open,
  onToggle,
  openAgents,
  onToggleAgent,
}: {
  row: SourceContactSourceRow
  totalLeads: number
  open: boolean
  onToggle: () => void
  openAgents: ReadonlySet<string>
  onToggleAgent: (key: string) => void
}) {
  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        aria-label={t.ledger.toggleRowAriaLabel(row.source)}
        onClick={onToggle}
        className={cn(
          GRID,
          'w-full border-b border-[var(--color-border)] px-5 py-3 text-left transition-colors hover:bg-[var(--color-surface-alt)]',
        )}
      >
        <span className="inline-flex items-center gap-2.5 text-[13.5px] font-bold text-[var(--color-text)]">
          <NextIcon
            className={cn('h-3.5 w-3.5 shrink-0 text-[var(--color-text-muted)] transition-transform', open && 'rotate-90')}
          />
          {row.source}
        </span>
        <span className="font-bold text-[var(--color-text)]">{row.counts.leads}</span>
        <span className="text-[var(--color-text-muted)]">{pct(row.counts.leads, totalLeads)}</span>
        <MixBar counts={row.counts} />
        <span className="font-semibold text-[var(--color-text-muted)]">
          {pct(row.counts.spoke, row.counts.leads)}
        </span>
      </button>
      {open ? (
        <div className="border-b border-[var(--color-border)] bg-[var(--color-surface-alt)]">
          {row.agents.map((agent) => {
            const key = `${row.source}|${agent.agentId}`
            const agentOpen = openAgents.has(key)
            return (
              <div key={agent.agentId}>
                <button
                  type="button"
                  aria-expanded={agentOpen}
                  aria-label={t.ledger.toggleRowAriaLabel(agentDisplayName(agent))}
                  onClick={() => onToggleAgent(key)}
                  className={cn(GRID, 'w-full px-5 py-2.5 pl-11 text-left text-[13px] hover:bg-[var(--color-surface)]')}
                >
                  <span className="inline-flex items-center gap-2 font-semibold text-[var(--color-text)]">
                    <NextIcon
                      className={cn('h-3 w-3 shrink-0 text-[var(--color-text-muted)] transition-transform', agentOpen && 'rotate-90')}
                    />
                    {agentDisplayName(agent)}
                  </span>
                  <span className="font-semibold text-[var(--color-text)]">{agent.counts.leads}</span>
                  <span className="text-[var(--color-text-muted)]">
                    {t.ledger.shareOfSource(pct(agent.counts.leads, row.counts.leads))}
                  </span>
                  <MixBar counts={agent.counts} className="h-[7px]" />
                  <span className="text-[var(--color-text-muted)]">
                    {pct(agent.counts.spoke, agent.counts.leads)}
                  </span>
                </button>
                {agentOpen ? (
                  <div className="flex flex-col gap-1.5 px-5 pb-3 pl-[68px] pt-1">
                    {outcomeKeys.map((outcome) => {
                      const count = agent.counts[outcome]
                      const width = agent.counts.leads
                        ? `${((count / agent.counts.leads) * 100).toFixed(1)}%`
                        : '0%'
                      return (
                        <div
                          key={outcome}
                          className="grid grid-cols-[230px_60px_60px_minmax(120px,1fr)] items-center gap-3 text-[12.5px]"
                        >
                          <span className="inline-flex items-center gap-2 text-[var(--color-text)]">
                            <OutcomeDot outcome={outcome} className="h-2 w-2" />
                            {t.outcomes[outcome]}
                          </span>
                          <span className="font-bold text-[var(--color-text)]">{count}</span>
                          <span className="text-[var(--color-text-muted)]">
                            {pct(count, agent.counts.leads)}
                          </span>
                          <span className="block h-[5px] overflow-hidden rounded-full bg-[var(--color-surface-alt)]">
                            <span
                              className="block h-full rounded-full"
                              style={{ width, background: outcomeVars[outcome].dot }}
                            />
                          </span>
                        </div>
                      )
                    })}
                  </div>
                ) : null}
              </div>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

function toggleIn(set: ReadonlySet<string>, value: string): ReadonlySet<string> {
  const next = new Set(set)
  if (next.has(value)) next.delete(value)
  else next.add(value)
  return next
}
