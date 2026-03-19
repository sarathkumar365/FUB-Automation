import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { ProcessedCallFilters } from '../../../platform/ports/processedCallsPort'
import { queryDefaults } from '../../../shared/constants/queryDefaults'
import { toProcessedCallsApiDateFilters } from '../lib/processedCallDateFilters'

export function useProcessedCallsQuery(filters: Omit<ProcessedCallFilters, 'limit'>) {
  const { processedCallsPort } = useAppPorts()
  const dateFilters = toProcessedCallsApiDateFilters(filters)
  const queryFilters: ProcessedCallFilters = {
    ...filters,
    ...dateFilters,
    limit: queryDefaults.processedCalls.limit,
  }

  return useQuery({
    queryKey: queryKeys.processedCalls.list(queryFilters),
    queryFn: () => processedCallsPort.listProcessedCalls(queryFilters),
  })
}
