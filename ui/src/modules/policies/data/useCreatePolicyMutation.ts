import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { CreatePolicyCommand } from '../../../platform/ports/policyPort'

export function useCreatePolicyMutation() {
  const { policyPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (cmd: CreatePolicyCommand) => policyPort.createPolicy(cmd),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['policies'] })
    },
  })
}
