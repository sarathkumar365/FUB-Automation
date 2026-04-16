import { useMemo, useState, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { Button } from '../../../shared/ui/button'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { PagePagination } from '../../../shared/ui/PagePagination'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { WorkflowRunSummary, WorkflowRunStatus } from '../../workflows/lib/workflowSchemas'
import { useWorkflowRunsQuery } from '../data/useWorkflowRunsQuery'
import {
  createWorkflowRunsSearchParamsFromState,
  parseWorkflowRunsSearchParams,
  toWorkflowRunsDraftFilters,
  type WorkflowRunsFilterDraft,
  type WorkflowRunsPageSearchState,
} from '../lib/workflowRunsSearchParams'
import { formatWorkflowRunReasonCode, formatWorkflowRunStatus, getWorkflowRunStatusTone } from '../lib/workflowRunsDisplay'

const WORKFLOW_RUN_STATUS_OPTIONS: WorkflowRunStatus[] = ['PENDING', 'BLOCKED', 'DUPLICATE_IGNORED', 'CANCELED', 'COMPLETED', 'FAILED']

export function WorkflowRunsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const searchState = useMemo(() => parseWorkflowRunsSearchParams(searchParams), [searchParams])
  const listQuery = useWorkflowRunsQuery({
    status: searchState.status,
    page: searchState.page,
    size: searchState.size,
  })
  const filterDraftKey = useMemo(() => searchState.status ?? 'ALL', [searchState.status])
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: WorkflowRunsFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toWorkflowRunsDraftFilters(searchState),
  }))
  const draftFilters = draftFilterState.key === filterDraftKey ? draftFilterState.value : toWorkflowRunsDraftFilters(searchState)
  const rows = useMemo(() => listQuery.data?.items ?? [], [listQuery.data?.items])
  const failedCount = useMemo(() => rows.filter((row) => row.status === 'FAILED').length, [rows])

  const columns = useMemo<ColumnDef<WorkflowRunSummary>[]>(
    () => [
      {
        key: 'id',
        header: uiText.workflowRuns.runIdHeader,
        render: (row) => row.id,
      },
      {
        key: 'workflowKey',
        header: uiText.workflowRuns.workflowKeyHeader,
        render: (row) => <span className="font-mono text-xs">{row.workflowKey}</span>,
      },
      {
        key: 'version',
        header: uiText.workflowRuns.versionHeader,
        render: (row) => row.workflowVersionNumber,
      },
      {
        key: 'status',
        header: uiText.workflowRuns.statusHeader,
        render: (row) => <StatusBadge label={formatWorkflowRunStatus(row.status)} tone={getWorkflowRunStatusTone(row.status)} />,
      },
      {
        key: 'reasonCode',
        header: uiText.workflowRuns.reasonCodeHeader,
        render: (row) => formatWorkflowRunReasonCode(row.reasonCode),
      },
      {
        key: 'startedAt',
        header: uiText.workflowRuns.startedAtHeader,
        render: (row) => formatNullableDate(row.startedAt),
      },
      {
        key: 'completedAt',
        header: uiText.workflowRuns.completedAtHeader,
        render: (row) => formatNullableDate(row.completedAt),
      },
    ],
    [],
  )

  const panelRegion = useMemo(
    () => ({
      title: uiText.workflowRuns.panelTitle,
      body: (
        <div className="space-y-2 text-sm">
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.panelShownCountLabel}: </span>
            {rows.length}
          </p>
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.panelFailedCountLabel}: </span>
            {failedCount}
          </p>
        </div>
      ),
    }),
    [failedCount, rows.length],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.workflowRuns.inspectorTitle,
      body: buildInspectorBody(searchState.selectedRunId, rows),
    }),
    [rows, searchState.selectedRunId],
  )

  useShellRegionRegistration({
    panel: panelRegion,
    inspector: inspectorRegion,
  })

  const handleApply = () => {
    const nextState: WorkflowRunsPageSearchState = {
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      page: 0,
      size: searchState.size,
      selectedRunId: undefined,
    }
    setSearchParams(createWorkflowRunsSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilterState({
      key: filterDraftKey,
      value: { status: 'ALL' },
    })
    setSearchParams(
      createWorkflowRunsSearchParamsFromState({
        page: 0,
        size: searchState.size,
      }),
    )
  }

  const handlePageChange = (page: number) => {
    setSearchParams(
      createWorkflowRunsSearchParamsFromState({
        ...searchState,
        page,
        selectedRunId: undefined,
      }),
    )
  }

  const handleSelectRow = (row: WorkflowRunSummary) => {
    setSearchParams(
      createWorkflowRunsSearchParamsFromState({
        ...searchState,
        selectedRunId: row.id,
      }),
    )
    navigate(routes.workflowRunDetail(row.id))
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.workflowRuns.title} subtitle={uiText.workflowRuns.subtitle} />

      <FilterBar
        actions={
          <>
            <Button type="button" size="sm" onClick={handleApply}>
              {uiText.filters.apply}
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={handleReset}>
              {uiText.filters.reset}
            </Button>
          </>
        }
      >
        <ControlGroup label={uiText.workflowRuns.filterStatusLabel}>
          <Select
            aria-label={uiText.workflowRuns.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              setDraftFilterState({
                key: filterDraftKey,
                value: {
                  status: event.target.value as WorkflowRunsFilterDraft['status'],
                },
              })
            }
            className="w-[220px]"
          >
            <option value="ALL">{uiText.workflowRuns.filterStatusAll}</option>
            {WORKFLOW_RUN_STATUS_OPTIONS.map((status) => (
              <option key={status} value={status}>
                {formatWorkflowRunStatus(status)}
              </option>
            ))}
          </Select>
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.workflowRuns.tableTitle}>
        {listQuery.isError ? (
          <ErrorState message={uiText.states.errorMessage} />
        ) : listQuery.isPending ? (
          <LoadingState />
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={rows}
              getRowKey={(row) => row.id}
              onRowClick={handleSelectRow}
              selectedRowKey={searchState.selectedRunId ?? null}
              getRowAriaLabel={(row) => `${uiText.workflowRuns.rowAriaLabelPrefix} ${row.id}`}
              emptyMessage={uiText.workflowRuns.tableEmptyMessage}
            />
            <div className="mt-4">
              <PagePagination
                page={listQuery.data?.page ?? searchState.page}
                size={listQuery.data?.size ?? searchState.size}
                total={listQuery.data?.total ?? 0}
                onPageChange={handlePageChange}
              />
            </div>
          </>
        )}
      </PageCard>
    </div>
  )
}

function ControlGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex items-center gap-2">
      <span className="sr-only">{label}</span>
      {children}
    </label>
  )
}

function buildInspectorBody(selectedRunId: number | undefined, rows: WorkflowRunSummary[]) {
  if (!selectedRunId) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflowRuns.inspectorEmpty}</p>
  }

  const selected = rows.find((row) => row.id === selectedRunId)
  if (!selected) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflowRuns.inspectorEmpty}</p>
  }

  return (
    <div className="space-y-2 text-sm">
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailRunIdLabel}: </span>
        {selected.id}
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailWorkflowKeyLabel}: </span>
        <span className="font-mono">{selected.workflowKey}</span>
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.detailStatusLabel}: </span>
        {formatWorkflowRunStatus(selected.status)}
      </p>
    </div>
  )
}

function formatNullableDate(value: string | null): string {
  return value ? formatDateTime(value) : uiText.workflowRuns.missingValue
}
