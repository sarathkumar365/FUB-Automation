import type { StatusTone } from '../../../shared/ui/StatusBadge'
import type { WorkflowRunStatus, WorkflowRunStepStatus } from '../../workflows/lib/workflowSchemas'

const RUN_STATUS_LABELS: Record<WorkflowRunStatus, string> = {
  PENDING: 'Pending',
  BLOCKED: 'Blocked',
  DUPLICATE_IGNORED: 'Duplicate Ignored',
  CANCELED: 'Canceled',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

const RUN_STATUS_TONES: Record<WorkflowRunStatus, StatusTone> = {
  PENDING: 'warning',
  BLOCKED: 'warning',
  DUPLICATE_IGNORED: 'info',
  CANCELED: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
}

const STEP_STATUS_LABELS: Record<WorkflowRunStepStatus, string> = {
  PENDING: 'Pending',
  WAITING_DEPENDENCY: 'Waiting Dependency',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
}

const STEP_STATUS_TONES: Record<WorkflowRunStepStatus, StatusTone> = {
  PENDING: 'warning',
  WAITING_DEPENDENCY: 'info',
  PROCESSING: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
  SKIPPED: 'info',
}

export function formatWorkflowRunStatus(status: WorkflowRunStatus | null): string {
  if (!status) {
    return 'Unknown'
  }

  return RUN_STATUS_LABELS[status]
}

export function getWorkflowRunStatusTone(status: WorkflowRunStatus | null): StatusTone {
  if (!status) {
    return 'info'
  }

  return RUN_STATUS_TONES[status]
}

export function formatWorkflowRunStepStatus(status: WorkflowRunStepStatus | null): string {
  if (!status) {
    return 'Unknown'
  }

  return STEP_STATUS_LABELS[status]
}

export function getWorkflowRunStepStatusTone(status: WorkflowRunStepStatus | null): StatusTone {
  if (!status) {
    return 'info'
  }

  return STEP_STATUS_TONES[status]
}

export function formatWorkflowRunReasonCode(reasonCode: string | null): string {
  if (!reasonCode) {
    return '-'
  }

  const normalized = reasonCode.trim()
  return normalized.length > 0 ? normalized : '-'
}

export function canCancelWorkflowRun(status: WorkflowRunStatus | null): boolean {
  return status === 'PENDING' || status === 'BLOCKED'
}
