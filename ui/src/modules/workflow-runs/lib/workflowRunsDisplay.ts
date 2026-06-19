import type { StatusTone } from '@shared/ui/StatusBadge'
import type { WorkflowRunStatus, WorkflowRunStepStatus } from '@modules/workflows/lib/workflowSchemas'

const RUN_STATUS_TONES: Record<WorkflowRunStatus, StatusTone> = {
  PENDING: 'warning',
  BLOCKED: 'warning',
  DUPLICATE_IGNORED: 'info',
  CANCELED: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
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
  // Render the status enum literally in UPPERCASE per the design system
  // (e.g. DUPLICATE_IGNORED → "DUPLICATE IGNORED").
  return status ? status.replace(/_/g, ' ') : 'UNKNOWN'
}

export function getWorkflowRunStatusTone(status: WorkflowRunStatus | null): StatusTone {
  if (!status) {
    return 'info'
  }

  return RUN_STATUS_TONES[status]
}

export function formatWorkflowRunStepStatus(status: WorkflowRunStepStatus | null): string {
  // Render the step-status enum literally in UPPERCASE per the design system.
  return status ? status.replace(/_/g, ' ') : 'UNKNOWN'
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
