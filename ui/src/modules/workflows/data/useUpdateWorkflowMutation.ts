import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { UpdateWorkflowCommand } from '../../../platform/ports/workflowPort'
import { invalidateWorkflowMutationQueries } from './workflowMutationInvalidation'

export function useUpdateWorkflowMutation(key: string | undefined) {
  const { workflowPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (command: UpdateWorkflowCommand) => {
      if (!key) {
        throw new Error('Workflow key is required')
      }
      return workflowPort.updateWorkflow(key, command)
    },
    onSuccess: async (updatedWorkflow) => {
      await invalidateWorkflowMutationQueries(queryClient, updatedWorkflow.key)
    },
  })
}

