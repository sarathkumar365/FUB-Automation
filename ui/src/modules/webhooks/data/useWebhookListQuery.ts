import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import { queryDefaults } from '../../../shared/constants/queryDefaults'
import type { WebhookListFilters } from '../../../shared/types/webhook'

export function useWebhookListQuery(filters: Omit<WebhookListFilters, 'limit'>, enabled = true) {
  const { adminWebhookPort } = useAppPorts()
  const queryFilters: WebhookListFilters = {
    ...filters,
    limit: queryDefaults.webhooks.limit,
  }

  return useQuery({
    queryKey: queryKeys.webhooks.list(queryFilters),
    queryFn: () => adminWebhookPort.listWebhooks(queryFilters),
    enabled,
  })
}
