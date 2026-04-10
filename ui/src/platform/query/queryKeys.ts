import type { WebhookListFilters } from '../../shared/types/webhook'
import type { ProcessedCallFilters } from '../ports/processedCallsPort'
import type { PolicyExecutionListFilters } from '../ports/policyExecutionPort'
import type { PolicyListFilters } from '../ports/policyPort'

export const queryKeys = {
  webhooks: {
    list: (filters: WebhookListFilters) => ['webhooks', 'list', filters] as const,
    detail: (id: number) => ['webhooks', 'detail', id] as const,
    eventTypes: () => ['webhooks', 'event-types'] as const,
  },
  processedCalls: {
    list: (filters: ProcessedCallFilters) => ['processed-calls', 'list', filters] as const,
  },
  policies: {
    list: (filters: PolicyListFilters) => ['policies', 'list', filters] as const,
    active: (domain: string, policyKey: string) => ['policies', 'active', domain, policyKey] as const,
  },
  policyExecutions: {
    list: (filters: PolicyExecutionListFilters) => ['policy-executions', 'list', filters] as const,
    detail: (id: number) => ['policy-executions', 'detail', id] as const,
  },
}
