import type { WorkflowRunStatus } from '../../workflows/lib/workflowSchemas'

const RUN_STATUS_VALUES: WorkflowRunStatus[] = ['PENDING', 'BLOCKED', 'DUPLICATE_IGNORED', 'CANCELED', 'COMPLETED', 'FAILED']
export const DEFAULT_WORKFLOW_RUNS_PAGE = 0
export const DEFAULT_WORKFLOW_RUNS_SIZE = 20

export type WorkflowRunsPageSearchState = {
  status?: WorkflowRunStatus
  page: number
  size: number
  selectedRunId?: number
}

export type WorkflowRunsFilterDraft = {
  status: WorkflowRunStatus | 'ALL'
}

export function parseWorkflowRunsSearchParams(searchParams: URLSearchParams): WorkflowRunsPageSearchState {
  const statusValue = searchParams.get('status') ?? undefined
  const pageRaw = searchParams.get('page')
  const sizeRaw = searchParams.get('size')
  const selectedRunIdRaw = searchParams.get('selectedRunId')

  return {
    status: isWorkflowRunStatus(statusValue) ? statusValue : undefined,
    page: normalizeNonNegativeInt(pageRaw, DEFAULT_WORKFLOW_RUNS_PAGE),
    size: normalizePositiveInt(sizeRaw, DEFAULT_WORKFLOW_RUNS_SIZE),
    selectedRunId: normalizePositiveIntOrUndefined(selectedRunIdRaw),
  }
}

export function toWorkflowRunsDraftFilters(state: WorkflowRunsPageSearchState): WorkflowRunsFilterDraft {
  return {
    status: state.status ?? 'ALL',
  }
}

export function createWorkflowRunsSearchParamsFromState(state: WorkflowRunsPageSearchState): URLSearchParams {
  const params = new URLSearchParams()

  if (state.status) {
    params.set('status', state.status)
  }
  if (state.page !== DEFAULT_WORKFLOW_RUNS_PAGE) {
    params.set('page', String(state.page))
  }
  if (state.size !== DEFAULT_WORKFLOW_RUNS_SIZE) {
    params.set('size', String(state.size))
  }
  if (state.selectedRunId) {
    params.set('selectedRunId', String(state.selectedRunId))
  }

  return params
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

function normalizePositiveIntOrUndefined(value: string | null): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed)) {
    return undefined
  }
  return parsed > 0 ? parsed : undefined
}

function isWorkflowRunStatus(value: string | undefined): value is WorkflowRunStatus {
  return value ? RUN_STATUS_VALUES.includes(value as WorkflowRunStatus) : false
}
