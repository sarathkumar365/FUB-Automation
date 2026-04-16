import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'
import { buildDashboardSnapshot } from '../lib/dashboardSnapshot'

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
          limit: 5,
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
