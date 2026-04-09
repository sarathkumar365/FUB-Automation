import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import { queryDefaults } from '../../../shared/constants/queryDefaults'
import type { PolicyExecutionListFilters } from '../../../platform/ports/policyExecutionPort'

export function usePolicyExecutionsQuery(
  filters: Omit<PolicyExecutionListFilters, 'limit'>,
  enabled = true,
) {
  const { policyExecutionPort } = useAppPorts()
  const queryFilters: PolicyExecutionListFilters = {
    ...filters,
    limit: queryDefaults.policyExecutions.limit,
  }

  return useQuery({
    queryKey: queryKeys.policyExecutions.list(queryFilters),
    queryFn: () => policyExecutionPort.listExecutions(queryFilters),
    enabled,
  })
}
