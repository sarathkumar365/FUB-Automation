import { Link, useNavigate } from 'react-router-dom'
import { routes } from '@shared/constants/routes'
import { uiText } from '@shared/constants/uiText'
import { formatDateTime } from '@shared/lib/date'
import { DataTable, type ColumnDef } from '@shared/ui/DataTable'
import { ErrorState } from '@shared/ui/ErrorState'
import { LoadingState } from '@shared/ui/LoadingState'
import { PageCard } from '@shared/ui/PageCard'
import { PageHeader } from '@shared/ui/PageHeader'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { formatWorkflowRunStatus, getWorkflowRunStatusTone } from '@modules/workflow-runs/lib/workflowRunsDisplay'
import type { WorkflowRunStatus } from '@platform/contracts/workflowSchemas'
import type { DashboardFailureRow, DashboardRunRow, Delta } from '@platform/contracts/dashboardSchemas'
import { useDashboardSnapshotQuery } from '../data/useDashboardSnapshotQuery'
import { AreaChart } from './charts/AreaChart'
import { Sparkbars } from './charts/Sparkbars'
import './DashboardPage.css'

type DeltaTone = 'ok' | 'bad' | 'muted'
type DeltaPolarity = 'rate' | 'failures' | 'neutral'
type DeltaView = { text: string; tone: DeltaTone; direction: Delta['direction'] }

