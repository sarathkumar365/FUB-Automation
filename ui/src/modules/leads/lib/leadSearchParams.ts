import type { LeadStatus } from '../../../shared/types/lead'

const LEAD_STATUS_VALUES: LeadStatus[] = ['ACTIVE', 'ARCHIVED', 'MERGED']

export type LeadsPageSearchState = {
  sourceSystem?: string
  status?: LeadStatus
  sourceLeadIdPrefix?: string
  from?: string
  to?: string
  cursor?: string
}

export type LeadFilterDraft = {
  sourceSystem: string
  status: LeadStatus | 'ALL'
  sourceLeadIdPrefix: string
  from: string
  to: string
}

export function parseLeadsSearchParams(searchParams: URLSearchParams): LeadsPageSearchState {
  const statusValue = searchParams.get('status') ?? undefined
  return {
    sourceSystem: normalizeString(searchParams.get('sourceSystem')),
    status: isLeadStatus(statusValue) ? statusValue : undefined,
    sourceLeadIdPrefix: normalizeString(searchParams.get('sourceLeadIdPrefix')),
    from: normalizeString(searchParams.get('from')),
    to: normalizeString(searchParams.get('to')),
    cursor: normalizeString(searchParams.get('cursor')),
  }
}

export function toDraftFilters(state: LeadsPageSearchState): LeadFilterDraft {
  return {
    sourceSystem: state.sourceSystem ?? '',
    status: state.status ?? 'ALL',
    sourceLeadIdPrefix: state.sourceLeadIdPrefix ?? '',
    from: state.from ?? '',
    to: state.to ?? '',
  }
}

export function createSearchParamsFromState(state: LeadsPageSearchState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.sourceSystem) {
    params.set('sourceSystem', state.sourceSystem)
  }
  if (state.status) {
    params.set('status', state.status)
  }
  if (state.sourceLeadIdPrefix) {
    params.set('sourceLeadIdPrefix', state.sourceLeadIdPrefix)
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

function isLeadStatus(value: string | undefined): value is LeadStatus {
  return value !== undefined && (LEAD_STATUS_VALUES as string[]).includes(value)
}
