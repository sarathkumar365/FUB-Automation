import type { PolicyResponse } from '../../modules/policies/lib/policySchemas'

export type PolicyListFilters = {
  domain?: string
  policyKey?: string
}

export type CreatePolicyCommand = {
  domain: string
  policyKey: string
  enabled: boolean
  blueprint: Record<string, unknown>
}

export type UpdatePolicyCommand = {
  enabled: boolean
  expectedVersion: number
  blueprint: Record<string, unknown>
}

export type ActivatePolicyCommand = {
  expectedVersion: number
}

export interface PolicyPort {
  listPolicies(filters: PolicyListFilters): Promise<PolicyResponse[]>
  getActivePolicy(domain: string, policyKey: string): Promise<PolicyResponse>
  createPolicy(cmd: CreatePolicyCommand): Promise<PolicyResponse>
  updatePolicy(id: number, cmd: UpdatePolicyCommand): Promise<PolicyResponse>
  activatePolicy(id: number, cmd: ActivatePolicyCommand): Promise<PolicyResponse>
}
