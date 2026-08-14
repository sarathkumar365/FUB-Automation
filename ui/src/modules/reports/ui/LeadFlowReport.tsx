import { useMemo, useState } from 'react'
import type { SourceContactReport } from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { SegmentedControl } from '@shared/ui'
import { pruneDrill, type FlowDrill } from '../lib/reportMath'
import { FlowStoryView } from './FlowStoryView'
import { LedgerView } from './LedgerView'
import { OutcomeChip } from './ReportBits'

const t = uiText.reports

type LeadView = 'flow' | 'ledger'

const VIEW_OPTIONS = [
  { value: 'flow', label: t.flow.viewFlow },
  { value: 'ledger', label: t.flow.viewLedger },
] as const

export function LeadFlowReport({
  report,
  windowPhrase,
}: {
  report: SourceContactReport
  windowPhrase: string
}) {
  const [view, setView] = useState<LeadView>('flow')
  const [rawDrill, setRawDrill] = useState<FlowDrill>({ source: null, agentId: null })
  const [showLabels, setShowLabels] = useState(true)

  // A selection can outlive the data that contained it (window switch mid-drill).
  const drill = useMemo(() => pruneDrill(report, rawDrill), [report, rawDrill])

  const capitalizedPhrase = windowPhrase.charAt(0).toUpperCase() + windowPhrase.slice(1)

  return (
    <section>
      <p className="mb-1.5 mt-6 max-w-[860px] text-[22px] font-bold leading-[1.45] tracking-[-0.012em] text-[var(--color-text)]">
        {capitalizedPhrase},{' '}
        <span className="font-extrabold">{t.narration.leadCount(report.totals.leads)}</span>
        {t.narration.leadArrived}
        <span className="font-extrabold">{t.narration.sourceCount(report.sources.length)}</span>
        {t.narration.leadHold}
        <OutcomeChip outcome="spoke">{t.narration.spokeChip(report.totals.spoke)}</OutcomeChip>
        {t.narration.chipJoin}
        <OutcomeChip outcome="attempted">{t.narration.attemptedChip(report.totals.attempted)}</OutcomeChip>
        {t.narration.chipJoinLast}
        <OutcomeChip outcome="nothing">{t.narration.nothingChip(report.totals.nothing)}</OutcomeChip>
        {t.narration.sentenceEnd}
      </p>

      <div className="mt-4">
        <SegmentedControl
          options={VIEW_OPTIONS}
          value={view}
          onChange={setView}
          ariaLabel={t.flow.viewAriaLabel}
        />
      </div>

      {view === 'flow' ? (
        <FlowStoryView
          report={report}
          drill={drill}
          onDrillChange={setRawDrill}
          showLabels={showLabels}
          onToggleLabels={() => setShowLabels((value) => !value)}
          windowPhrase={windowPhrase}
        />
      ) : (
        <LedgerView report={report} />
      )}
    </section>
  )
}
