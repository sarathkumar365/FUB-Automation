import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { invalidateCancelWorkflowRunQueries } from './workflowRunMutationInvalidation'

export function useCancelWorkflowRunMutation(runId: number | undefined) {
  const { workflowRunPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      if (!runId) {
        throw new Error('Workflow run id is required')
      }
      return workflowRunPort.cancelWorkflowRun(runId)
    },
    onSuccess: async (updatedRun) => {
      await invalidateCancelWorkflowRunQueries(queryClient, updatedRun)
    },
  })
}
