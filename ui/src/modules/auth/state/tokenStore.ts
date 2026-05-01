/**
 * Holds the current admin JWT for the duration of a browser tab session.
 *
 * Strategy:
 * - In-memory variable for fast reads without `JSON.parse` on every fetch.
 * - `sessionStorage` mirror so a page reload keeps the user logged in but
 *   tab-close clears the token (matches dev-environment expectations and
 *   keeps the token off `localStorage` where forgotten devices would
 *   retain it).
 * - Expiry is checked on read; expired tokens auto-clear so callers
 *   never see a stale token in API requests.
 */

const STORAGE_KEY = 'admin-auth-token'

export type StoredToken = {
  token: string
  /** ISO-8601 timestamp of the token's `exp` claim. */
  expiresAt: string
  username: string
  role: 'ADMIN' | 'OPERATOR' | 'VIEWER'
}

let cached: StoredToken | null = null

function safeStorage(): Storage | null {
  if (typeof window === 'undefined') return null
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function readFromStorage(): StoredToken | null {
  const storage = safeStorage()
  if (!storage) return null
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as StoredToken
    if (
      typeof parsed.token === 'string' &&
      typeof parsed.expiresAt === 'string' &&
      typeof parsed.username === 'string' &&
      (parsed.role === 'ADMIN' || parsed.role === 'OPERATOR' || parsed.role === 'VIEWER')
    ) {
      return parsed
    }
    return null
  } catch {
    return null
  }
}

function writeToStorage(value: StoredToken | null): void {
  const storage = safeStorage()
  if (!storage) return
  if (value === null) {
    storage.removeItem(STORAGE_KEY)
  } else {
    storage.setItem(STORAGE_KEY, JSON.stringify(value))
  }
}

function isExpired(value: StoredToken): boolean {
  const exp = Date.parse(value.expiresAt)
  if (Number.isNaN(exp)) return true
  return exp <= Date.now()
}

export function setToken(value: StoredToken): void {
  cached = value
  writeToStorage(value)
}

export function getToken(): StoredToken | null {
  if (cached === null) {
    cached = readFromStorage()
  }
  if (cached !== null && isExpired(cached)) {
    clearToken()
    return null
  }
  return cached
}

export function clearToken(): void {
  cached = null
  writeToStorage(null)
}

export function getRole(): StoredToken['role'] | null {
  return getToken()?.role ?? null
}

/** Test-only hatch — resets in-memory cache without touching storage. */
export function __resetTokenStoreCacheForTests(): void {
  cached = null
}
