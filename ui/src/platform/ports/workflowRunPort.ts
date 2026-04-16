import type {
  WorkflowRunDetailResponse,
  WorkflowRunPageResponse,
  WorkflowRunStatus,
} from '../../modules/workflows/lib/workflowSchemas'

export type WorkflowRunListFilters = {
  status?: WorkflowRunStatus
  page?: number
  size?: number
}

export interface WorkflowRunPort {
  listWorkflowRuns(filters: WorkflowRunListFilters): Promise<WorkflowRunPageResponse>
  listWorkflowRunsForKey(key: string, filters: WorkflowRunListFilters): Promise<WorkflowRunPageResponse>
  getWorkflowRunDetail(runId: number): Promise<WorkflowRunDetailResponse>
  cancelWorkflowRun(runId: number): Promise<WorkflowRunDetailResponse>
}
