const RECEIVED_AT_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
})

export function formatWebhookReceivedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return RECEIVED_AT_FORMATTER.format(date).replace(/\b(AM|PM)\b/, (match) => match.toLowerCase())
}

export function formatWebhookEventType(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) {
    return value
  }

  const withSpaces = trimmed
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()

  return withSpaces
    .split(' ')
    .map((word) => {
      if (/^[A-Z0-9]{2,}$/.test(word)) {
        return word
      }
      return `${word.charAt(0).toUpperCase()}${word.slice(1).toLowerCase()}`
    })
    .join(' ')
}
