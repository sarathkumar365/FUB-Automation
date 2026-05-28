import type { PersonsPort } from '../../ports/personsPort'
import type { PersonFeedPage, PersonListFilters, PersonSummary, PersonSummaryFilters } from '../../../shared/types/person'
import { HttpJsonClient } from './httpJsonClient'
import { toQueryString } from './queryParams'
import { personFeedPageSchema, personSummarySchema } from './personSchemas'

export class HttpPersonsAdapter implements PersonsPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  listPersons(filters: PersonListFilters): Promise<PersonFeedPage> {
    const query = toQueryString({
      sourceSystem: filters.sourceSystem,
      status: filters.status,
      sourcePersonIdPrefix: filters.sourcePersonIdPrefix,
      from: filters.from,
      to: filters.to,
      limit: filters.limit,
      cursor: filters.cursor,
    })
    return this.httpClient.get(`/admin/persons${query}`, personFeedPageSchema)
  }

  getPersonSummary(sourcePersonId: string, filters: PersonSummaryFilters): Promise<PersonSummary> {
    const query = toQueryString({
      sourceSystem: filters.sourceSystem,
      includeLive: filters.includeLive,
    })
    return this.httpClient.get(
      `/admin/persons/${encodeURIComponent(sourcePersonId)}/summary${query}`,
      personSummarySchema,
    )
  }
}
