import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useWorkflowDetailQuery(key: string | undefined) {
  const { workflowPort } = useAppPorts()

  return useQuery({
    queryKey: key ? queryKeys.workflows.detail(key) : ['workflows', 'detail', 'none'],
    queryFn: () => workflowPort.getWorkflowByKey(key as string),
    enabled: Boolean(key),
  })
}

