import { useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '@app/useShellRegionRegistration'
import {
  reportWindowKeys,
  type AccountabilityReport as AccountabilityReportData,
  type ReportWindowKey,
  type SourceContactReport,
} from '@platform/contracts/reportingSchemas'
import { uiText } from '@shared/constants/uiText'
import { cn } from '@shared/lib/cn'
import { BarChartIcon, Button, EmptyState, ErrorState, SegmentedControl, Skeleton } from '@shared/ui'
import { PageHeader } from '@shared/ui/PageHeader'
import { useRise } from '@shared/lib/useRise'
import {
  useAccountabilityReportQuery,
  useSourceContactReportQuery,
} from '../data/useReportQueries'
import { agentDisplayName } from '../lib/reportMath'
import { AccountabilityReport } from './AccountabilityReport'
import { AgentWorklistInspector } from './AgentWorklistInspector'
import { LeadFlowReport } from './LeadFlowReport'

const t = uiText.reports

type ReportId = 'lead' | 'agents'

const WINDOW_OPTIONS = reportWindowKeys.map((key) => ({ value: key, label: t.windows[key] }))

export function ReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const report: ReportId = searchParams.get('report') === 'agents' ? 'agents' : 'lead'
  const windowParam = searchParams.get('window')
  const window: ReportWindowKey = reportWindowKeys.includes(windowParam as ReportWindowKey)
    ? (windowParam as ReportWindowKey)
    : 'yesterday'

  const [inspectorAgentId, setInspectorAgentId] = useState<number | null>(null)

  const setParams = (next: { report?: ReportId; window?: ReportWindowKey }) => {
    setSearchParams(
      (previous) => {
        const params = new URLSearchParams(previous)
        params.set('report', next.report ?? report)
        params.set('window', next.window ?? window)
        return params
      },
      { replace: true },
    )
    setInspectorAgentId(null)
  }

  const leadQuery = useSourceContactReportQuery(window)
  const agentsQuery = useAccountabilityReportQuery(window)
  const activeQuery = report === 'lead' ? leadQuery : agentsQuery

  const panelRegion = useMemo(
    () => ({
      title: t.panelTitle,
      body: (
        <ReportsPanelNav
          active={report}
          onSelect={(next) => setParams({ report: next })}
        />
      ),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setParams identity churns; report/window drive it
    [report, window],
  )

  const inspectorAgent =
    report === 'agents' && agentsQuery.data
      ? (agentsQuery.data.agents.find((agent) => agent.agentId === inspectorAgentId) ?? null)
      : null

  const inspectorRegion = useMemo(
    () =>
      inspectorAgent
        ? {
            title: agentDisplayName(inspectorAgent),
            body: (
              <AgentWorklistInspector
                agent={inspectorAgent}
                window={window}
                windowLabel={t.windows[window]}
                onClose={() => setInspectorAgentId(null)}
              />
            ),
          }
        : null,
    [inspectorAgent, window],
  )

  useShellRegionRegistration({ panel: panelRegion, inspector: inspectorRegion })

  const windowPhrase = t.windowPhrases[window]
  const headerText = report === 'lead' ? t.lead : t.agents

  return (
    <div className="space-y-4">
      <PageHeader title={headerText.title} subtitle={headerText.subtitle}>
        <SegmentedControl
          options={WINDOW_OPTIONS}
          value={window}
          onChange={(next) => setParams({ window: next })}
          ariaLabel={t.windowAriaLabel}
        />
      </PageHeader>

      <ReportBody
        report={report}
        window={window}
        windowPhrase={windowPhrase}
        leadData={leadQuery.data}
        agentsData={agentsQuery.data}
        isPending={activeQuery.isPending}
        isError={activeQuery.isError}
        onRetry={() => void activeQuery.refetch()}
        onViewThisWeek={() => setParams({ window: 'this-week' })}
        inspectorAgentId={inspectorAgentId}
        onSelectAgent={setInspectorAgentId}
      />
    </div>
  )
}

function ReportBody({
  report,
  windowPhrase,
  leadData,
  agentsData,
  isPending,
  isError,
  onRetry,
  onViewThisWeek,
  inspectorAgentId,
  onSelectAgent,
  window,
}: {
  report: ReportId
  window: ReportWindowKey
  windowPhrase: string
  leadData: SourceContactReport | undefined
  agentsData: AccountabilityReportData | undefined
  isPending: boolean
  isError: boolean
  onRetry: () => void
  onViewThisWeek: () => void
  inspectorAgentId: number | null
  onSelectAgent: (agentId: number) => void
}) {
  if (isPending) {
    return (
      <div className="mt-7 flex flex-col gap-4">
        <Skeleton animation="shimmer" className="h-[34px] w-[72%] rounded-lg" />
        <Skeleton animation="shimmer" className="h-[34px] w-[46%] rounded-lg" />
        <Skeleton
          animation="shimmer"
          shape="block"
          className="h-[420px] rounded-xl border border-[var(--color-border)]"
        />
        <p className="text-[12.5px] text-[var(--color-text-muted)]">{t.states.loadingCaption}</p>
      </div>
    )
  }

  if (isError) {
    return <ErrorState onRetry={onRetry} />
  }

  const data = report === 'lead' ? leadData : agentsData
  if (!data) {
    return <ErrorState onRetry={onRetry} />
  }

  if (data.totals.leads === 0) {
    const feedWasDown = data.window.eventsReceived === 0
    return (
      <div className="mt-24">
        <EmptyState
          hero
          icon={<BarChartIcon className="h-6 w-6" />}
          kicker={t.states.emptyKicker}
          title={t.states.emptyTitle(windowPhrase)}
          message={feedWasDown ? t.states.emptyFeedDownMessage : t.states.emptyMessage}
        >
          {window !== 'this-week' ? (
            <Button variant="secondary" onClick={onViewThisWeek}>
              {t.states.emptyCta}
            </Button>
          ) : null}
        </EmptyState>
      </div>
    )
  }

  // key remounts the section on report/window change so the entrance re-runs
  return (
    <RisingSection key={`${report}-${window}`}>
      {report === 'lead' && leadData ? (
        <LeadFlowReport report={leadData} windowPhrase={windowPhrase} />
      ) : null}
      {report === 'agents' && agentsData ? (
        <AccountabilityReport
          report={agentsData}
          selectedAgentId={inspectorAgentId}
          onSelectAgent={onSelectAgent}
        />
      ) : null}
    </RisingSection>
  )
}

/** Transform-only entrance per the shell convention (useRise — never opacity). */
function RisingSection({ children }: { children: ReactNode }) {
  const ref = useRise<HTMLDivElement>()
  return <div ref={ref}>{children}</div>
}

function ReportsPanelNav({
  active,
  onSelect,
}: {
  active: ReportId
  onSelect: (report: ReportId) => void
}) {
  const cards: { id: ReportId; title: string; scope: string }[] = [
    { id: 'lead', title: t.nav.lead.title, scope: t.nav.lead.scope },
    { id: 'agents', title: t.nav.agents.title, scope: t.nav.agents.scope },
  ]
  return (
    <div className="flex h-full flex-col">
      <p className="px-1 pb-3 text-xs text-[var(--color-text-muted)]">{t.panelSubtitle}</p>
      <nav className="flex flex-col gap-1">
        {cards.map((card) => {
          const isActive = card.id === active
          return (
            <button
              key={card.id}
              type="button"
              aria-current={isActive ? 'page' : undefined}
              onClick={() => onSelect(card.id)}
              className={cn(
                'rounded-lg px-3 py-2.5 text-left transition-colors',
                isActive ? 'bg-[var(--color-brand-soft)]' : 'hover:bg-[var(--color-surface-alt)]',
              )}
            >
              <span
                className={cn(
                  'block text-[13.5px] font-bold',
                  isActive ? 'text-[var(--color-brand)]' : 'text-[var(--color-text)]',
                )}
              >
                {card.title}
              </span>
              <span className="mt-0.5 block text-xs text-[var(--color-text-muted)]">{card.scope}</span>
            </button>
          )
        })}
      </nav>
      <div className="mt-4 border-t border-[var(--color-border)] px-1 pt-3">
        <p className="text-xs text-[var(--color-text-muted)]">{t.panelFooter}</p>
      </div>
    </div>
  )
}
