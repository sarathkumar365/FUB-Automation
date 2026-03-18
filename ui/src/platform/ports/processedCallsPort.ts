export type ProcessedCallFilters = {
  status?: 'RECEIVED' | 'PROCESSING' | 'SKIPPED' | 'TASK_CREATED' | 'FAILED'
  from?: string
  to?: string
  limit?: number
}

export type ProcessedCallSummary = {
  callId: number
  status: ProcessedCallFilters['status']
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
