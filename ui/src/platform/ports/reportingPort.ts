import type {
  AccountabilityReport,
  ReportWindowKey,
  SourceContactReport,
  UnreachedLeads,
} from '../contracts/reportingSchemas'

export interface ReportingPort {
  getSourceContactReport(window: ReportWindowKey): Promise<SourceContactReport>
  getAccountabilityReport(window: ReportWindowKey): Promise<AccountabilityReport>
  getUnreachedLeads(agentId: number, window: ReportWindowKey): Promise<UnreachedLeads>
}
