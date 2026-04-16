import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { WorkflowRunListFilters } from '../../../platform/ports/workflowRunPort'

export function useWorkflowRunsQuery(filters: WorkflowRunListFilters) {
  const { workflowRunPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.workflowRuns.list(filters),
    queryFn: () => workflowRunPort.listWorkflowRuns(filters),
  })
}
