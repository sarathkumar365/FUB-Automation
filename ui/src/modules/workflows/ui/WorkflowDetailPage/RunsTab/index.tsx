/**
 * Runs tab for the workflow detail page.
 *
 * Lifted from the old monolith with the FilterBar border removed — filters
 * now sit inline above the table as a chip-style row. All URL search-param
 * semantics are preserved (backward-compatible with the existing tests).
 */
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { routes } from '../../../../../shared/constants/routes'
import { uiText } from '../../../../../shared/constants/uiText'
import { Button } from '../../../../../shared/ui/button'
import { DataTable, type ColumnDef } from '../../../../../shared/ui/DataTable'
import { ErrorState } from '../../../../../shared/ui/ErrorState'
import { LoadingState } from '../../../../../shared/ui/LoadingState'
import { PagePagination } from '../../../../../shared/ui/PagePagination'
import { FilterBar } from '../../../../../shared/ui/FilterBar'
import { Select } from '../../../../../shared/ui/select'
import { StatusBadge } from '../../../../../shared/ui/StatusBadge'
import { formatDateTime } from '../../../../../shared/lib/date'
import { useWorkflowRunsForKeyQuery } from '../../../../workflow-runs/data/useWorkflowRunsForKeyQuery'
import {
  formatWorkflowRunReasonCode,
  formatWorkflowRunStatus,
  getWorkflowRunStatusTone,
} from '../../../../workflow-runs/lib/workflowRunsDisplay'
import type { WorkflowResponse, WorkflowRunStatus, WorkflowRunSummary } from '../../../lib/workflowSchemas'
import {
  createWorkflowDetailSearchParamsFromState,
  parseWorkflowDetailSearchParams,
  toWorkflowDetailRunsDraftFilters,
  type WorkflowDetailRunsFilterDraft,
} from '../../../lib/workflowsSearchParams'

const WORKFLOW_RUN_STATUS_OPTIONS: WorkflowRunStatus[] = [
  'PENDING',
  'BLOCKED',
  'DUPLICATE_IGNORED',
  'CANCELED',
  'COMPLETED',
  'FAILED',
]

export interface RunsTabProps {
  workflow: WorkflowResponse
}

export function RunsTab({ workflow }: RunsTabProps) {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const detailSearchState = useMemo(
    () => parseWorkflowDetailSearchParams(searchParams),
    [searchParams],
  )
  const runsQuery = useWorkflowRunsForKeyQuery(
    workflow.key,
    {
      status: detailSearchState.runStatus,
      page: detailSearchState.runPage,
      size: detailSearchState.runSize,
    },
    { enabled: true },
  )

  const runFilterDraftKey = detailSearchState.runStatus ?? 'ALL'
  const [runFilterDraftState, setRunFilterDraftState] = useState<{
    key: string
    value: WorkflowDetailRunsFilterDraft
  }>(() => ({
    key: runFilterDraftKey,
    value: toWorkflowDetailRunsDraftFilters(detailSearchState),
  }))
  const runDraftFilters =
    runFilterDraftState.key === runFilterDraftKey
      ? runFilterDraftState.value
      : toWorkflowDetailRunsDraftFilters(detailSearchState)
  const runRows = runsQuery.data?.items ?? []

  const columns: ColumnDef<WorkflowRunSummary>[] = [
    {
      key: 'id',
      header: uiText.workflows.detailRunIdHeader,
      render: (row) => row.id,
    },
    {
      key: 'status',
      header: uiText.workflows.detailRunStatusHeader,
      render: (row) => (
        <StatusBadge
          label={formatWorkflowRunStatus(row.status)}
          tone={getWorkflowRunStatusTone(row.status)}
        />
      ),
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

  const handleApply = () => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runStatus: runDraftFilters.status === 'ALL' ? undefined : runDraftFilters.status,
        runPage: 0,
      }),
    )
  }

  const handleReset = () => {
    setRunFilterDraftState({ key: runFilterDraftKey, value: { status: 'ALL' } })
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runStatus: undefined,
        runPage: 0,
      }),
    )
  }

  const handlePageChange = (runPage: number) => {
    setSearchParams(
      createWorkflowDetailSearchParamsFromState({
        ...detailSearchState,
        tab: 'runs',
        runPage,
      }),
    )
  }

  const handleSelectRow = (row: WorkflowRunSummary) => {
    const searchForBackLink = createWorkflowDetailSearchParamsFromState({
      ...detailSearchState,
      tab: 'runs',
    })
    const backTo = buildPathWithSearch(routes.workflowDetail(workflow.key), searchForBackLink)
    navigate(`${routes.workflowRunDetail(row.id)}?${new URLSearchParams({ backTo }).toString()}`)
  }

  return (
    <div className="space-y-4">
      <FilterBar
        bordered={false}
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
        <label className="flex items-center gap-2">
          <span className="text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
            {uiText.workflows.detailRunsFilterStatusLabel}
          </span>
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

      <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        {runsQuery.isError ? (
          <ErrorState message={uiText.states.errorMessage} />
        ) : runsQuery.isPending ? (
          <LoadingState />
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={runRows}
              getRowKey={(row) => row.id}
              onRowClick={handleSelectRow}
              getRowAriaLabel={(row) =>
                `${uiText.workflows.detailRunRowAriaPrefix} ${row.id}`
              }
              emptyMessage={uiText.workflows.detailRunsEmptyMessage}
            />
            <div className="mt-4">
              <PagePagination
                page={runsQuery.data?.page ?? detailSearchState.runPage}
                size={runsQuery.data?.size ?? detailSearchState.runSize}
                total={runsQuery.data?.total ?? 0}
                onPageChange={handlePageChange}
              />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function formatNullableDate(value: string | null): string {
  return value ? formatDateTime(value) : uiText.workflowRuns.missingValue
}

function buildPathWithSearch(pathname: string, searchParams: URLSearchParams): string {
  const search = searchParams.toString()
  return search.length > 0 ? `${pathname}?${search}` : pathname
}
