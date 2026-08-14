import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '@app/useAppPorts'
import { queryKeys } from '@platform/query/queryKeys'

export function useDashboardSnapshotQuery() {
  const { dashboardPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.dashboard.snapshot(),
    queryFn: () => dashboardPort.getSnapshot(),
  })
}
