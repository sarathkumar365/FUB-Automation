import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { PolicyListFilters } from '../../../platform/ports/policyPort'

export function usePoliciesQuery(filters: PolicyListFilters = {}, enabled = true) {
  const { policyPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.policies.list(filters),
    queryFn: () => policyPort.listPolicies(filters),
    enabled,
  })
}
