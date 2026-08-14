import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '@app/useAppPorts'
import { queryKeys } from '@platform/query/queryKeys'
import type { ReportWindowKey } from '@platform/contracts/reportingSchemas'

export function useSourceContactReportQuery(window: ReportWindowKey) {
  const { reportingPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.reports.sourceContact(window),
    queryFn: () => reportingPort.getSourceContactReport(window),
  })
}

export function useAccountabilityReportQuery(window: ReportWindowKey) {
  const { reportingPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.reports.accountability(window),
    queryFn: () => reportingPort.getAccountabilityReport(window),
  })
}

/** Worklist behind an agent's red number; fetched only while an inspector is open. */
export function useUnreachedLeadsQuery(agentId: number | null, window: ReportWindowKey) {
  const { reportingPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.reports.unreached(agentId ?? -1, window),
    queryFn: () => reportingPort.getUnreachedLeads(agentId as number, window),
    enabled: agentId !== null,
  })
}
