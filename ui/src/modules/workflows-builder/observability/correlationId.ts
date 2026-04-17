/**
 * Correlation IDs tie a single user intent (e.g. "Save draft") to the action
 * log entries, network requests, and server spans it produced.
 *
 * We generate short random IDs (not UUIDs) because they appear inline in the
 * debug overlay and engineers will copy/paste them into logs. `nanoid`-style
 * 12-char alphabet is plenty for tens of thousands of dispatches per session
 * and collisions here don't corrupt anything — worst case is two log entries
 * get merged in the same filter.
 *
 * When you kick off a user intent that will dispatch multiple actions, call
 * `newCorrelationId()` once and pass it to every `dispatch(..., { correlationId })`
 * so they group together in the trace.
 */

const ALPHABET = '0123456789abcdefghijklmnopqrstuvwxyz'

export function newCorrelationId(prefix = 'cid'): string {
  let id = ''
  const cryptoObj: Crypto | undefined =
    typeof globalThis !== 'undefined' && 'crypto' in globalThis ? globalThis.crypto : undefined
  if (cryptoObj && typeof cryptoObj.getRandomValues === 'function') {
    const bytes = new Uint8Array(8)
    cryptoObj.getRandomValues(bytes)
    for (const byte of bytes) {
      id += ALPHABET[byte % ALPHABET.length]
    }
  } else {
    // Fallback for exotic envs. Not cryptographically strong — we don't need it.
    for (let i = 0; i < 8; i += 1) {
      id += ALPHABET[Math.floor(Math.random() * ALPHABET.length)]
    }
  }
  return `${prefix}_${id}`
}

/**
 * Header name to propagate to the backend so server logs / spans can be
 * correlated with UI actions. The backend should log this alongside its own
 * traceId so we get a full picture in one query.
 */
export const CORRELATION_HEADER = 'x-correlation-id'
