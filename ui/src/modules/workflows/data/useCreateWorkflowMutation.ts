import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { CreateWorkflowCommand } from '../../../platform/ports/workflowPort'
import { invalidateWorkflowMutationQueries } from './workflowMutationInvalidation'

export function useCreateWorkflowMutation() {
  const { workflowPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (cmd: CreateWorkflowCommand) => workflowPort.createWorkflow(cmd),
    onSuccess: async (createdWorkflow) => {
      await invalidateWorkflowMutationQueries(queryClient, createdWorkflow.key)
    },
  })
}
