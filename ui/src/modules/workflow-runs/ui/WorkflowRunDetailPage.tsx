import { useMemo, useState, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { useNotify } from '../../../shared/notifications/useNotify'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { JsonViewer } from '../../../shared/ui/JsonViewer'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useCancelWorkflowRunMutation } from '../data/useCancelWorkflowRunMutation'
import { useWorkflowRunDetailQuery } from '../data/useWorkflowRunDetailQuery'
import {
  canCancelWorkflowRun,
  formatWorkflowRunReasonCode,
  formatWorkflowRunStatus,
  getWorkflowRunStatusTone,
} from '../lib/workflowRunsDisplay'
import { WorkflowStepTimeline } from './WorkflowStepTimeline'

export function WorkflowRunDetailPage() {
  const notify = useNotify()
  const { runId: runIdParam } = useParams<{ runId: string }>()
  const [searchParams] = useSearchParams()
  const runId = parseRunId(runIdParam)
  const backLink = resolveBackLink(searchParams.get('backTo'))
  const [isCancelDialogOpen, setCancelDialogOpen] = useState(false)
  const detailQuery = useWorkflowRunDetailQuery(runId)
  const cancelMutation = useCancelWorkflowRunMutation(runId)

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.workflowRuns.inspectorTitle,
      body: buildDetailInspectorBody({
        isPending: detailQuery.isPending,
        isError: detailQuery.isError,
        run: detailQuery.data,
      }),
    }),
    [detailQuery.data, detailQuery.isError, detailQuery.isPending],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  if (detailQuery.isPending) {
    return <LoadingState />
  }

  if (!runId || detailQuery.isError || !detailQuery.data) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.workflowRuns.detailTitle} subtitle={uiText.workflowRuns.detailSubtitle}>
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.workflowRuns.detailBackLabel}
          </Link>
        </PageHeader>
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflowRuns.detailNotFound}</p>
        </PageCard>
      </div>
    )
  }

  const workflowRun = detailQuery.data
  const isCancelable = canCancelWorkflowRun(workflowRun.status)

  const handleConfirmCancel = async () => {
    setCancelDialogOpen(false)
    try {
      await cancelMutation.mutateAsync()
      notify.success(uiText.workflowRuns.cancelSuccess)
    } catch {
      notify.error(uiText.workflowRuns.cancelError)
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.workflowRuns.detailTitle} subtitle={uiText.workflowRuns.detailSubtitle}>
        <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
          {uiText.workflowRuns.detailBackLabel}
        </Link>
      </PageHeader>

      <PageCard title={uiText.workflowRuns.detailMetadataTitle}>
        <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
          <MetadataRow label={uiText.workflowRuns.detailRunIdLabel} value={workflowRun.id} />
          <MetadataRow
            label={uiText.workflowRuns.detailWorkflowKeyLabel}
            value={<Link className="font-mono underline" to={routes.workflowDetail(workflowRun.workflowKey)}>{workflowRun.workflowKey}</Link>}
          />
          <MetadataRow label={uiText.workflowRuns.detailVersionLabel} value={workflowRun.workflowVersionNumber} />
          <MetadataRow
            label={uiText.workflowRuns.detailStatusLabel}
            value={<StatusBadge label={formatWorkflowRunStatus(workflowRun.status)} tone={getWorkflowRunStatusTone(workflowRun.status)} />}
          />
          <MetadataRow label={uiText.workflowRuns.detailReasonCodeLabel} value={formatWorkflowRunReasonCode(workflowRun.reasonCode)} />
          <MetadataRow label={uiText.workflowRuns.detailStartedAtLabel} value={formatNullableDate(workflowRun.startedAt)} />
          <MetadataRow label={uiText.workflowRuns.detailCompletedAtLabel} value={formatNullableDate(workflowRun.completedAt)} />
          <MetadataRow
            label={uiText.workflowRuns.detailSourceLeadIdLabel}
            value={
              workflowRun.sourceLeadId ? (
                <Link
                  className="font-mono underline"
                  to={`${routes.leadDetail(workflowRun.sourceLeadId)}?backTo=${encodeURIComponent(routes.workflowRunDetail(workflowRun.id))}`}
                  data-testid="workflow-run-source-lead-link"
                >
                  {workflowRun.sourceLeadId}
                </Link>
              ) : (
                uiText.workflowRuns.missingValue
              )
            }
          />
          <MetadataRow label={uiText.workflowRuns.detailEventIdLabel} value={workflowRun.eventId ?? uiText.workflowRuns.missingValue} />
        </div>
      </PageCard>

      <PageCard title={uiText.workflowRuns.detailTriggerPayloadTitle}>
        <JsonViewer value={workflowRun.triggerPayload} />
      </PageCard>

      <PageCard title={uiText.workflowRuns.detailStepsTitle}>
        {isCancelable ? (
          <div className="mb-3 flex justify-end">
            <Button type="button" variant="destructive" size="sm" onClick={() => setCancelDialogOpen(true)} disabled={cancelMutation.isPending}>
              {uiText.workflowRuns.cancelAction}
            </Button>
          </div>
        ) : null}
        <WorkflowStepTimeline steps={workflowRun.steps} />
      </PageCard>

      <ConfirmDialog
        open={isCancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        title={uiText.workflowRuns.cancelConfirmTitle}
        description={uiText.workflowRuns.cancelConfirmDescription}
        onConfirm={handleConfirmCancel}
      />
    </div>
  )
}

function MetadataRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <p className="flex gap-2">
      <span className="text-[var(--color-text-muted)]">{label}:</span>
      <span>{value}</span>
    </p>
  )
}

function buildDetailInspectorBody({
  isPending,
  isError,
  run,
}: {
  isPending: boolean
  isError: boolean
  run: {
    id: number
    status: Parameters<typeof formatWorkflowRunStatus>[0]
    steps: { id: number }[]
  } | undefined
}) {
  if (isPending) {
    return <LoadingState />
  }
  if (isError) {
    return <ErrorState message={uiText.states.errorMessage} />
  }
  if (!run) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflowRuns.inspectorEmpty}</p>
  }

  return (
    <div className="space-y-2 text-sm">
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailRunIdLabel}: </span>
        {run.id}
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailStatusLabel}: </span>
        {formatWorkflowRunStatus(run.status)}
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailStepsTitle}: </span>
        {run.steps.length}
      </p>
    </div>
  )
}

function parseRunId(value: string | undefined): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return undefined
  }

  return parsed
}

function formatNullableDate(value: string | null): string {
  return value ? formatDateTime(value) : uiText.workflowRuns.missingValue
}

function resolveBackLink(backTo: string | null): string {
  if (!backTo) {
    return routes.workflowRuns
  }
  if (!backTo.startsWith('/admin-ui')) {
    return routes.workflowRuns
  }
  if (backTo.includes('://')) {
    return routes.workflowRuns
  }
  return backTo
}
