import type { PersonStatus } from '../../../shared/types/person'

const PERSON_STATUS_VALUES: PersonStatus[] = ['ACTIVE', 'ARCHIVED', 'MERGED']

export type PersonsPageSearchState = {
  sourceSystem?: string
  status?: PersonStatus
  sourcePersonIdPrefix?: string
  from?: string
  to?: string
  cursor?: string
}

export type PersonFilterDraft = {
  sourceSystem: string
  status: PersonStatus | 'ALL'
  sourcePersonIdPrefix: string
  from: string
  to: string
}

export function parsePersonsSearchParams(searchParams: URLSearchParams): PersonsPageSearchState {
  const statusValue = searchParams.get('status') ?? undefined
  return {
    sourceSystem: normalizeString(searchParams.get('sourceSystem')),
    status: isPersonStatus(statusValue) ? statusValue : undefined,
    sourcePersonIdPrefix: normalizeString(searchParams.get('sourcePersonIdPrefix')),
    from: normalizeString(searchParams.get('from')),
    to: normalizeString(searchParams.get('to')),
    cursor: normalizeString(searchParams.get('cursor')),
  }
}

export function toDraftFilters(state: PersonsPageSearchState): PersonFilterDraft {
  return {
    sourceSystem: state.sourceSystem ?? '',
    status: state.status ?? 'ALL',
    sourcePersonIdPrefix: state.sourcePersonIdPrefix ?? '',
    from: state.from ?? '',
    to: state.to ?? '',
  }
}

export function createSearchParamsFromState(state: PersonsPageSearchState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.sourceSystem) {
    params.set('sourceSystem', state.sourceSystem)
  }
  if (state.status) {
    params.set('status', state.status)
  }
  if (state.sourcePersonIdPrefix) {
    params.set('sourcePersonIdPrefix', state.sourcePersonIdPrefix)
  }
  if (state.from) {
    params.set('from', state.from)
  }
  if (state.to) {
    params.set('to', state.to)
  }
  if (state.cursor) {
    params.set('cursor', state.cursor)
  }
  return params
}

function normalizeString(value: string | null): string | undefined {
  if (!value) {
    return undefined
  }
  const trimmed = value.trim()
  return trimmed.length === 0 ? undefined : trimmed
}

function isPersonStatus(value: string | undefined): value is PersonStatus {
  return value !== undefined && (PERSON_STATUS_VALUES as string[]).includes(value)
}
