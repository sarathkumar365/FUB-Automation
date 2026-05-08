import type { WorkflowRunPageResponse, WorkflowRunSummary, WorkflowPageResponse } from '../../workflows/lib/workflowSchemas'
import type { WebhookFeedPage } from '../../../shared/types/webhook'

export type DashboardSnapshot = {
  activeWorkflows: {
    count: number
  }
  recentRuns: {
    items: WorkflowRunSummary[]
  }
  failedRuns: {
    count: number
    items: WorkflowRunSummary[]
  }
  systemHealth: {
    mode: 'placeholder'
    recentWebhookCount: number
    latestWebhookReceivedAt: string | null
  }
}

type DashboardSnapshotInputs = {
  activeWorkflowsPage: WorkflowPageResponse
  recentRunsPage: WorkflowRunPageResponse
  failedRunsPage: WorkflowRunPageResponse
  recentWebhooksPage: WebhookFeedPage
}

export function buildDashboardSnapshot({
  activeWorkflowsPage,
  recentRunsPage,
  failedRunsPage,
  recentWebhooksPage,
}: DashboardSnapshotInputs): DashboardSnapshot {
  return {
    activeWorkflows: {
      count: activeWorkflowsPage.total,
    },
    recentRuns: {
      items: recentRunsPage.items.slice(0, 5),
    },
    failedRuns: {
      count: failedRunsPage.total,
      items: failedRunsPage.items.slice(0, 5),
    },
    systemHealth: {
      mode: 'placeholder',
      recentWebhookCount: recentWebhooksPage.items.length,
      latestWebhookReceivedAt: recentWebhooksPage.items[0]?.receivedAt ?? null,
    },
  }
}
