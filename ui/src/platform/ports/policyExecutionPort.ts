import type {
  PolicyExecutionRunDetail,
  PolicyExecutionRunPage,
  PolicyExecutionRunStatus,
} from '../../modules/policies/lib/policySchemas'

export type PolicyExecutionListFilters = {
  status?: PolicyExecutionRunStatus
  policyKey?: string
  from?: string
  to?: string
  limit?: number
  cursor?: string
}

export interface PolicyExecutionPort {
  listExecutions(filters: PolicyExecutionListFilters): Promise<PolicyExecutionRunPage>
  getExecutionDetail(id: number): Promise<PolicyExecutionRunDetail>
}
