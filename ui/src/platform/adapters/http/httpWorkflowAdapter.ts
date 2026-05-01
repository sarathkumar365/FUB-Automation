import {
  stepTypeCatalogEntrySchema,
  triggerTypeCatalogEntrySchema,
  validateWorkflowResponseSchema,
  workflowPageResponseSchema,
  workflowResponseSchema,
  workflowVersionSummarySchema,
} from '../../../modules/workflows/lib/workflowSchemas'
import type {
  CreateWorkflowCommand,
  RollbackWorkflowCommand,
  UpdateWorkflowCommand,
  ValidateWorkflowCommand,
  WorkflowListFilters,
  WorkflowPort,
} from '../../ports/workflowPort'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'
import { z } from 'zod'

export class HttpWorkflowAdapter implements WorkflowPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listWorkflows(filters: WorkflowListFilters) {
    const query = toQueryString({
      status: filters.status,
      page: filters.page,
      size: filters.size,
    })

    return this.httpClient.get(`/admin/workflows${query}`, workflowPageResponseSchema)
  }

  getWorkflowByKey(key: string) {
    return this.httpClient.get(`/admin/workflows/${key}`, workflowResponseSchema)
  }

  createWorkflow(cmd: CreateWorkflowCommand) {
    return this.httpClient.post('/admin/workflows', workflowResponseSchema, cmd)
  }

  updateWorkflow(key: string, cmd: UpdateWorkflowCommand) {
    return this.httpClient.put(`/admin/workflows/${key}`, workflowResponseSchema, cmd)
  }

  listWorkflowVersions(key: string) {
    return this.httpClient.get(`/admin/workflows/${key}/versions`, z.array(workflowVersionSummarySchema))
  }

  validateWorkflow(cmd: ValidateWorkflowCommand) {
    return this.httpClient.post('/admin/workflows/validate', validateWorkflowResponseSchema, cmd)
  }

  activateWorkflow(key: string) {
    return this.httpClient.post(`/admin/workflows/${key}/activate`, workflowResponseSchema)
  }

  deactivateWorkflow(key: string) {
    return this.httpClient.post(`/admin/workflows/${key}/deactivate`, workflowResponseSchema)
  }

  rollbackWorkflow(key: string, cmd: RollbackWorkflowCommand) {
    return this.httpClient.post(`/admin/workflows/${key}/rollback`, workflowResponseSchema, cmd)
  }

  archiveWorkflow(key: string) {
    return this.httpClient.delete(`/admin/workflows/${key}`, workflowResponseSchema)
  }

  listStepTypes() {
    return this.httpClient.get('/admin/workflows/step-types', z.array(stepTypeCatalogEntrySchema))
  }

  listTriggerTypes() {
    return this.httpClient.get('/admin/workflows/trigger-types', z.array(triggerTypeCatalogEntrySchema))
  }
}
