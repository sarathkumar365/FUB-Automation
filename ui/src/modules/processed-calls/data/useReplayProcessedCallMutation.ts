import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { HttpRequestError } from '../../../platform/adapters/http/httpJsonClient'

export type ReplayResult = 'accepted' | 'notFound' | 'notReplayable' | 'unexpectedError'

export function useReplayProcessedCallMutation() {
  const { processedCallsPort } = useAppPorts()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (callId: number): Promise<ReplayResult> => {
      try {
        await processedCallsPort.replayProcessedCall(callId)
        return 'accepted'
      } catch (error) {
        const status = getHttpStatus(error)
        if (status === 404) {
          return 'notFound'
        }
        if (status === 409) {
          return 'notReplayable'
        }
        return 'unexpectedError'
      }
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['processed-calls', 'list'],
      })
    },
  })
}

function getHttpStatus(error: unknown): number | undefined {
  if (error instanceof HttpRequestError) {
    return error.status
  }
  if (typeof error === 'object' && error !== null && 'status' in error) {
    const status = (error as { status?: unknown }).status
    return typeof status === 'number' ? status : undefined
  }
  return undefined
}
