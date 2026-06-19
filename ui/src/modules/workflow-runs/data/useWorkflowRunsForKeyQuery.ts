import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '@app/useAppPorts'
import { queryKeys } from '@platform/query/queryKeys'
import type { WorkflowRunListFilters } from '@platform/ports/workflowRunPort'

type UseWorkflowRunsForKeyQueryOptions = {
  enabled?: boolean
}

export function useWorkflowRunsForKeyQuery(
  key: string | undefined,
  filters: WorkflowRunListFilters,
  options?: UseWorkflowRunsForKeyQueryOptions,
) {
  const { workflowRunPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.workflowRuns.listForKey(key || 'none', filters),
    queryFn: () => workflowRunPort.listWorkflowRunsForKey(key as string, filters),
    enabled: Boolean(key) && (options?.enabled ?? true),
  })
}
