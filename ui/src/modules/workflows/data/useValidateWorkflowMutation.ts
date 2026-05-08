import { useMutation } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { ValidateWorkflowCommand } from '../../../platform/ports/workflowPort'

export function useValidateWorkflowMutation() {
  const { workflowPort } = useAppPorts()

  return useMutation({
    mutationFn: (command: ValidateWorkflowCommand) => workflowPort.validateWorkflow(command),
  })
}

