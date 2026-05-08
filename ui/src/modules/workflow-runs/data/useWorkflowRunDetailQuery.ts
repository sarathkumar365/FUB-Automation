import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useWorkflowRunDetailQuery(runId: number | undefined) {
  const { workflowRunPort } = useAppPorts()

  return useQuery({
    queryKey: typeof runId === 'number' ? queryKeys.workflowRuns.detail(runId) : ['workflow-runs', 'detail', 'none'],
    queryFn: () => workflowRunPort.getWorkflowRunDetail(runId as number),
    enabled: typeof runId === 'number',
  })
}
