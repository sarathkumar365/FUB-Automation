import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useWebhookDetailQuery(selectedId: number | undefined) {
  const { adminWebhookPort } = useAppPorts()

  return useQuery({
    queryKey: selectedId !== undefined ? queryKeys.webhooks.detail(selectedId) : ['webhooks', 'detail', 'none'],
    queryFn: () => adminWebhookPort.getWebhookDetail(selectedId as number),
    enabled: selectedId !== undefined,
  })
}
