import type { PolicyExecutionRunStatus } from './policySchemas'

const RUN_STATUS_VALUES: PolicyExecutionRunStatus[] = [
  'PENDING',
  'BLOCKED_POLICY',
  'DUPLICATE_IGNORED',
  'COMPLETED',
  'FAILED',
]

export type PoliciesTab = 'runs' | 'manage'

export type PoliciesPageSearchState = {
  tab: PoliciesTab
  status?: PolicyExecutionRunStatus
  policyKey?: string
  from?: string
  to?: string
  cursor?: string
  selectedRun?: number
  selectedPolicy?: number
}

export type PoliciesFilterDraft = {
  status: PolicyExecutionRunStatus | 'ALL'
  policyKey: string
  from: string
  to: string
}

export function parsePoliciesSearchParams(searchParams: URLSearchParams): PoliciesPageSearchState {
  const tabRaw = searchParams.get('tab')
  const tab: PoliciesTab = tabRaw === 'manage' ? 'manage' : 'runs'

  const statusValue = searchParams.get('status') ?? undefined
  const selectedRunRaw = searchParams.get('selectedRun')
  const selectedRun = selectedRunRaw ? Number(selectedRunRaw) : undefined
  const selectedPolicyRaw = searchParams.get('selectedPolicy')
  const selectedPolicy = selectedPolicyRaw ? Number(selectedPolicyRaw) : undefined

  return {
    tab,
    status: isRunStatus(statusValue) ? statusValue : undefined,
    policyKey: normalizeString(searchParams.get('policyKey')),
    from: normalizeString(searchParams.get('from')),
    to: normalizeString(searchParams.get('to')),
    cursor: normalizeString(searchParams.get('cursor')),
    selectedRun: Number.isFinite(selectedRun) ? selectedRun : undefined,
    selectedPolicy: Number.isFinite(selectedPolicy) ? selectedPolicy : undefined,
  }
}

export function toPoliciesFilterDraft(state: PoliciesPageSearchState): PoliciesFilterDraft {
  return {
    status: state.status ?? 'ALL',
    policyKey: state.policyKey ?? '',
    from: state.from ?? '',
    to: state.to ?? '',
  }
}

export function createPoliciesSearchParams(state: PoliciesPageSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.tab !== 'runs') {
    params.set('tab', state.tab)
  }
  if (state.status) {
    params.set('status', state.status)
  }
  if (state.policyKey) {
    params.set('policyKey', state.policyKey)
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
  if (state.selectedRun !== undefined) {
    params.set('selectedRun', String(state.selectedRun))
  }
  if (state.selectedPolicy !== undefined) {
    params.set('selectedPolicy', String(state.selectedPolicy))
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

function isRunStatus(value: string | undefined): value is PolicyExecutionRunStatus {
  return value ? RUN_STATUS_VALUES.includes(value as PolicyExecutionRunStatus) : false
}
