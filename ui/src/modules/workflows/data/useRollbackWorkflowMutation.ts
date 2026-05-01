import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { invalidateWorkflowMutationQueries } from './workflowMutationInvalidation'

export function useRollbackWorkflowMutation(key: string | undefined) {
  const { workflowPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (toVersion: number) => {
      if (!key) {
        throw new Error('Workflow key is required')
      }
      return workflowPort.rollbackWorkflow(key, { toVersion })
    },
    onSuccess: async (updatedWorkflow) => {
      await invalidateWorkflowMutationQueries(queryClient, updatedWorkflow.key)
    },
  })
}

