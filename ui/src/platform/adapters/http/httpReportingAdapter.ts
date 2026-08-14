import {
  accountabilityReportSchema,
  sourceContactReportSchema,
  unreachedLeadsSchema,
  type ReportWindowKey,
} from '../../contracts/reportingSchemas'
import type { ReportingPort } from '../../ports/reportingPort'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'

export class HttpReportingAdapter implements ReportingPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  getSourceContactReport(window: ReportWindowKey) {
    return this.httpClient.get(
      `/admin/reporting/source-contact${toQueryString({ window })}`,
      sourceContactReportSchema,
    )
  }

  getAccountabilityReport(window: ReportWindowKey) {
    return this.httpClient.get(
      `/admin/reporting/accountability${toQueryString({ window })}`,
      accountabilityReportSchema,
    )
  }

  getUnreachedLeads(agentId: number, window: ReportWindowKey) {
    return this.httpClient.get(
      `/admin/reporting/accountability/${agentId}/unreached${toQueryString({ window })}`,
      unreachedLeadsSchema,
    )
  }
}
