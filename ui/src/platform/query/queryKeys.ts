import type { WebhookListFilters } from '../../shared/types/webhook'

export const queryKeys = {
  webhooks: {
    list: (filters: WebhookListFilters) => ['webhooks', 'list', filters] as const,
    detail: (id: number) => ['webhooks', 'detail', id] as const,
  },
}