export function DashboardPage() {
  const navigate = useNavigate()
  const snapshotQuery = useDashboardSnapshotQuery()
  const snapshot = snapshotQuery.data

  if (snapshotQuery.isPending) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />
        <LoadingState />
      </div>
    )
  }

  if (snapshotQuery.isError || !snapshot) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />
        <ErrorState onRetry={() => void snapshotQuery.refetch()} />
      </div>
    )
  }

  const { hero, funnel, throughput, recentRuns, needsAttention } = snapshot
  const successRateText =
    hero.stats.successRate.value === null
      ? uiText.workflowRuns.missingValue
      : `${hero.stats.successRate.value.toFixed(1)}%`

  const runsDelta = makeDeltaView(hero.stats.runs.delta, 'neutral', (v) => signed(v, `${Math.abs(Math.round(v))}%`))
  const successDelta = makeDeltaView(hero.stats.successRate.delta, 'rate', (v) => signed(v, `${Math.abs(v).toFixed(1)}pt`))
  const failuresDelta = makeDeltaView(hero.stats.openFailures.delta, 'failures', (v) => signed(v, `${Math.abs(Math.round(v))}`))

  const runColumns: ColumnDef<DashboardRunRow>[] = [
    { key: 'id', header: uiText.dashboard.runIdHeader, render: (r) => <span className="font-mono text-xs">{r.id}</span> },
    { key: 'wf', header: uiText.dashboard.workflowKeyHeader, render: (r) => <span className="font-mono text-xs">{r.workflowKey}</span> },
    {
      key: 'status',
      header: uiText.dashboard.statusHeader,
      render: (r) => <StatusBadge label={formatWorkflowRunStatus(r.status)} tone={getWorkflowRunStatusTone(r.status)} />,
    },
    {
      key: 'duration',
      header: uiText.dashboard.durationHeader,
      render: (r) => (
        <span className="font-mono text-xs text-[var(--color-text-muted)]">
          {r.durationSec === null ? uiText.workflowRuns.missingValue : `${r.durationSec}s`}
        </span>
      ),
    },
    {
      key: 'completed',
      header: uiText.dashboard.completedAtHeader,
      render: (r) => (
        <span className="font-mono text-xs text-[var(--color-text-muted)]">
          {r.completedAt === null ? uiText.workflowRuns.missingValue : formatDateTime(r.completedAt)}
        </span>
      ),
    },
  ]

  return (
    <div className="dash-root mx-auto flex w-full flex-col gap-[18px] lg:w-[85%]">
      {/* Hero — health headline + throughput */}
      <section className="relative overflow-hidden rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-7 shadow-[var(--shadow-subtle)]">
        <div className="dash-herowash" aria-hidden="true" />
        <div className="relative z-[1] grid grid-cols-1 items-center gap-9 lg:grid-cols-[1.05fr_1fr]">
          <div>
            <p className="text-[11px] font-extrabold uppercase tracking-[0.12em] text-[var(--color-text-muted)]">
              {uiText.dashboard.kicker}
            </p>
            <div className="mt-3 flex items-center gap-3.5">
              <h1 className="dash-bignum m-0 text-[46px] font-extrabold text-[var(--color-text)]">
                {uiText.dashboard.health[hero.state]}
              </h1>
              <LivePill />
            </div>
            <p className="mt-3 max-w-[440px] text-sm leading-relaxed text-[var(--color-text-muted)]">
              {uiText.dashboard.subtitle}
            </p>
            <p className="mb-5 mt-1 font-mono text-[11px] text-[var(--color-text-muted)]">
              {uiText.dashboard.updatedLabel} {formatDateTime(snapshot.window.to)}
            </p>
            <div className="flex items-start border-t border-[var(--color-border)] pt-4">
              <HeroStat label={uiText.dashboard.runsTitle} value={hero.stats.runs.value} delta={runsDelta} />
              <Divider />
              <HeroStat label={uiText.dashboard.successRateTitle} value={successRateText} delta={successDelta} />
              <Divider />
              <HeroStat
                label={uiText.dashboard.openFailuresTitle}
                value={hero.openFailures}
                valueTone={hero.openFailures > 0 ? 'bad' : undefined}
                delta={failuresDelta}
              />
            </div>
          </div>

          <div>
            <div className="mb-2.5 flex items-baseline justify-between">
              <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-[var(--color-text-muted)]">
                {uiText.dashboard.throughputLabel}
              </span>
              <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-[var(--color-text-muted)]">
                <span className="dash-livedot inline-block h-1.5 w-1.5 rounded-full bg-[var(--color-live)]" />
                {throughput.perMin}
                {uiText.dashboard.perMinLabel}
              </span>
            </div>
            <AreaChart data={throughput.series} height={150} />
            <div className="mt-1.5 flex justify-between font-mono text-[10px] text-[var(--color-text-muted)]">
              <span>{uiText.dashboard.rangeAgoLabel}</span>
              <span>
                {uiText.dashboard.peakLabel} {throughput.peak} · {uiText.dashboard.avgLabel} {throughput.avg}
              </span>
              <span>{uiText.dashboard.nowLabel}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Funnel rail — four volume counts (no conversion %s in v1) */}
      <section className="flex flex-col overflow-hidden rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-subtle)] sm:flex-row sm:items-stretch">
        <FunnelStage label={uiText.dashboard.funnelIngested} sub={uiText.dashboard.funnelIngestedSub} value={funnel.ingested.value} spark={funnel.ingested.spark} />
        <Chevron />
        <FunnelStage label={uiText.dashboard.funnelDomainEvents} sub={uiText.dashboard.funnelDomainEventsSub} value={funnel.domainEvents.value} spark={funnel.domainEvents.spark} />
        <Chevron />
        <FunnelStage label={uiText.dashboard.funnelRuns} sub={uiText.dashboard.funnelRunsSub} value={funnel.runs.value} spark={funnel.runs.spark} />
        <Chevron />
        <FunnelStage label={uiText.dashboard.funnelFailed} sub={uiText.dashboard.funnelFailedSub} value={funnel.failed.value} spark={funnel.failed.spark} tone="bad" />
      </section>

      {/* Recent runs + needs attention */}
      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[1.7fr_1fr]">
        <PageCard title={uiText.dashboard.recentRunsTitle}>
          <DataTable
            columns={runColumns}
            rows={recentRuns}
            getRowKey={(r) => r.id}
            emptyMessage={uiText.dashboard.recentRunsEmpty}
            onRowClick={(r) => navigate(routes.workflowRunDetail(r.id))}
            getRowAriaLabel={(r) => `${uiText.dashboard.runRowAriaLabelPrefix} ${r.id}`}
            rowAccent={(r) => statusRailColor(r.status)}
          />
          <Link
            to={routes.workflowRuns}
            className="mt-3.5 inline-flex h-8 items-center justify-center rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-xs font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-alt)]"
          >
            {uiText.dashboard.openRuns}
          </Link>
        </PageCard>

        <PageCard title={uiText.dashboard.needsAttentionTitle}>
          {needsAttention.length === 0 ? (
            <p className="flex items-center gap-2 py-3 text-sm font-semibold text-[var(--color-status-ok)]">
              <CheckIcon />
              {uiText.dashboard.needsAttentionEmpty}
            </p>
          ) : (
            <ul className="flex flex-col gap-2.5">
              {needsAttention.map((row) => (
                <AttentionRow key={row.ref} row={row} onSelect={() => navigate(routes.workflowRunDetail(Number(row.ref)))} />
              ))}
            </ul>
          )}
        </PageCard>
      </div>
    </div>
  )
}

function Divider() {
  return <div className="w-px self-stretch bg-[var(--color-border)]" />
}

function LivePill() {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-[var(--color-status-ok)] bg-[var(--color-status-ok-bg)] px-3 py-1 text-[11px] font-semibold text-[var(--color-status-ok)]">
      <span className="dash-livedot inline-block h-1.5 w-1.5 rounded-full bg-[var(--color-live)]" />
      {uiText.dashboard.livePillLabel}
    </span>
  )
}

