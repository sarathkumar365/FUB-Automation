import {
  workflowRunDetailResponseSchema,
  workflowRunPageResponseSchema,
} from '../../../modules/workflows/lib/workflowSchemas'
import type { WorkflowRunListFilters, WorkflowRunPort } from '../../ports/workflowRunPort'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'

export class HttpWorkflowRunAdapter implements WorkflowRunPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listWorkflowRuns(filters: WorkflowRunListFilters) {
    const query = toQueryString({
      status: filters.status,
      page: filters.page,
      size: filters.size,
    })

    return this.httpClient.get(`/admin/workflow-runs${query}`, workflowRunPageResponseSchema)
  }

  listWorkflowRunsForKey(key: string, filters: WorkflowRunListFilters) {
    const query = toQueryString({
      status: filters.status,
      page: filters.page,
      size: filters.size,
    })

    return this.httpClient.get(`/admin/workflows/${key}/runs${query}`, workflowRunPageResponseSchema)
  }

  getWorkflowRunDetail(runId: number) {
    return this.httpClient.get(`/admin/workflow-runs/${runId}`, workflowRunDetailResponseSchema)
  }

  cancelWorkflowRun(runId: number) {
    return this.httpClient.post(`/admin/workflow-runs/${runId}/cancel`, workflowRunDetailResponseSchema)
  }
}
