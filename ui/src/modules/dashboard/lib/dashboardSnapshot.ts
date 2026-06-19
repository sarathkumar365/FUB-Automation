import type { WorkflowRunPageResponse, WorkflowRunSummary, WorkflowPageResponse } from '@platform/contracts/workflowSchemas'
import type { WebhookFeedPage } from '@shared/types/webhook'

/**
 * The dashboard's recent-webhook window. The feed is cursor-based (no total),
 * so `recentWebhookCount` is a windowed count, not a grand total. The query
 * deliberately fetches WINDOW + 1 so the count can tell "exactly N" apart from
 * "more than N" — the UI shows "N+" only when the count exceeds WINDOW.
 */
export const RECENT_WEBHOOK_WINDOW = 5

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
