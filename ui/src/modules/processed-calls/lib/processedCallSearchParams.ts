import type { ProcessedCallStatus } from '../../../platform/ports/processedCallsPort'

const STATUS_VALUES: ProcessedCallStatus[] = ['RECEIVED', 'PROCESSING', 'SKIPPED', 'TASK_CREATED', 'FAILED']

export type ProcessedCallsPageSearchState = {
  status?: ProcessedCallStatus
  from?: string
  to?: string
  selectedCallId?: number
}

export type ProcessedCallsFilterDraft = {
  status: ProcessedCallStatus | 'ALL'
  from: string
  to: string
}

export function parseProcessedCallsSearchParams(searchParams: URLSearchParams): ProcessedCallsPageSearchState {
  const statusValue = searchParams.get('status') ?? undefined
  const selectedCallIdRaw = searchParams.get('selectedCallId')
  const selectedCallId = selectedCallIdRaw ? Number(selectedCallIdRaw) : undefined

  return {
    status: isProcessedCallStatus(statusValue) ? statusValue : undefined,
    from: normalizeString(searchParams.get('from')),
    to: normalizeString(searchParams.get('to')),
    selectedCallId: Number.isFinite(selectedCallId) ? selectedCallId : undefined,
  }
}

export function toProcessedCallsDraftFilters(state: ProcessedCallsPageSearchState): ProcessedCallsFilterDraft {
  return {
    status: state.status ?? 'ALL',
    from: state.from ?? '',
    to: state.to ?? '',
  }
}

export function createProcessedCallsSearchParamsFromState(state: ProcessedCallsPageSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.status) {
    params.set('status', state.status)
  }
  if (state.from) {
    params.set('from', state.from)
  }
  if (state.to) {
    params.set('to', state.to)
  }
  if (state.selectedCallId !== undefined) {
    params.set('selectedCallId', String(state.selectedCallId))
  }

  return params
}

function normalizeString(value: string | null): string | undefined {
  if (!value) {
    return undefined
  }

  const normalized = value.trim()
  return normalized.length > 0 ? normalized : undefined
}

function isProcessedCallStatus(value: string | undefined): value is ProcessedCallStatus {
  return value ? STATUS_VALUES.includes(value as ProcessedCallStatus) : false
}
