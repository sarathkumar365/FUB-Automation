import type { PolicyExecutionListFilters, PolicyExecutionPort } from '../../ports/policyExecutionPort'
import {
  policyExecutionRunDetailSchema,
  policyExecutionRunPageSchema,
} from '../../../modules/policies/lib/policySchemas'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'

export class HttpPolicyExecutionAdapter implements PolicyExecutionPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listExecutions(filters: PolicyExecutionListFilters) {
    const query = toQueryString({
      status: filters.status,
      policyKey: filters.policyKey,
      from: filters.from ? `${filters.from}T00:00:00Z` : undefined,
      to: filters.to ? `${filters.to}T23:59:59Z` : undefined,
      limit: filters.limit,
      cursor: filters.cursor,
    })
    return this.httpClient.get(`/admin/policy-executions${query}`, policyExecutionRunPageSchema)
  }

  getExecutionDetail(id: number) {
    return this.httpClient.get(`/admin/policy-executions/${id}`, policyExecutionRunDetailSchema)
  }
}
