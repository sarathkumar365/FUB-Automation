import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useWorkflowVersionsQuery(key: string | undefined) {
  const { workflowPort } = useAppPorts()

  return useQuery({
    queryKey: key ? queryKeys.workflows.versions(key) : ['workflows', 'versions', 'none'],
    queryFn: () => workflowPort.listWorkflowVersions(key as string),
    enabled: Boolean(key),
  })
}

