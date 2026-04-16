import { useMemo, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { useWorkflowRunsForKeyQuery } from '../../workflow-runs/data/useWorkflowRunsForKeyQuery'
import {
  formatWorkflowRunReasonCode,
  formatWorkflowRunStatus,
  getWorkflowRunStatusTone,
} from '../../workflow-runs/lib/workflowRunsDisplay'
import { formatDateTime } from '../../../shared/lib/date'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { useNotify } from '../../../shared/notifications/useNotify'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { JsonViewer } from '../../../shared/ui/JsonViewer'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { PagePagination } from '../../../shared/ui/PagePagination'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useActivateWorkflowMutation } from '../data/useActivateWorkflowMutation'
import { useArchiveWorkflowMutation } from '../data/useArchiveWorkflowMutation'
import { useDeactivateWorkflowMutation } from '../data/useDeactivateWorkflowMutation'
import { useRollbackWorkflowMutation } from '../data/useRollbackWorkflowMutation'
import { useUpdateWorkflowMutation } from '../data/useUpdateWorkflowMutation'
import { useValidateWorkflowMutation } from '../data/useValidateWorkflowMutation'
import { useWorkflowDetailQuery } from '../data/useWorkflowDetailQuery'
import { useWorkflowVersionsQuery } from '../data/useWorkflowVersionsQuery'
import {
  canActivateWorkflow,
  canArchiveWorkflow,
  canDeactivateWorkflow,
  canEditWorkflow,
  canValidateWorkflow,
  formatWorkflowStatus,
  getWorkflowStatusTone,
} from '../lib/workflowsDisplay'
import {
  createWorkflowDetailSearchParamsFromState,
  parseWorkflowDetailSearchParams,
  toWorkflowDetailRunsDraftFilters,
  type WorkflowDetailRunsFilterDraft,
} from '../lib/workflowsSearchParams'
import type { WorkflowRunSummary, WorkflowRunStatus } from '../lib/workflowSchemas'
import { WorkflowActions } from './WorkflowActions'
import { WorkflowEditModal } from './WorkflowEditModal'
import { WorkflowVersionList } from './WorkflowVersionList'

