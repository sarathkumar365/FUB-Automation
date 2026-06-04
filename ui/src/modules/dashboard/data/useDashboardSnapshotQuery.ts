import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import { buildDashboardSnapshot, RECENT_WEBHOOK_WINDOW } from '../lib/dashboardSnapshot'

export function useDashboardSnapshotQuery() {
  const { workflowPort, workflowRunPort, adminWebhookPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.dashboard.snapshot(),
    queryFn: async () => {
      const [activeWorkflowsPage, recentRunsPage, failedRunsPage, recentWebhooksPage] = await Promise.all([
        workflowPort.listWorkflows({
          status: 'ACTIVE',
          page: 0,
          size: 1,
        }),
        workflowRunPort.listWorkflowRuns({
          page: 0,
          size: 5,
        }),
        workflowRunPort.listWorkflowRuns({
          status: 'FAILED',
          page: 0,
          size: 5,
        }),
        adminWebhookPort.listWebhooks({
          // Fetch one past the window so recentWebhookCount can distinguish
          // "exactly WINDOW" from "more than WINDOW" (the UI shows "N+" only
          // when the count exceeds WINDOW).
          limit: RECENT_WEBHOOK_WINDOW + 1,
        }),
      ])

      return buildDashboardSnapshot({
        activeWorkflowsPage,
        recentRunsPage,
        failedRunsPage,
        recentWebhooksPage,
      })
    },
  })
}
