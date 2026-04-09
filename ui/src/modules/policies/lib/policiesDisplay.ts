import type { StatusTone } from '../../../shared/ui/StatusBadge'
import type {
  PolicyExecutionRunStatus,
  PolicyExecutionStepStatus,
  PolicyStatus,
  PolicyStepType,
} from './policySchemas'

// --- Run status ---

export function runStatusTone(status: PolicyExecutionRunStatus): StatusTone {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'error'
    case 'BLOCKED_POLICY':
      return 'warning'
    case 'PENDING':
    case 'DUPLICATE_IGNORED':
      return 'info'
  }
}

export function runStatusLabel(status: PolicyExecutionRunStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Pending'
    case 'BLOCKED_POLICY':
      return 'Blocked'
    case 'DUPLICATE_IGNORED':
      return 'Duplicate'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
  }
}

// --- Step status ---

export function stepStatusTone(status: PolicyExecutionStepStatus): StatusTone {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'error'
    case 'PROCESSING':
      return 'warning'
    case 'PENDING':
    case 'WAITING_DEPENDENCY':
    case 'SKIPPED':
      return 'info'
  }
}

export function stepStatusLabel(status: PolicyExecutionStepStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Pending'
    case 'WAITING_DEPENDENCY':
      return 'Waiting'
    case 'PROCESSING':
      return 'Processing'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    case 'SKIPPED':
      return 'Skipped'
  }
}

// --- Step type ---

export function stepTypeLabel(type: PolicyStepType): string {
  switch (type) {
    case 'WAIT_AND_CHECK_CLAIM':
      return 'Check Claim'
    case 'WAIT_AND_CHECK_COMMUNICATION':
      return 'Check Communication'
    case 'ON_FAILURE_EXECUTE_ACTION':
      return 'Execute Action'
  }
}

// --- Policy status ---

export function policyStatusTone(status: PolicyStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'INACTIVE':
      return 'info'
  }
}

export function policyStatusLabel(status: PolicyStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active'
    case 'INACTIVE':
      return 'Inactive'
  }
}

// --- Action type ---

export function actionTypeLabel(actionType: string): string {
  switch (actionType) {
    case 'REASSIGN':
      return 'Reassign to User'
    case 'MOVE_TO_POND':
      return 'Move to Pond'
    default:
      return actionType
  }
}

// --- Relative time ---

export function formatRelativeTime(dateStr: string): string {
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  if (Number.isNaN(then)) {
    return dateStr
  }

  const diffMs = now - then
  if (diffMs < 0) {
    return 'just now'
  }

  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) {
    return `${diffSec}s ago`
  }

  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) {
    return `${diffMin}m ago`
  }

  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) {
    return `${diffHr}h ago`
  }

  const diffDay = Math.floor(diffHr / 24)
  return `${diffDay}d ago`
}

// --- Policy key display ---

export function formatPolicyLabel(policyKey: string, version: number): string {
  const shortKey = policyKey.length > 16 ? policyKey.slice(0, 14) + '..' : policyKey
  return `${shortKey} v${version}`
}
