import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { ActivatePolicyCommand } from '../../../platform/ports/policyPort'

export function useActivatePolicyMutation() {
  const { policyPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, cmd }: { id: number; cmd: ActivatePolicyCommand }) => policyPort.activatePolicy(id, cmd),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['policies'] })
    },
  })
}
