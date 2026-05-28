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
import type { PersonFeedItem, PersonStatus } from '../../../shared/types/person'
import { usePersonsQuery } from '../data/usePersonsQuery'
import { formatPersonName, personStatusTone } from '../lib/personDisplay'
import {
  createSearchParamsFromState,
  parsePersonsSearchParams,
  toDraftFilters,
  type PersonFilterDraft,
  type PersonsPageSearchState,
} from '../lib/personSearchParams'

const LIMIT = 50

export function PersonsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const searchState = useMemo(() => parsePersonsSearchParams(searchParams), [searchParams])
  const filterDraftKey = useMemo(
    () =>
      [
        searchState.sourceSystem ?? '',
        searchState.status ?? '',
        searchState.sourcePersonIdPrefix ?? '',
        searchState.from ?? '',
        searchState.to ?? '',
      ].join('|'),
    [
      searchState.from,
      searchState.sourcePersonIdPrefix,
      searchState.sourceSystem,
      searchState.status,
      searchState.to,
    ],
  )
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: PersonFilterDraft }>(
    () => ({ key: filterDraftKey, value: toDraftFilters(searchState) }),
  )
  const draftFilters =
    draftFilterState.key === filterDraftKey ? draftFilterState.value : toDraftFilters(searchState)

  const listQuery = usePersonsQuery({
    sourceSystem: searchState.sourceSystem,
    status: searchState.status,
    sourcePersonIdPrefix: searchState.sourcePersonIdPrefix,
    from: searchState.from,
    to: searchState.to,
    limit: LIMIT,
    cursor: searchState.cursor,
  })

  const rows = listQuery.data?.items ?? []

  const columns = useMemo<ColumnDef<PersonFeedItem>[]>(
    () => [
      {
        key: 'sourceSystem',
        header: uiText.persons.tableSourceSystemHeader,
        render: (row) => row.sourceSystem,
      },
      {
        key: 'sourcePersonId',
        header: uiText.persons.tableSourcePersonIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.sourcePersonId}</span>,
      },
      {
        key: 'name',
        header: uiText.persons.tableNameHeader,
        render: (row) => formatPersonName(row.snapshot) ?? uiText.persons.nameUnknown,
      },
      {
        key: 'status',
        header: uiText.persons.tableStatusHeader,
        render: (row) => <StatusBadge label={row.status} tone={personStatusTone(row.status)} />,
      },
      {
        key: 'updatedAt',
        header: uiText.persons.tableUpdatedAtHeader,
        render: (row) => formatDateTime(row.updatedAt),
      },
      {
        key: 'lastSyncedAt',
        header: uiText.persons.tableLastSyncedHeader,
        render: (row) => formatDateTime(row.lastSyncedAt),
      },
    ],
    [],
  )

  const handleDraftChange = <K extends keyof PersonFilterDraft>(key: K, value: PersonFilterDraft[K]) => {
    setDraftFilterState((existing) => ({
      key: filterDraftKey,
      value: {
        ...(existing.key === filterDraftKey ? existing.value : draftFilters),
        [key]: value,
      },
    }))
  }

  const handleApply = () => {
    const nextState: PersonsPageSearchState = {
      sourceSystem: draftFilters.sourceSystem.trim() || undefined,
      status: draftFilters.status === 'ALL' ? undefined : (draftFilters.status as PersonStatus),
      sourcePersonIdPrefix: draftFilters.sourcePersonIdPrefix.trim() || undefined,
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
        sourcePersonIdPrefix: '',
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

  const handleSelectRow = (row: PersonFeedItem) => {
    navigate(routes.personDetail(row.sourcePersonId))
  }

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.persons.title} subtitle={uiText.persons.subtitle} />

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
        <FilterIcon data-testid="persons-filter-icon" className="text-[var(--color-text-muted)]" />

        <ControlGroup label={uiText.persons.filterSourceSystemLabel}>
          <Input
            aria-label={uiText.persons.filterSourceSystemLabel}
            placeholder={uiText.persons.filterSourceSystemAll}
            value={draftFilters.sourceSystem}
            onChange={(event) => handleDraftChange('sourceSystem', event.target.value)}
            className="w-[138px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.persons.filterStatusLabel}>
          <Select
            aria-label={uiText.persons.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              handleDraftChange('status', event.target.value as PersonFilterDraft['status'])
            }
            className="w-[138px]"
          >
            <option value="ALL">{uiText.persons.filterStatusAll}</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
            <option value="MERGED">MERGED</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.persons.filterSourcePersonIdPrefixLabel}>
          <Input
            aria-label={uiText.persons.filterSourcePersonIdPrefixLabel}
            placeholder={uiText.persons.filterSourcePersonIdPrefixPlaceholder}
            value={draftFilters.sourcePersonIdPrefix}
            onChange={(event) => handleDraftChange('sourcePersonIdPrefix', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.persons.filterFromLabel}>
          <DateInput
            aria-label={uiText.persons.filterFromLabel}
            value={draftFilters.from}
            onChange={(event) => handleDraftChange('from', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.persons.filterToLabel}>
          <DateInput
            aria-label={uiText.persons.filterToLabel}
            value={draftFilters.to}
            onChange={(event) => handleDraftChange('to', event.target.value)}
            className="w-[170px]"
          />
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.persons.tableTitle}>
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
              getRowAriaLabel={(row) => `${uiText.persons.rowAriaLabelPrefix} ${row.sourcePersonId}`}
              onRowClick={handleSelectRow}
              emptyMessage={uiText.persons.tableEmptyMessage}
            />
            <div className="mt-3 flex items-center justify-end gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={handleNext}
                disabled={!listQuery.data?.nextCursor}
                aria-label={uiText.persons.paginationNextAria}
              >
                <NextIcon className="mr-1.5" />
                {listQuery.data?.nextCursor ? uiText.persons.paginationNext : uiText.persons.paginationNoMore}
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
