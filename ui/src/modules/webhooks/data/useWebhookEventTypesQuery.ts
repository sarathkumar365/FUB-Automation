import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

const FIVE_MINUTES = 5 * 60 * 1000

export function useWebhookEventTypesQuery() {
  const { adminWebhookPort } = useAppPorts()
  return useQuery({
    queryKey: queryKeys.webhooks.eventTypes(),
    queryFn: () => adminWebhookPort.listEventTypes(),
    staleTime: FIVE_MINUTES,
  })
}
