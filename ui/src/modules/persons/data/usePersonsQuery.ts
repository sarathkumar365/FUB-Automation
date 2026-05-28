import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { PersonListFilters } from '../../../shared/types/person'

export function usePersonsQuery(filters: PersonListFilters) {
  const { personsPort } = useAppPorts()
  return useQuery({
    queryKey: queryKeys.persons.list(filters),
    queryFn: () => personsPort.listPersons(filters),
  })
}
