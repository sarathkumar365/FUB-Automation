import type { PersonFeedPage, PersonListFilters, PersonSummary, PersonSummaryFilters } from '../../shared/types/person'

export interface PersonsPort {
  listPersons(filters: PersonListFilters): Promise<PersonFeedPage>
  getPersonSummary(sourcePersonId: string, filters: PersonSummaryFilters): Promise<PersonSummary>
}
