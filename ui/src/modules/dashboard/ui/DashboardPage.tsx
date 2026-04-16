import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { formatWorkflowRunStatus, getWorkflowRunStatusTone } from '../../workflow-runs/lib/workflowRunsDisplay'
import type { WorkflowRunSummary } from '../../workflows/lib/workflowSchemas'
import { useDashboardSnapshotQuery } from '../data/useDashboardSnapshotQuery'

export function DashboardPage() {
  const snapshotQuery = useDashboardSnapshotQuery()
  const snapshot = snapshotQuery.data

  const panelRegion = useMemo(
    () => ({
      title: uiText.dashboard.panelTitle,
      body: (
        <div className="space-y-2 text-sm">
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.activeWorkflowsTitle}: </span>
            {snapshot?.activeWorkflows.count ?? 0}
          </p>
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.failedRunsTitle}: </span>
            {snapshot?.failedRuns.count ?? 0}
          </p>
        </div>
      ),
    }),
    [snapshot?.activeWorkflows.count, snapshot?.failedRuns.count],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.dashboard.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.inspectorDescription}</p>,
    }),
    [],
  )

  useShellRegionRegistration({
    panel: panelRegion,
    inspector: inspectorRegion,
  })

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

  const recentRuns = snapshot.recentRuns.items.slice(0, 5)
  const failedRuns = snapshot.failedRuns.items.slice(0, 5)

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <PageCard title={uiText.dashboard.activeWorkflowsTitle}>
          <p className="text-3xl font-semibold text-[var(--color-text)]">{snapshot.activeWorkflows.count}</p>
          <DashboardLink to={buildRouteWithStatus(routes.workflows, 'ACTIVE')} label={uiText.dashboard.openWorkflows} />
        </PageCard>

        <PageCard title={uiText.dashboard.recentRunsTitle}>
          {recentRuns.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.recentRunsEmpty}</p>
          ) : (
            <RunList runs={recentRuns} />
          )}
          <DashboardLink to={routes.workflowRuns} label={uiText.dashboard.openRuns} />
        </PageCard>

        <PageCard title={uiText.dashboard.failedRunsTitle}>
          <p className="text-2xl font-semibold text-[var(--color-text)]">{snapshot.failedRuns.count}</p>
          {failedRuns.length === 0 ? (
            <p className="mt-2 text-sm text-[var(--color-text-muted)]">{uiText.dashboard.failedRunsEmpty}</p>
          ) : (
            <div className="mt-2">
              <RunList runs={failedRuns} />
            </div>
          )}
          <DashboardLink to={buildRouteWithStatus(routes.workflowRuns, 'FAILED')} label={uiText.dashboard.openFailedRuns} />
        </PageCard>

        <PageCard title={uiText.dashboard.systemHealthTitle}>
          <p className="text-sm">
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.recentWebhooksLabel}: </span>
            {snapshot.systemHealth.recentWebhookCount}
          </p>
          <p className="mt-1 text-sm">
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.latestWebhookAtLabel}: </span>
            {formatNullableDate(snapshot.systemHealth.latestWebhookReceivedAt)}
          </p>
          <p className="mt-3 text-xs text-[var(--color-text-muted)]">{uiText.dashboard.placeholderHealthMessage}</p>
        </PageCard>
      </div>
    </div>
  )
}

function RunList({
  runs,
}: {
  runs: WorkflowRunSummary[]
}) {
  return (
    <div className="overflow-x-auto rounded-md border border-[var(--color-border)]">
      <table className="min-w-full text-left text-xs">
        <thead className="bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]">
          <tr>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.runIdHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.workflowKeyHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.statusHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.completedAtHeader}</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.id} className="border-t border-[var(--color-border)]">
              <td className="px-2 py-2">{run.id}</td>
              <td className="px-2 py-2 font-mono">{run.workflowKey}</td>
              <td className="px-2 py-2">
                <StatusBadge label={formatWorkflowRunStatus(run.status)} tone={getWorkflowRunStatusTone(run.status)} />
              </td>
              <td className="px-2 py-2">{formatNullableDate(run.completedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function DashboardLink({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="mt-3 inline-flex h-8 items-center justify-center rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-xs font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-alt)]"
    >
      {label}
    </Link>
  )
}

function buildRouteWithStatus(path: string, status: string): string {
  const params = new URLSearchParams()
  params.set('status', status)
  return `${path}?${params.toString()}`
}

function formatNullableDate(value: string | null): string {
  if (!value) {
    return uiText.workflowRuns.missingValue
  }
  return formatDateTime(value)
}
