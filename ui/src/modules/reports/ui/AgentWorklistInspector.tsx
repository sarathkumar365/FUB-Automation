import type { AgentCounts, ReportWindowKey } from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { CloseIcon, ErrorState, LoadingState } from '@shared/ui'
import { useUnreachedLeadsQuery } from '../data/useReportQueries'
import { outcomeVars } from '../lib/outcomeStyles'
import { formatAge, type OutcomeKey } from '../lib/reportMath'
import { OutcomeDot } from './ReportBits'

const t = uiText.reports

export function AgentWorklistInspector({
  agent,
  window,
  windowLabel,
  onClose,
}: {
  agent: AgentCounts
  window: ReportWindowKey
  windowLabel: string
  onClose: () => void
}) {
  const query = useUnreachedLeadsQuery(agent.agentId, window)

  return (
    <div>
      <div className="flex items-start justify-between gap-2">
        <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-[var(--color-text-muted)]">
          {t.inspector.kicker(windowLabel)}
        </p>
        <button
          type="button"
          aria-label={t.inspector.closeAriaLabel}
          onClick={onClose}
          className="rounded-md p-1 text-[var(--color-text-muted)] hover:bg-[var(--color-surface-alt)]"
        >
          <CloseIcon className="h-[18px] w-[18px]" />
        </button>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2">
        <StatTile outcome="spoke" value={agent.counts.spoke} caption={t.inspector.tileSpoke} />
        <StatTile outcome="attempted" value={agent.counts.attempted} caption={t.inspector.tileAttempted} />
        <StatTile outcome="nothing" value={agent.counts.nothing} caption={t.inspector.tileWaiting} />
      </div>

      <div className="mt-4 border-t border-[var(--color-border)] pt-3.5">
        <p className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-[var(--color-text-muted)]">
          {t.inspector.worklistKicker(agent.counts.nothing)}
        </p>

        {query.isPending ? <LoadingState /> : null}
        {query.isError ? <ErrorState onRetry={() => void query.refetch()} /> : null}
        {query.data ? <Worklist data={query.data} /> : null}
      </div>
    </div>
  )
}

function Worklist({ data }: { data: NonNullable<ReturnType<typeof useUnreachedLeadsQuery>['data']> }) {
  const now = new Date()
  if (data.leads.length === 0) {
    return <p className="my-2.5 text-[13.5px] text-[var(--color-text-muted)]">{t.inspector.allReached}</p>
  }
  return (
    <div>
      {data.leads.map((lead) => (
        <div
          key={lead.sourcePersonId}
          className="flex flex-col gap-1 border-b border-[var(--color-border)] py-2.5"
        >
          <div className="flex items-center justify-between gap-2.5">
            <span className="inline-flex items-center gap-2 text-[13.5px] font-bold text-[var(--color-text)]">
              <OutcomeDot outcome="nothing" className="h-[7px] w-[7px]" />
              {lead.name ?? t.inspector.unnamedLead}
            </span>
            <span className="font-mono text-xs text-[var(--color-text-muted)]">
              {lead.phone ?? lead.email ?? ''}
            </span>
          </div>
          <span className="pl-[15px] text-xs text-[var(--color-text-muted)]">
            {t.inspector.leadMeta(
              lead.source ?? t.inspector.unknownSource,
              formatAge(lead.arrivedAt, now, t.inspector),
            )}
          </span>
        </div>
      ))}
      {data.truncated ? (
        <p className="mt-3 text-[11.5px] text-[var(--color-text-muted)]">
          {t.inspector.truncatedNote(data.limit)}
        </p>
      ) : null}
      <p className="mb-4 mt-3 text-[11.5px] text-[var(--color-text-muted)]">{t.inspector.footerNote}</p>
    </div>
  )
}

function StatTile({
  outcome,
  value,
  caption,
}: {
  outcome: OutcomeKey
  value: number
  caption: string
}) {
  return (
    <div className="rounded-lg border border-[var(--color-border)] px-3 py-2.5">
      <p className="text-xl font-extrabold" style={{ color: outcomeVars[outcome].fg }}>
        {value}
      </p>
      <p className="mt-0.5 text-[11px] font-semibold uppercase tracking-[0.05em] text-[var(--color-text-muted)]">
        {caption}
      </p>
    </div>
  )
}
