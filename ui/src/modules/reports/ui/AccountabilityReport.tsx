import type { AccountabilityReport as AccountabilityReportData } from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { agentDisplayName, pct } from '../lib/reportMath'
import { AgentTable } from './AgentTable'
import { OutcomeChip } from './ReportBits'

const t = uiText.reports

export function AccountabilityReport({
  report,
  selectedAgentId,
  onSelectAgent,
}: {
  report: AccountabilityReportData
  selectedAgentId: number | null
  onSelectAgent: (agentId: number) => void
}) {
  const withLeads = report.agents.filter((agent) => agent.counts.leads > 0)
  // Each clause only earns its place with a real number: praising a "top
  // caller" at 0% or accusing someone of 0 untouched leads reads as nonsense.
  const topCaller = [...withLeads]
    .sort((a, b) => b.counts.spoke / b.counts.leads - a.counts.spoke / a.counts.leads)
    .find((agent) => agent.counts.spoke > 0)
  const worst = [...report.agents]
    .sort((a, b) => b.counts.nothing - a.counts.nothing)
    .find((agent) => agent.counts.nothing > 0)

  return (
    <section>
      <p className="mb-1.5 mt-6 max-w-[860px] text-[22px] font-bold leading-[1.45] tracking-[-0.012em] text-[var(--color-text)]">
        {t.narration.agentsHold(
          t.narration.agentCount(report.agents.length),
          t.narration.leadCount(report.totals.leads),
        )}
        {topCaller ? (
          <>
            <AgentNameLink
              name={agentDisplayName(topCaller)}
              tone="brand"
              onClick={() => onSelectAgent(topCaller.agentId)}
            />
            {t.narration.topCallerAfter(pct(topCaller.counts.spoke, topCaller.counts.leads))}
          </>
        ) : null}
        {worst ? (
          <>
            <AgentNameLink
              name={agentDisplayName(worst)}
              tone="amber"
              onClick={() => onSelectAgent(worst.agentId)}
            />
            {t.narration.worstAfter}
            <OutcomeChip outcome="nothing">{t.narration.worstChip(worst.counts.nothing)}</OutcomeChip>
            {t.narration.sentenceEnd}
          </>
        ) : null}
      </p>
      <p className="mb-4 text-[13px] text-[var(--color-text-muted)]">{t.narration.agentsSubline}</p>

      <AgentTable report={report} selectedAgentId={selectedAgentId} onSelectAgent={onSelectAgent} />
    </section>
  )
}

function AgentNameLink({
  name,
  tone,
  onClick,
}: {
  name: string
  tone: 'brand' | 'amber'
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="cursor-pointer border-b-2 font-bold text-[var(--color-text)]"
      style={{
        borderBottomColor:
          tone === 'brand' ? 'var(--color-outcome-spoke-dot)' : 'var(--color-outcome-nothing-dot)',
      }}
    >
      {name}
    </button>
  )
}