export function WorkflowDetailPage() {
  const navigate = useNavigate()
  const { key } = useParams<{ key: string }>()
  const notify = useNotify()
  const [searchParams, setSearchParams] = useSearchParams()
  const detailSearchState = useMemo(() => parseWorkflowDetailSearchParams(searchParams), [searchParams])
  const isDefinitionTab = detailSearchState.tab === 'definition'
  const runsQuery = useWorkflowRunsForKeyQuery(key, {
    status: detailSearchState.runStatus,
    page: detailSearchState.runPage,
    size: detailSearchState.runSize,
  }, {
    enabled: !isDefinitionTab,
  })
  const runFilterDraftKey = useMemo(() => detailSearchState.runStatus ?? 'ALL', [detailSearchState.runStatus])
  const [runFilterDraftState, setRunFilterDraftState] = useState<{ key: string; value: WorkflowDetailRunsFilterDraft }>(() => ({
    key: runFilterDraftKey,
    value: toWorkflowDetailRunsDraftFilters(detailSearchState),
  }))
  const runDraftFilters = runFilterDraftState.key === runFilterDraftKey ? runFilterDraftState.value : toWorkflowDetailRunsDraftFilters(detailSearchState)
  const runRows = useMemo(() => runsQuery.data?.items ?? [], [runsQuery.data?.items])
  const [isEditOpen, setEditOpen] = useState(false)
  const [rollbackVersion, setRollbackVersion] = useState<number | null>(null)
  const [validationResult, setValidationResult] = useState<{ valid: boolean; errors: string[] } | null>(null)
  const detailQuery = useWorkflowDetailQuery(key)
  const versionsQuery = useWorkflowVersionsQuery(key)
  const updateMutation = useUpdateWorkflowMutation(key)
  const validateMutation = useValidateWorkflowMutation()
  const activateMutation = useActivateWorkflowMutation(key)
  const deactivateMutation = useDeactivateWorkflowMutation(key)
  const archiveMutation = useArchiveWorkflowMutation(key)
  const rollbackMutation = useRollbackWorkflowMutation(key)

  const inspectorBody = useMemo(() => {
    if (versionsQuery.isPending) {
      return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.loadingMessage}</p>
    }
    if (versionsQuery.isError) {
      return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.errorMessage}</p>
    }
    if (!versionsQuery.data || versionsQuery.data.length === 0) {
      return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.emptyMessage}</p>
    }

    return (
      <WorkflowVersionList
        versions={versionsQuery.data}
        currentVersionNumber={detailQuery.data?.versionNumber ?? null}
        onRequestRollback={(toVersion) => setRollbackVersion(toVersion)}
        isRollbackPending={rollbackMutation.isPending}
      />
    )
  }, [detailQuery.data?.versionNumber, rollbackMutation.isPending, versionsQuery.data, versionsQuery.isError, versionsQuery.isPending])

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.workflows.detailVersionsTitle,
      body: inspectorBody,
    }),
    [inspectorBody],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  if (detailQuery.isPending) {
    return <LoadingState />
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.workflows.detailTitle} subtitle={uiText.workflows.detailSubtitle} />
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflows.detailNotFound}</p>
        </PageCard>
      </div>
    )
  }

  const workflow = detailQuery.data

  const handleUpdate = async (command: Parameters<NonNullable<typeof updateMutation['mutateAsync']>>[0]) => {
    try {
      await updateMutation.mutateAsync(command)
      setEditOpen(false)
      notify.success(uiText.workflows.editSuccess)
    } catch {
      notify.error(uiText.workflows.editError)
    }
  }

  const handleValidate = async () => {
    try {
      const result = await validateMutation.mutateAsync({
        trigger: workflow.trigger ?? {},
        graph: workflow.graph ?? {},
      })
      setValidationResult(result)
      if (result.valid) {
        notify.success(uiText.workflows.validateSuccess)
      } else {
        notify.warning(uiText.workflows.validateInvalid)
      }
    } catch {
      notify.error(uiText.workflows.validateError)
    }
  }

  const handleActivate = async () => {
    try {
      await activateMutation.mutateAsync()
      notify.success(uiText.workflows.activateSuccess)
    } catch {
      notify.error(uiText.workflows.activateError)
    }
  }

  const handleDeactivate = async () => {
    try {
      await deactivateMutation.mutateAsync()
      notify.success(uiText.workflows.deactivateSuccess)
    } catch {
      notify.error(uiText.workflows.deactivateError)
    }
  }

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync()
      notify.success(uiText.workflows.archiveSuccess)
    } catch {
      notify.error(uiText.workflows.archiveError)
    }
  }

  const handleRollback = async () => {
    if (rollbackVersion === null) {
      return
    }

    const targetVersion = rollbackVersion
    setRollbackVersion(null)
    try {
      await rollbackMutation.mutateAsync(targetVersion)
      notify.success(uiText.workflows.rollbackSuccess)
    } catch {
      notify.error(uiText.workflows.rollbackError)
    }
  }

  const isAnyActionPending =
    updateMutation.isPending ||
    validateMutation.isPending ||
    activateMutation.isPending ||
    deactivateMutation.isPending ||
    archiveMutation.isPending ||
    rollbackMutation.isPending

  const runColumns: ColumnDef<WorkflowRunSummary>[] = [
    {
      key: 'id',
      header: uiText.workflows.detailRunIdHeader,
      render: (row) => row.id,
    },
    {
      key: 'status',
      header: uiText.workflows.detailRunStatusHeader,
      render: (row) => <StatusBadge label={formatWorkflowRunStatus(row.status)} tone={getWorkflowRunStatusTone(row.status)} />,
    },
    {
      key: 'reasonCode',
      header: uiText.workflows.detailRunReasonCodeHeader,
      render: (row) => formatWorkflowRunReasonCode(row.reasonCode),
    },
    {
      key: 'startedAt',
      header: uiText.workflows.detailRunStartedHeader,
      render: (row) => formatNullableDate(row.startedAt),
    },
    {
      key: 'completedAt',
      header: uiText.workflows.detailRunCompletedHeader,
      render: (row) => formatNullableDate(row.completedAt),
    },
  ]

  const handleSelectDefinitionTab = () => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'definition',
      }),
    )
  }

  const handleSelectRunsTab = () => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
      }),
    )
  }

  const handleRunApply = () => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runStatus: runDraftFilters.status === 'ALL' ? undefined : runDraftFilters.status,
        runPage: 0,
      }),
    )
  }

  const handleRunReset = () => {
    setRunFilterDraftState({
      key: runFilterDraftKey,
      value: { status: 'ALL' },
    })
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runStatus: undefined,
        runPage: 0,
      }),
    )
  }

  const handleRunPageChange = (runPage: number) => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runPage,
      }),
    )
  }

  const handleSelectRunRow = (row: WorkflowRunSummary) => {
    navigate(routes.workflowRunDetail(row.id))
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.workflows.detailTitle} subtitle={uiText.workflows.detailSubtitle}>
        <Link className="text-sm text-[var(--color-brand)] underline" to={routes.workflows}>
          {uiText.workflows.title}
        </Link>
      </PageHeader>

      <div className="flex items-center gap-2">
        <Button type="button" size="sm" variant={isDefinitionTab ? 'default' : 'outline'} onClick={handleSelectDefinitionTab}>
          {uiText.workflows.detailTabDefinition}
        </Button>
        <Button type="button" size="sm" variant={isDefinitionTab ? 'outline' : 'default'} onClick={handleSelectRunsTab}>
          {uiText.workflows.detailTabRuns}
        </Button>
      </div>

      {isDefinitionTab ? (
        <>
          <PageCard title={uiText.workflows.detailMetadataTitle}>
            <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
              <MetadataRow label={uiText.workflows.detailKeyLabel} value={<span className="font-mono">{workflow.key}</span>} />
              <MetadataRow label={uiText.workflows.detailNameLabel} value={workflow.name} />
              <MetadataRow label={uiText.workflows.detailDescriptionLabel} value={workflow.description ?? '-'} />
              <MetadataRow
                label={uiText.workflows.detailStatusLabel}
                value={<StatusBadge label={formatWorkflowStatus(workflow.status)} tone={getWorkflowStatusTone(workflow.status)} />}
              />
              <MetadataRow label={uiText.workflows.detailVersionLabel} value={workflow.versionNumber ?? '-'} />
            </div>
          </PageCard>

          <PageCard title={uiText.workflows.detailActionsTitle}>
            <WorkflowActions
              canEdit={canEditWorkflow(workflow.status)}
              canValidate={canValidateWorkflow(workflow.status)}
              canActivate={canActivateWorkflow(workflow.status)}
              canDeactivate={canDeactivateWorkflow(workflow.status)}
              canArchive={canArchiveWorkflow(workflow.status)}
              isPending={isAnyActionPending}
              onEdit={() => setEditOpen(true)}
              onValidate={handleValidate}
              onActivate={handleActivate}
              onDeactivate={handleDeactivate}
              onArchive={handleArchive}
            />
          </PageCard>

          {validationResult ? (
            <PageCard title={uiText.workflows.validationResultTitle}>
              <p className="text-sm">
                <span className="text-[var(--color-text-muted)]">{uiText.workflows.validationResultTitle}: </span>
                <span className="font-medium">{validationResult.valid ? uiText.workflows.validationValidLabel : uiText.workflows.validationInvalidLabel}</span>
              </p>
              {!validationResult.valid && validationResult.errors.length > 0 ? (
                <div className="mt-3">
                  <p className="text-sm font-medium text-[var(--color-text)]">{uiText.workflows.validationErrorsTitle}</p>
                  <ul className="mt-1 list-inside list-disc text-sm text-[var(--color-text-muted)]">
                    {validationResult.errors.map((error) => (
                      <li key={error}>{error}</li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </PageCard>
          ) : null}

          <PageCard title={uiText.workflows.detailTriggerTitle}>
            <JsonViewer value={workflow.trigger} />
          </PageCard>

          <PageCard title={uiText.workflows.detailGraphTitle}>
            <JsonViewer value={workflow.graph} />
          </PageCard>
        </>
      ) : (
        <>
          <FilterBar
            actions={
              <>
                <Button type="button" size="sm" onClick={handleRunApply}>
                  {uiText.filters.apply}
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={handleRunReset}>
                  {uiText.filters.reset}
                </Button>
              </>
            }
          >
            <label className="flex items-center gap-2">
              <span className="sr-only">{uiText.workflows.detailRunsFilterStatusLabel}</span>
              <Select
                aria-label={uiText.workflows.detailRunsFilterStatusLabel}
                value={runDraftFilters.status}
                onChange={(event) =>
                  setRunFilterDraftState({
                    key: runFilterDraftKey,
                    value: {
                      status: event.target.value as WorkflowDetailRunsFilterDraft['status'],
                    },
                  })
                }
                className="w-[220px]"
              >
                <option value="ALL">{uiText.workflows.detailRunsFilterStatusAll}</option>
                {WORKFLOW_RUN_STATUS_OPTIONS.map((status) => (
                  <option key={status} value={status}>
                    {formatWorkflowRunStatus(status)}
                  </option>
                ))}
              </Select>
            </label>
          </FilterBar>

          <PageCard title={uiText.workflows.detailRunsTitle}>
            {runsQuery.isError ? (
              <ErrorState message={uiText.states.errorMessage} />
            ) : runsQuery.isPending ? (
              <LoadingState />
            ) : (
              <>
                <DataTable
                  columns={runColumns}
                  rows={runRows}
                  getRowKey={(row) => row.id}
                  onRowClick={handleSelectRunRow}
                  getRowAriaLabel={(row) => `${uiText.workflows.detailRunRowAriaPrefix} ${row.id}`}
                  emptyMessage={uiText.workflows.detailRunsEmptyMessage}
                />
                <div className="mt-4">
                  <PagePagination
                    page={runsQuery.data?.page ?? detailSearchState.runPage}
                    size={runsQuery.data?.size ?? detailSearchState.runSize}
                    total={runsQuery.data?.total ?? 0}
                    onPageChange={handleRunPageChange}
                  />
                </div>
              </>
            )}
          </PageCard>
        </>
      )}

      <WorkflowEditModal
        key={`${workflow.key}-${workflow.version ?? 'none'}-${isEditOpen ? 'open' : 'closed'}`}
        open={isEditOpen}
        onOpenChange={setEditOpen}
        workflow={workflow}
        onSubmit={handleUpdate}
        isSubmitting={updateMutation.isPending}
      />
      <ConfirmDialog
        open={rollbackVersion !== null}
        title={uiText.workflows.rollbackConfirmTitle}
        description={uiText.workflows.rollbackConfirmDescription}
        onOpenChange={(open) => {
          if (!open) {
            setRollbackVersion(null)
          }
        }}
        onConfirm={() => void handleRollback()}
      />
    </div>
  )
}

const WORKFLOW_RUN_STATUS_OPTIONS: WorkflowRunStatus[] = ['PENDING', 'BLOCKED', 'DUPLICATE_IGNORED', 'CANCELED', 'COMPLETED', 'FAILED']

function MetadataRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <p className="flex gap-2">
      <span className="text-[var(--color-text-muted)]">{label}:</span>
      <span>{value}</span>
    </p>
  )
}

function formatNullableDate(value: string | null): string {
  return value ? formatDateTime(value) : uiText.workflowRuns.missingValue
}
