import { useMemo, useState, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { Button } from '../../../shared/ui/button'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { DateInput } from '../../../shared/ui/DateInput'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { ApplyIcon, FilterIcon, NextIcon, ResetIcon } from '../../../shared/ui/icons'
import { Input } from '../../../shared/ui/input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { LeadFeedItem, LeadStatus } from '../../../shared/types/lead'
import { useLeadsQuery } from '../data/useLeadsQuery'
import { formatLeadName, leadStatusTone } from '../lib/leadDisplay'
import {
  createSearchParamsFromState,
  parseLeadsSearchParams,
  toDraftFilters,
  type LeadFilterDraft,
  type LeadsPageSearchState,
} from '../lib/leadSearchParams'

const LIMIT = 50

export function LeadsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const searchState = useMemo(() => parseLeadsSearchParams(searchParams), [searchParams])
  const filterDraftKey = useMemo(
    () =>
      [
        searchState.sourceSystem ?? '',
        searchState.status ?? '',
        searchState.sourceLeadIdPrefix ?? '',
        searchState.from ?? '',
        searchState.to ?? '',
      ].join('|'),
    [
      searchState.from,
      searchState.sourceLeadIdPrefix,
      searchState.sourceSystem,
      searchState.status,
      searchState.to,
    ],
  )
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: LeadFilterDraft }>(
    () => ({ key: filterDraftKey, value: toDraftFilters(searchState) }),
  )
  const draftFilters =
    draftFilterState.key === filterDraftKey ? draftFilterState.value : toDraftFilters(searchState)

  const listQuery = useLeadsQuery({
    sourceSystem: searchState.sourceSystem,
    status: searchState.status,
    sourceLeadIdPrefix: searchState.sourceLeadIdPrefix,
    from: searchState.from,
    to: searchState.to,
    limit: LIMIT,
    cursor: searchState.cursor,
  })

  const rows = listQuery.data?.items ?? []

  const columns = useMemo<ColumnDef<LeadFeedItem>[]>(
    () => [
      {
        key: 'sourceSystem',
        header: uiText.leads.tableSourceSystemHeader,
        render: (row) => row.sourceSystem,
      },
      {
        key: 'sourceLeadId',
        header: uiText.leads.tableSourceLeadIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.sourceLeadId}</span>,
      },
      {
        key: 'name',
        header: uiText.leads.tableNameHeader,
        render: (row) => formatLeadName(row.snapshot) ?? uiText.leads.nameUnknown,
      },
      {
        key: 'status',
        header: uiText.leads.tableStatusHeader,
        render: (row) => <StatusBadge label={row.status} tone={leadStatusTone(row.status)} />,
      },
      {
        key: 'updatedAt',
        header: uiText.leads.tableUpdatedAtHeader,
        render: (row) => formatDateTime(row.updatedAt),
      },
      {
        key: 'lastSyncedAt',
        header: uiText.leads.tableLastSyncedHeader,
        render: (row) => formatDateTime(row.lastSyncedAt),
      },
    ],
    [],
  )

  const handleDraftChange = <K extends keyof LeadFilterDraft>(key: K, value: LeadFilterDraft[K]) => {
    setDraftFilterState((existing) => ({
      key: filterDraftKey,
      value: {
        ...(existing.key === filterDraftKey ? existing.value : draftFilters),
        [key]: value,
      },
    }))
  }

  const handleApply = () => {
    const nextState: LeadsPageSearchState = {
      sourceSystem: draftFilters.sourceSystem.trim() || undefined,
      status: draftFilters.status === 'ALL' ? undefined : (draftFilters.status as LeadStatus),
      sourceLeadIdPrefix: draftFilters.sourceLeadIdPrefix.trim() || undefined,
      from: draftFilters.from || undefined,
      to: draftFilters.to || undefined,
      cursor: undefined,
    }
    setSearchParams(createSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilterState({
      key: filterDraftKey,
      value: {
        sourceSystem: '',
        status: 'ALL',
        sourceLeadIdPrefix: '',
        from: '',
        to: '',
      },
    })
    setSearchParams(new URLSearchParams())
  }

  const handleNext = () => {
    const nextCursor = listQuery.data?.nextCursor
    if (!nextCursor) {
      return
    }
    setSearchParams(createSearchParamsFromState({ ...searchState, cursor: nextCursor }))
  }

  const handleSelectRow = (row: LeadFeedItem) => {
    navigate(routes.leadDetail(row.sourceLeadId))
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.leads.title} subtitle={uiText.leads.subtitle} />

      <FilterBar
        actions={
          <>
            <Button type="button" size="sm" onClick={handleApply} aria-label={uiText.filters.apply}>
              <ApplyIcon className="mr-1.5" />
              {uiText.filters.apply}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={handleReset}
              aria-label={uiText.filters.reset}
            >
              <ResetIcon className="mr-1.5" />
              {uiText.filters.reset}
            </Button>
          </>
        }
      >
        <FilterIcon data-testid="leads-filter-icon" className="text-[var(--color-text-muted)]" />

        <ControlGroup label={uiText.leads.filterSourceSystemLabel}>
          <Input
            aria-label={uiText.leads.filterSourceSystemLabel}
            placeholder={uiText.leads.filterSourceSystemAll}
            value={draftFilters.sourceSystem}
            onChange={(event) => handleDraftChange('sourceSystem', event.target.value)}
            className="w-[138px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.leads.filterStatusLabel}>
          <Select
            aria-label={uiText.leads.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              handleDraftChange('status', event.target.value as LeadFilterDraft['status'])
            }
            className="w-[138px]"
          >
            <option value="ALL">{uiText.leads.filterStatusAll}</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
            <option value="MERGED">MERGED</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.leads.filterSourceLeadIdPrefixLabel}>
          <Input
            aria-label={uiText.leads.filterSourceLeadIdPrefixLabel}
            placeholder={uiText.leads.filterSourceLeadIdPrefixPlaceholder}
            value={draftFilters.sourceLeadIdPrefix}
            onChange={(event) => handleDraftChange('sourceLeadIdPrefix', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.leads.filterFromLabel}>
          <DateInput
            aria-label={uiText.leads.filterFromLabel}
            value={draftFilters.from}
            onChange={(event) => handleDraftChange('from', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.leads.filterToLabel}>
          <DateInput
            aria-label={uiText.leads.filterToLabel}
            value={draftFilters.to}
            onChange={(event) => handleDraftChange('to', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.leads.tableTitle}>
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
              getRowAriaLabel={(row) => `${uiText.leads.rowAriaLabelPrefix} ${row.sourceLeadId}`}
              onRowClick={handleSelectRow}
              emptyMessage={uiText.leads.tableEmptyMessage}
            />
            <div className="mt-3 flex items-center justify-end gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={handleNext}
                disabled={!listQuery.data?.nextCursor}
                aria-label={uiText.leads.paginationNextAria}
              >
                <NextIcon className="mr-1.5" />
                {listQuery.data?.nextCursor ? uiText.leads.paginationNext : uiText.leads.paginationNoMore}
              </Button>
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