function HeroStat({
  label,
  value,
  valueTone,
  delta,
}: {
  label: string
  value: string | number
  valueTone?: 'bad'
  delta: DeltaView | null
}) {
  return (
    <div className="px-[22px] first:pl-0">
      <div className="text-[11px] font-bold uppercase tracking-[0.05em] text-[var(--color-text-muted)]">{label}</div>
      <div
        className="dash-bignum mt-1.5 font-mono text-2xl font-bold"
        style={{ color: valueTone === 'bad' ? 'var(--color-status-bad)' : 'var(--color-text)' }}
      >
        {value}
      </div>
      <div className="mt-1 h-4 text-[11px]">
        <DeltaIndicator view={delta} />
      </div>
    </div>
  )
}

function DeltaIndicator({ view }: { view: DeltaView | null }) {
  if (view === null) {
    return <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.missingValue}</span>
  }
  const color =
    view.tone === 'ok' ? 'var(--color-status-ok)' : view.tone === 'bad' ? 'var(--color-status-bad)' : 'var(--color-text-muted)'
  return (
    <span className="inline-flex items-center gap-1 font-bold" style={{ color }}>
      {view.direction !== 'FLAT' ? (
        <svg
          viewBox="0 0 24 24"
          width="12"
          height="12"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{ transform: view.direction === 'DOWN' ? 'rotate(180deg)' : 'none' }}
        >
          <path d="M12 19V5M5 12l7-7 7 7" />
        </svg>
      ) : null}
      {view.text}
    </span>
  )
}

function FunnelStage({
  label,
  sub,
  value,
  spark,
  tone,
}: {
  label: string
  sub: string
  value: number
  spark: number[]
  tone?: 'bad'
}) {
  const color = tone === 'bad' ? 'var(--color-status-bad)' : 'var(--color-brand)'
  return (
    <div className="min-w-0 flex-1 p-4">
      <div className="flex items-center gap-2">
        <span className="inline-block h-[7px] w-[7px] rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />
        <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-[var(--color-text-muted)]">{label}</span>
      </div>
      <div className="mt-2 flex items-baseline gap-1.5">
        <span
          className="dash-bignum font-mono text-3xl font-bold"
          style={{ color: tone === 'bad' ? 'var(--color-status-bad)' : 'var(--color-text)' }}
        >
          {value}
        </span>
        <span className="text-[11px] text-[var(--color-text-muted)]">{sub}</span>
      </div>
      <div className="mt-2.5">
        <Sparkbars data={spark} height={26} color={color} />
      </div>
    </div>
  )
}

function Chevron() {
  return (
    <div className="hidden flex-shrink-0 items-center self-stretch border-l border-[var(--color-border)] px-3.5 sm:flex">
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="var(--color-text-muted)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="m9 18 6-6-6-6" />
      </svg>
    </div>
  )
}

function AttentionRow({ row, onSelect }: { row: DashboardFailureRow; onSelect: () => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        className="flex w-full items-center gap-3 rounded-md bg-[var(--color-status-bad-bg)] px-3 py-2.5 text-left transition-colors hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)]"
      >
        <span className="flex-shrink-0 rounded px-1.5 py-1 text-[10px] font-extrabold uppercase tracking-[0.06em] text-[var(--color-status-bad)]">
          {row.status}
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-2">
            <span className="font-mono text-xs font-semibold text-[var(--color-status-bad)]">#{row.ref}</span>
            <span className="truncate font-mono text-[11px] text-[var(--color-text-muted)]">
              {row.reason ?? uiText.workflowRuns.missingValue}
            </span>
          </span>
          <span className="mt-0.5 block font-mono text-[10.5px] text-[var(--color-text-muted)]">
            {row.workflowKey} · {formatAge(row.ageSeconds)}
          </span>
        </span>
      </button>
    </li>
  )
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  )
}

function statusRailColor(status: WorkflowRunStatus): string {
  const tone = getWorkflowRunStatusTone(status)
  if (tone === 'error') return 'var(--color-status-bad)'
  if (tone === 'success') return 'var(--color-status-ok)'
  return 'var(--color-brand)'
}

function makeDeltaView(delta: Delta, polarity: DeltaPolarity, format: (value: number) => string): DeltaView | null {
  if (delta.value === null) {
    return null
  }
  const tone: DeltaTone =
    delta.direction === 'FLAT' || polarity === 'neutral'
      ? 'muted'
      : polarity === 'rate'
        ? delta.direction === 'UP'
          ? 'ok'
          : 'bad'
        : delta.direction === 'UP'
          ? 'bad'
          : 'ok'
  return { text: format(delta.value), tone, direction: delta.direction }
}

function signed(value: number, body: string): string {
  const sign = value > 0 ? '+' : value < 0 ? '−' : ''
  return `${sign}${body}`
}

function formatAge(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`
  return `${Math.floor(seconds / 3600)}h`
}
