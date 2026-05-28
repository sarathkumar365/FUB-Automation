import type { PersonFeedItem, PersonStatus } from '../../../shared/types/person'
import type { StatusTone } from '../../../shared/ui/StatusBadge'

const NAME_FIELDS = ['name', 'fullName'] as const

export function formatPersonName(snapshot: unknown): string | null {
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

export function personStatusTone(status: PersonStatus): StatusTone {
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

export function personDisplayName(person: Pick<PersonFeedItem, 'snapshot' | 'sourcePersonId'>, fallback: string): string {
  return formatPersonName(person.snapshot) ?? fallback
}
