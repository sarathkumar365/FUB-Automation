import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useStepTypesQuery() {
  const { workflowPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.workflows.stepTypes(),
    queryFn: () => workflowPort.listStepTypes(),
    staleTime: Infinity,
  })
}

