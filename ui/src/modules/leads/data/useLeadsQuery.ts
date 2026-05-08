import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { LeadListFilters } from '../../../shared/types/lead'

export function useLeadsQuery(filters: LeadListFilters) {
  const { leadsPort } = useAppPorts()
  return useQuery({
    queryKey: queryKeys.leads.list(filters),
    queryFn: () => leadsPort.listLeads(filters),
  })
}
