export type ProcessedCallStatus = 'RECEIVED' | 'PROCESSING' | 'SKIPPED' | 'TASK_CREATED' | 'FAILED'

export type ProcessedCallFilters = {
  status?: ProcessedCallStatus
  from?: string
  to?: string
  limit?: number
}

export type ProcessedCallSummary = {
  callId: number
  status: ProcessedCallStatus
  ruleApplied?: string | null
  taskId?: number | null
  failureReason?: string | null
  retryCount: number
  updatedAt: string
}

export interface ProcessedCallsPort {
  listProcessedCalls(filters: ProcessedCallFilters): Promise<ProcessedCallSummary[]>
  replayProcessedCall(callId: number): Promise<{ message: string }>
}
