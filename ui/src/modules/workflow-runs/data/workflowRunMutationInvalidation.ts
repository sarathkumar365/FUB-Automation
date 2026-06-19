import type { QueryClient } from '@tanstack/react-query'
import { queryKeys } from '@platform/query/queryKeys'
import type { WorkflowRunDetailResponse } from '@platform/contracts/workflowSchemas'

export async function invalidateCancelWorkflowRunQueries(
  queryClient: QueryClient,
  updatedRun: WorkflowRunDetailResponse,
) {
  await queryClient.invalidateQueries({
    queryKey: queryKeys.workflowRuns.detail(updatedRun.id),
  })
  await queryClient.invalidateQueries({
    queryKey: queryKeys.workflowRuns.lists(),
  })
  await queryClient.invalidateQueries({
    queryKey: queryKeys.workflowRuns.forKey(updatedRun.workflowKey),
  })
}
