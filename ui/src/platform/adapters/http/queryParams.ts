export function toQueryString(values: Record<string, string | number | boolean | undefined>): string {
  const params = new URLSearchParams()

  Object.entries(values).forEach(([key, value]) => {
    if (value === undefined) {
      return
    }

    if (typeof value === 'string' && value.trim() === '') {
      return
    }

    params.set(key, String(value))
  })

  const query = params.toString()
  return query ? `?${query}` : ''
}
