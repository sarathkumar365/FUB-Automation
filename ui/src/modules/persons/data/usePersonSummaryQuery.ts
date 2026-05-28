import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { PersonSummaryFilters } from '../../../shared/types/person'

export function usePersonSummaryQuery(sourcePersonId: string | undefined, filters: PersonSummaryFilters) {
  const { personsPort } = useAppPorts()
  return useQuery({
    queryKey: sourcePersonId
      ? queryKeys.persons.summary(sourcePersonId, filters)
      : ['persons', 'summary', 'disabled'],
    queryFn: () => personsPort.getPersonSummary(sourcePersonId as string, filters),
    enabled: Boolean(sourcePersonId),
  })
}
