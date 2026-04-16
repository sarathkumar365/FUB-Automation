import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useTriggerTypesQuery() {
  const { workflowPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.workflows.triggerTypes(),
    queryFn: () => workflowPort.listTriggerTypes(),
    staleTime: Infinity,
  })
}

