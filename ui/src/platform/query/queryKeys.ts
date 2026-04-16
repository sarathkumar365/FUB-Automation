import type { WebhookListFilters } from '../../shared/types/webhook'
import type { ProcessedCallFilters } from '../ports/processedCallsPort'
import type { WorkflowListFilters } from '../ports/workflowPort'
import type { WorkflowRunListFilters } from '../ports/workflowRunPort'

export const queryKeys = {
  webhooks: {
    list: (filters: WebhookListFilters) => ['webhooks', 'list', filters] as const,
    detail: (id: number) => ['webhooks', 'detail', id] as const,
    eventTypes: () => ['webhooks', 'event-types'] as const,
  },
  processedCalls: {
    list: (filters: ProcessedCallFilters) => ['processed-calls', 'list', filters] as const,
  },
  workflows: {
    list: (filters: WorkflowListFilters) => ['workflows', 'list', filters] as const,
    detail: (key: string) => ['workflows', 'detail', key] as const,
    versions: (key: string) => ['workflows', 'versions', key] as const,
    stepTypes: () => ['workflows', 'step-types'] as const,
    triggerTypes: () => ['workflows', 'trigger-types'] as const,
  },
  workflowRuns: {
    list: (filters: WorkflowRunListFilters) => ['workflow-runs', 'list', filters] as const,
    listForKey: (key: string, filters: WorkflowRunListFilters) => ['workflow-runs', 'key', key, filters] as const,
    detail: (runId: number) => ['workflow-runs', 'detail', runId] as const,
  },
}
