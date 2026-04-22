import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { LeadSummaryFilters } from '../../../shared/types/lead'

export function useLeadSummaryQuery(sourceLeadId: string | undefined, filters: LeadSummaryFilters) {
  const { leadsPort } = useAppPorts()
  return useQuery({
    queryKey: sourceLeadId
      ? queryKeys.leads.summary(sourceLeadId, filters)
      : ['leads', 'summary', 'disabled'],
    queryFn: () => leadsPort.getLeadSummary(sourceLeadId as string, filters),
    enabled: Boolean(sourceLeadId),
  })
}
