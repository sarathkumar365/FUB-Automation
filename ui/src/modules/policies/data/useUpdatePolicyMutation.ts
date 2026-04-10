import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import type { UpdatePolicyCommand } from '../../../platform/ports/policyPort'

export function useUpdatePolicyMutation() {
  const { policyPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, cmd }: { id: number; cmd: UpdatePolicyCommand }) => policyPort.updatePolicy(id, cmd),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['policies'] })
    },
  })
}
