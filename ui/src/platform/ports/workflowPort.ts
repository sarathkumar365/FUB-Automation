import type {
  StepTypeCatalogEntry,
  TriggerTypeCatalogEntry,
  ValidateWorkflowResponse,
  WorkflowPageResponse,
  WorkflowResponse,
  WorkflowStatus,
  WorkflowVersionSummary,
} from '../../modules/workflows/lib/workflowSchemas'

export type WorkflowListFilters = {
  status?: WorkflowStatus
  page?: number
  size?: number
}

export type CreateWorkflowCommand = {
  key: string
  name: string
  description?: string
  trigger: Record<string, unknown>
  graph: Record<string, unknown>
  status: WorkflowStatus
}

export type UpdateWorkflowCommand = {
  name: string
  description?: string
  trigger: Record<string, unknown>
  graph: Record<string, unknown>
}

export type ValidateWorkflowCommand = {
  graph: Record<string, unknown>
  trigger: Record<string, unknown>
}

export type RollbackWorkflowCommand = {
  toVersion: number
}

export interface WorkflowPort {
  listWorkflows(filters: WorkflowListFilters): Promise<WorkflowPageResponse>
  getWorkflowByKey(key: string): Promise<WorkflowResponse>
  createWorkflow(cmd: CreateWorkflowCommand): Promise<WorkflowResponse>
  updateWorkflow(key: string, cmd: UpdateWorkflowCommand): Promise<WorkflowResponse>
  listWorkflowVersions(key: string): Promise<WorkflowVersionSummary[]>
  validateWorkflow(cmd: ValidateWorkflowCommand): Promise<ValidateWorkflowResponse>
  activateWorkflow(key: string): Promise<WorkflowResponse>
  deactivateWorkflow(key: string): Promise<WorkflowResponse>
  rollbackWorkflow(key: string, cmd: RollbackWorkflowCommand): Promise<WorkflowResponse>
  archiveWorkflow(key: string): Promise<WorkflowResponse>
  listStepTypes(): Promise<StepTypeCatalogEntry[]>
  listTriggerTypes(): Promise<TriggerTypeCatalogEntry[]>
}
