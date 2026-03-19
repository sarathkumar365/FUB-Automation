import type { WebhookListFilters } from '../../shared/types/webhook'
import type { ProcessedCallFilters } from '../ports/processedCallsPort'

export const queryKeys = {
  webhooks: {
    list: (filters: WebhookListFilters) => ['webhooks', 'list', filters] as const,
    detail: (id: number) => ['webhooks', 'detail', id] as const,
  },
  processedCalls: {
    list: (filters: ProcessedCallFilters) => ['processed-calls', 'list', filters] as const,
  },
}
