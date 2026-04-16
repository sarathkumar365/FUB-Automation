import type { WorkflowStatus } from './workflowSchemas'
import type { StatusTone } from '../../../shared/ui/StatusBadge'

const STATUS_LABELS: Record<WorkflowStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  ARCHIVED: 'Archived',
}

const STATUS_TONES: Record<WorkflowStatus, StatusTone> = {
  DRAFT: 'warning',
  ACTIVE: 'success',
  INACTIVE: 'info',
  ARCHIVED: 'error',
}

const EDITABLE_STATUSES: WorkflowStatus[] = ['DRAFT', 'INACTIVE']
const ACTIVATABLE_STATUSES: WorkflowStatus[] = ['DRAFT', 'INACTIVE']
const DEACTIVATABLE_STATUSES: WorkflowStatus[] = ['ACTIVE']
const ARCHIVABLE_STATUSES: WorkflowStatus[] = ['DRAFT', 'INACTIVE']
const VALIDATABLE_STATUSES: WorkflowStatus[] = ['DRAFT', 'INACTIVE', 'ACTIVE']

export function formatWorkflowStatus(status: WorkflowStatus | null): string {
  if (!status) {
    return 'Unknown'
  }

  return STATUS_LABELS[status]
}

export function getWorkflowStatusTone(status: WorkflowStatus | null): StatusTone {
  if (!status) {
    return 'info'
  }

  return STATUS_TONES[status]
}

export function canEditWorkflow(status: WorkflowStatus | null): boolean {
  return status ? EDITABLE_STATUSES.includes(status) : false
}

export function canActivateWorkflow(status: WorkflowStatus | null): boolean {
  return status ? ACTIVATABLE_STATUSES.includes(status) : false
}

export function canDeactivateWorkflow(status: WorkflowStatus | null): boolean {
  return status ? DEACTIVATABLE_STATUSES.includes(status) : false
}

export function canArchiveWorkflow(status: WorkflowStatus | null): boolean {
  return status ? ARCHIVABLE_STATUSES.includes(status) : false
}

export function canValidateWorkflow(status: WorkflowStatus | null): boolean {
  return status ? VALIDATABLE_STATUSES.includes(status) : false
}
