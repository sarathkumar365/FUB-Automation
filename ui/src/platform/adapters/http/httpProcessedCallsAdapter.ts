import type { ProcessedCallFilters, ProcessedCallsPort } from '../../ports/processedCallsPort'
import { toQueryString } from './queryParams'
import { HttpJsonClient } from './httpJsonClient'
import { processedCallSummaryListSchema, replayProcessedCallResponseSchema } from './processedCallSchemas'

export class HttpProcessedCallsAdapter implements ProcessedCallsPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listProcessedCalls(filters: ProcessedCallFilters) {
    const query = toQueryString({
      status: filters.status,
      from: filters.from,
      to: filters.to,
      limit: filters.limit,
    })

    return this.httpClient.get(`/admin/processed-calls${query}`, processedCallSummaryListSchema)
  }

  replayProcessedCall(callId: number) {
    return this.httpClient.post(`/admin/processed-calls/${callId}/replay`, replayProcessedCallResponseSchema)
  }
}
