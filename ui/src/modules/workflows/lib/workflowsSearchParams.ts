import type { WorkflowRunStatus, WorkflowStatus } from './workflowSchemas'

const STATUS_VALUES: WorkflowStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED']
const RUN_STATUS_VALUES: WorkflowRunStatus[] = ['PENDING', 'BLOCKED', 'DUPLICATE_IGNORED', 'CANCELED', 'COMPLETED', 'FAILED']
export const DEFAULT_WORKFLOW_PAGE = 0
export const DEFAULT_WORKFLOW_SIZE = 20
export const DEFAULT_WORKFLOW_DETAIL_RUNS_PAGE = 0
export const DEFAULT_WORKFLOW_DETAIL_RUNS_SIZE = 20

export type WorkflowsPageSearchState = {
  status?: WorkflowStatus
  page: number
  size: number
  selectedKey?: string
}

export type WorkflowsFilterDraft = {
  status: WorkflowStatus | 'ALL'
}

export type WorkflowDetailTab = 'definition' | 'runs'

export type WorkflowDetailRunsFilterDraft = {
  status: WorkflowRunStatus | 'ALL'
}

export type WorkflowDetailSearchState = {
  tab: WorkflowDetailTab
  runStatus?: WorkflowRunStatus
  runPage: number
  runSize: number
}

export function parseWorkflowsSearchParams(searchParams: URLSearchParams): WorkflowsPageSearchState {
  const statusValue = searchParams.get('status') ?? undefined
  const pageRaw = searchParams.get('page')
  const sizeRaw = searchParams.get('size')

  return {
    status: isWorkflowStatus(statusValue) ? statusValue : undefined,
    page: normalizeNonNegativeInt(pageRaw, DEFAULT_WORKFLOW_PAGE),
    size: normalizePositiveInt(sizeRaw, DEFAULT_WORKFLOW_SIZE),
    selectedKey: normalizeString(searchParams.get('selectedKey')),
  }
}

export function toWorkflowsDraftFilters(state: WorkflowsPageSearchState): WorkflowsFilterDraft {
  return {
    status: state.status ?? 'ALL',
  }
}

export function createWorkflowsSearchParamsFromState(state: WorkflowsPageSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.status) {
    params.set('status', state.status)
  }
  if (state.page !== DEFAULT_WORKFLOW_PAGE) {
    params.set('page', String(state.page))
  }
  if (state.size !== DEFAULT_WORKFLOW_SIZE) {
    params.set('size', String(state.size))
  }
  if (state.selectedKey) {
    params.set('selectedKey', state.selectedKey)
  }

  return params
}

export function parseWorkflowDetailSearchParams(searchParams: URLSearchParams): WorkflowDetailSearchState {
  const tabValue = searchParams.get('tab')
  const runStatusValue = searchParams.get('runStatus') ?? undefined
  const runPageRaw = searchParams.get('runPage')
  const runSizeRaw = searchParams.get('runSize')

  return {
    tab: tabValue === 'runs' ? 'runs' : 'definition',
    runStatus: isWorkflowRunStatus(runStatusValue) ? runStatusValue : undefined,
    runPage: normalizeNonNegativeInt(runPageRaw, DEFAULT_WORKFLOW_DETAIL_RUNS_PAGE),
    runSize: normalizePositiveInt(runSizeRaw, DEFAULT_WORKFLOW_DETAIL_RUNS_SIZE),
  }
}

export function createWorkflowDetailSearchParamsFromState(state: WorkflowDetailSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.tab !== 'definition') {
    params.set('tab', state.tab)
  }
  if (state.runStatus) {
    params.set('runStatus', state.runStatus)
  }
  if (state.runPage !== DEFAULT_WORKFLOW_DETAIL_RUNS_PAGE) {
    params.set('runPage', String(state.runPage))
  }
  if (state.runSize !== DEFAULT_WORKFLOW_DETAIL_RUNS_SIZE) {
    params.set('runSize', String(state.runSize))
  }

  return params
}

export function toWorkflowDetailRunsDraftFilters(state: WorkflowDetailSearchState): WorkflowDetailRunsFilterDraft {
  return {
    status: state.runStatus ?? 'ALL',
  }
}

function normalizeString(value: string | null): string | undefined {
  if (!value) {
    return undefined
  }

  const normalized = value.trim()
  return normalized.length > 0 ? normalized : undefined
}

function normalizeNonNegativeInt(value: string | null, fallback: number): number {
  if (!value) {
    return fallback
  }

  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.max(0, parsed)
}

function normalizePositiveInt(value: string | null, fallback: number): number {
  if (!value) {
    return fallback
  }

  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.max(1, parsed)
}

function isWorkflowStatus(value: string | undefined): value is WorkflowStatus {
  return value ? STATUS_VALUES.includes(value as WorkflowStatus) : false
}

function isWorkflowRunStatus(value: string | undefined): value is WorkflowRunStatus {
  return value ? RUN_STATUS_VALUES.includes(value as WorkflowRunStatus) : false
}
