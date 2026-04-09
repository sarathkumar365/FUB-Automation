import type {
  ActivatePolicyCommand,
  CreatePolicyCommand,
  PolicyListFilters,
  PolicyPort,
  UpdatePolicyCommand,
} from '../../ports/policyPort'
import { policyListSchema, policyResponseSchema } from '../../../modules/policies/lib/policySchemas'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'

export class HttpPolicyAdapter implements PolicyPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listPolicies(filters: PolicyListFilters) {
    const query = toQueryString({
      domain: filters.domain,
      policyKey: filters.policyKey,
    })
    return this.httpClient.get(`/admin/policies${query}`, policyListSchema)
  }

  getActivePolicy(domain: string, policyKey: string) {
    return this.httpClient.get(`/admin/policies/${domain}/${policyKey}/active`, policyResponseSchema)
  }

  createPolicy(cmd: CreatePolicyCommand) {
    return this.httpClient.post('/admin/policies', policyResponseSchema, cmd)
  }

  updatePolicy(id: number, cmd: UpdatePolicyCommand) {
    return this.httpClient.put(`/admin/policies/${id}`, policyResponseSchema, cmd)
  }

  activatePolicy(id: number, cmd: ActivatePolicyCommand) {
    return this.httpClient.post(`/admin/policies/${id}/activate`, policyResponseSchema, cmd)
  }
}
