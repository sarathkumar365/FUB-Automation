/**
 * Deterministic content hash for graph JSON.
 *
 * Used to detect drift between view projections and to power the debug overlay's
 * "Before / After" column for every dispatched action.
 *
 * This is NOT cryptographic. It is a stable, fast FNV-1a 32-bit hash hex-encoded.
 * Good enough for "did the canonical graph change?" comparisons. The canonical
 * serialization is recursive JSON with keys sorted alphabetically, so two graphs
 * with the same content produce the same hash regardless of key insertion order.
 */

const FNV_OFFSET_32 = 0x811c9dc5
const FNV_PRIME_32 = 0x01000193

function fnv1a(value: string): string {
  let hash = FNV_OFFSET_32
  for (let i = 0; i < value.length; i += 1) {
    hash ^= value.charCodeAt(i)
    // Math.imul keeps this as a 32-bit operation.
    hash = Math.imul(hash, FNV_PRIME_32)
  }
  // Force unsigned and pad to 8 hex chars.
  return (hash >>> 0).toString(16).padStart(8, '0')
}

function canonicalStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => canonicalStringify(item)).join(',')}]`
  }
  const keys = Object.keys(value as Record<string, unknown>).sort()
  const parts = keys.map((key) => {
    const entry = (value as Record<string, unknown>)[key]
    return `${JSON.stringify(key)}:${canonicalStringify(entry)}`
  })
  return `{${parts.join(',')}}`
}

/** Produce an 8-char hex digest of any JSON-serializable value. */
export function contentHash(value: unknown): string {
  return fnv1a(canonicalStringify(value))
}
