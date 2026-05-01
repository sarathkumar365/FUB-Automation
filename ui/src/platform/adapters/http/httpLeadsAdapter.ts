import type { LeadsPort } from '../../ports/leadsPort'
import type { LeadFeedPage, LeadListFilters, LeadSummary, LeadSummaryFilters } from '../../../shared/types/lead'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'
import { leadFeedPageSchema, leadSummarySchema } from './leadSchemas'

export class HttpLeadsAdapter implements LeadsPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listLeads(filters: LeadListFilters): Promise<LeadFeedPage> {
    const query = toQueryString({
      sourceSystem: filters.sourceSystem,
      status: filters.status,
      sourceLeadIdPrefix: filters.sourceLeadIdPrefix,
      from: filters.from,
      to: filters.to,
      limit: filters.limit,
      cursor: filters.cursor,
    })
    return this.httpClient.get(`/admin/leads${query}`, leadFeedPageSchema)
  }

  getLeadSummary(sourceLeadId: string, filters: LeadSummaryFilters): Promise<LeadSummary> {
    const query = toQueryString({
      sourceSystem: filters.sourceSystem,
      includeLive: filters.includeLive,
    })
    return this.httpClient.get(
      `/admin/leads/${encodeURIComponent(sourceLeadId)}/summary${query}`,
      leadSummarySchema,
    )
  }
}
