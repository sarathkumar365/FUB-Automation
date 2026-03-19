import type { ProcessedCallStatus } from '../../../platform/ports/processedCallsPort'

const PROCESSED_CALL_DATE_TIME_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
})

export function formatProcessedCallStatus(status: ProcessedCallStatus): string {
  return status
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}

export function getProcessedCallStatusTone(status: ProcessedCallStatus): 'success' | 'warning' | 'error' | 'info' {
  if (status === 'TASK_CREATED') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'error'
  }

  if (status === 'PROCESSING') {
    return 'warning'
  }

  return 'info'
}

export function formatProcessedCallDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return PROCESSED_CALL_DATE_TIME_FORMATTER.format(date).replace(/\b(AM|PM)\b/, (match) => match.toLowerCase())
}
