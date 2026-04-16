import { useMemo, useState, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import type { CreateWorkflowCommand } from '../../../platform/ports/workflowPort'
import { uiText } from '../../../shared/constants/uiText'
import { routes } from '../../../shared/constants/routes'
import { useNotify } from '../../../shared/notifications/useNotify'
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
import type { WorkflowResponse, WorkflowStatus } from '../lib/workflowSchemas'
import { formatWorkflowStatus, getWorkflowStatusTone } from '../lib/workflowsDisplay'
import {
  createWorkflowsSearchParamsFromState,
  parseWorkflowsSearchParams,
  toWorkflowsDraftFilters,
  type WorkflowsFilterDraft,
  type WorkflowsPageSearchState,
} from '../lib/workflowsSearchParams'
import { useWorkflowsQuery } from '../data/useWorkflowsQuery'
import { useCreateWorkflowMutation } from '../data/useCreateWorkflowMutation'
import { WorkflowCreateModal } from './WorkflowCreateModal'

const WORKFLOW_STATUS_OPTIONS: WorkflowStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED']

export function WorkflowsPage() {
  const navigate = useNavigate()
  const notify = useNotify()
  const [searchParams, setSearchParams] = useSearchParams()
  const [isCreateOpen, setCreateOpen] = useState(false)
  const searchState = useMemo(() => parseWorkflowsSearchParams(searchParams), [searchParams])
  const createWorkflowMutation = useCreateWorkflowMutation()
  const listQuery = useWorkflowsQuery({
    status: searchState.status,
    page: searchState.page,
    size: searchState.size,
  })
  const filterDraftKey = useMemo(() => searchState.status ?? 'ALL', [searchState.status])
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: WorkflowsFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toWorkflowsDraftFilters(searchState),
  }))
  const draftFilters = draftFilterState.key === filterDraftKey ? draftFilterState.value : toWorkflowsDraftFilters(searchState)
  const rows = useMemo(() => listQuery.data?.items ?? [], [listQuery.data?.items])

  const columns = useMemo<ColumnDef<WorkflowResponse>[]>(
    () => [
      {
        key: 'key',
        header: uiText.workflows.keyHeader,
        render: (row) => <span className="font-mono text-xs">{row.key}</span>,
      },
      {
        key: 'name',
        header: uiText.workflows.nameHeader,
        render: (row) => row.name,
      },
      {
        key: 'status',
        header: uiText.workflows.statusHeader,
        render: (row) => <StatusBadge label={formatWorkflowStatus(row.status)} tone={getWorkflowStatusTone(row.status)} />,
      },
      {
        key: 'version',
        header: uiText.workflows.versionHeader,
        render: (row) => row.versionNumber ?? '-',
      },
      {
        key: 'created',
        header: uiText.workflows.createdHeader,
        render: () => uiText.workflows.createdAtUnknown,
      },
    ],
    [],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.workflows.inspectorTitle,
      body: buildInspectorBody(searchState.selectedKey, rows),
    }),
    [rows, searchState.selectedKey],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  const handleApply = () => {
    const nextState: WorkflowsPageSearchState = {
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      page: 0,
      size: searchState.size,
      selectedKey: undefined,
    }
    setSearchParams(createWorkflowsSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilterState({
      key: filterDraftKey,
      value: { status: 'ALL' },
    })
    setSearchParams(
      createWorkflowsSearchParamsFromState({
        page: 0,
        size: searchState.size,
      }),
    )
  }

  const handlePageChange = (page: number) => {
    setSearchParams(
      createWorkflowsSearchParamsFromState({
        ...searchState,
        page,
        selectedKey: undefined,
      }),
    )
  }

  const handleSelectRow = (row: WorkflowResponse) => {
    setSearchParams(
      createWorkflowsSearchParamsFromState({
        ...searchState,
        selectedKey: row.key,
      }),
    )
    navigate(routes.workflowDetail(row.key))
  }

  const handleCreateWorkflow = async (command: CreateWorkflowCommand) => {
    try {
      await createWorkflowMutation.mutateAsync(command)
      setCreateOpen(false)
      setSearchParams(
        createWorkflowsSearchParamsFromState({
          ...searchState,
          page: 0,
          selectedKey: undefined,
        }),
      )
      notify.success(uiText.workflows.createSuccess)
    } catch {
      notify.error(uiText.workflows.createError)
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.workflows.title} subtitle={uiText.workflows.subtitleCreate}>
        <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
          {uiText.workflows.createButton}
        </Button>
      </PageHeader>

      <FilterBar
        actions={
          <>
            <Button type="button" size="sm" onClick={handleApply} aria-label={uiText.filters.apply}>
              {uiText.filters.apply}
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={handleReset} aria-label={uiText.filters.reset}>
              {uiText.filters.reset}
            </Button>
          </>
        }
      >
        <ControlGroup label={uiText.workflows.filterStatusLabel}>
          <Select
            aria-label={uiText.workflows.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              setDraftFilterState({
                key: filterDraftKey,
                value: {
                  status: event.target.value as WorkflowsFilterDraft['status'],
                },
              })
            }
            className="w-[180px]"
          >
            <option value="ALL">{uiText.workflows.filterStatusAll}</option>
            {WORKFLOW_STATUS_OPTIONS.map((status) => (
              <option key={status} value={status}>
                {formatWorkflowStatus(status)}
              </option>
            ))}
          </Select>
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.workflows.tableTitle}>
        {listQuery.isError ? (
          <ErrorState message={uiText.states.errorMessage} />
        ) : listQuery.isPending ? (
          <LoadingState />
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={rows}
              getRowKey={(row) => row.key}
              onRowClick={handleSelectRow}
              selectedRowKey={searchState.selectedKey ?? null}
              getRowAriaLabel={(row) => `${uiText.workflows.rowAriaLabelPrefix} ${row.key}`}
              emptyMessage={uiText.workflows.tableEmptyMessage}
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

      <WorkflowCreateModal
        open={isCreateOpen}
        onOpenChange={setCreateOpen}
        onSubmit={handleCreateWorkflow}
        isSubmitting={createWorkflowMutation.isPending}
      />
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

function buildInspectorBody(selectedKey: string | undefined, rows: WorkflowResponse[]) {
  if (!selectedKey) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflows.inspectorEmpty}</p>
  }

  const selected = rows.find((row) => row.key === selectedKey)
  if (!selected) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflows.inspectorEmpty}</p>
  }

  return (
    <div className="space-y-2 text-sm">
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflows.detailKeyLabel}: </span>
        <span className="font-mono">{selected.key}</span>
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflows.detailNameLabel}: </span>
        {selected.name}
      </p>
      <p>
        <span className="text-[var(--color-text-muted)]">{uiText.workflows.detailStatusLabel}: </span>
        {formatWorkflowStatus(selected.status)}
      </p>
    </div>
  )
}
