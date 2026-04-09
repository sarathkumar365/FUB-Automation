import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function usePolicyExecutionDetailQuery(selectedId: number | undefined) {
  const { policyExecutionPort } = useAppPorts()

  return useQuery({
    queryKey:
      selectedId !== undefined
        ? queryKeys.policyExecutions.detail(selectedId)
        : ['policy-executions', 'detail', 'none'],
    queryFn: () => policyExecutionPort.getExecutionDetail(selectedId as number),
    enabled: selectedId !== undefined,
  })
}
