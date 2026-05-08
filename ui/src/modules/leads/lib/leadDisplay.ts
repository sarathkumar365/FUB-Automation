import type { LeadFeedItem, LeadStatus } from '../../../shared/types/lead'
import type { StatusTone } from '../../../shared/ui/StatusBadge'

const NAME_FIELDS = ['name', 'fullName'] as const

export function formatLeadName(snapshot: unknown): string | null {
  if (!snapshot || typeof snapshot !== 'object') {
    return null
  }
  const record = snapshot as Record<string, unknown>
  for (const field of NAME_FIELDS) {
    const value = record[field]
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim()
    }
  }
  const first = typeof record.firstName === 'string' ? record.firstName.trim() : ''
  const last = typeof record.lastName === 'string' ? record.lastName.trim() : ''
  const joined = `${first} ${last}`.trim()
  return joined.length > 0 ? joined : null
}

export function leadStatusTone(status: LeadStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'ARCHIVED':
      return 'warning'
    case 'MERGED':
      return 'info'
    default:
      return 'info'
  }
}

export function leadDisplayName(lead: Pick<LeadFeedItem, 'snapshot' | 'sourceLeadId'>, fallback: string): string {
  return formatLeadName(lead.snapshot) ?? fallback
}
