import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { WorkflowListFilters } from '../../../platform/ports/workflowPort'

export function useWorkflowsQuery(filters: WorkflowListFilters) {
  const { workflowPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.workflows.list(filters),
    queryFn: () => workflowPort.listWorkflows(filters),
  })
}

