import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { invalidateWorkflowMutationQueries } from './workflowMutationInvalidation'

export function useActivateWorkflowMutation(key: string | undefined) {
  const { workflowPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      if (!key) {
        throw new Error('Workflow key is required')
      }
      return workflowPort.activateWorkflow(key)
    },
    onSuccess: async (updatedWorkflow) => {
      await invalidateWorkflowMutationQueries(queryClient, updatedWorkflow.key)
    },
  })
}

