import type { ProcessedCallFilters } from '../../../platform/ports/processedCallsPort'

const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export function toProcessedCallsApiDateFilters(filters: Pick<ProcessedCallFilters, 'from' | 'to'>): Pick<ProcessedCallFilters, 'from' | 'to'> {
  return {
    from: normalizeDateFilter(filters.from, 'start'),
    to: normalizeDateFilter(filters.to, 'end'),
  }
}

function normalizeDateFilter(value: string | undefined, boundary: 'start' | 'end'): string | undefined {
  if (!value) {
    return undefined
  }

  if (!DATE_ONLY_PATTERN.test(value)) {
    return value
  }

  return toLocalOffsetBoundary(value, boundary)
}

function toLocalOffsetBoundary(dateOnly: string, boundary: 'start' | 'end'): string {
  const [yearRaw, monthRaw, dayRaw] = dateOnly.split('-')
  const year = Number(yearRaw)
  const month = Number(monthRaw)
  const day = Number(dayRaw)

  const date =
    boundary === 'start'
      ? new Date(year, month - 1, day, 0, 0, 0, 0)
      : new Date(year, month - 1, day, 23, 59, 59, 999)

  const yearPart = String(date.getFullYear()).padStart(4, '0')
  const monthPart = String(date.getMonth() + 1).padStart(2, '0')
  const dayPart = String(date.getDate()).padStart(2, '0')
  const hourPart = String(date.getHours()).padStart(2, '0')
  const minutePart = String(date.getMinutes()).padStart(2, '0')
  const secondPart = String(date.getSeconds()).padStart(2, '0')
  const millisecondPart = String(date.getMilliseconds()).padStart(3, '0')

  const offsetMinutes = date.getTimezoneOffset()
  const offsetSign = offsetMinutes > 0 ? '-' : '+'
  const absoluteOffsetMinutes = Math.abs(offsetMinutes)
  const offsetHoursPart = String(Math.floor(absoluteOffsetMinutes / 60)).padStart(2, '0')
  const offsetMinutesPart = String(absoluteOffsetMinutes % 60).padStart(2, '0')

  const dateTimePart =
    boundary === 'start'
      ? `${yearPart}-${monthPart}-${dayPart}T${hourPart}:${minutePart}:${secondPart}`
      : `${yearPart}-${monthPart}-${dayPart}T${hourPart}:${minutePart}:${secondPart}.${millisecondPart}`

  return `${dateTimePart}${offsetSign}${offsetHoursPart}:${offsetMinutesPart}`
}
