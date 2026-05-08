import type { LeadFeedPage, LeadListFilters, LeadSummary, LeadSummaryFilters } from '../../shared/types/lead'

export interface LeadsPort {
  listLeads(filters: LeadListFilters): Promise<LeadFeedPage>
  getLeadSummary(sourceLeadId: string, filters: LeadSummaryFilters): Promise<LeadSummary>
}
