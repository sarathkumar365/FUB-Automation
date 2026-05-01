import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  __resetTokenStoreCacheForTests,
  clearToken,
  getRole,
  getToken,
  setToken,
  type StoredToken,
} from '../modules/auth/state/tokenStore'

const validToken: StoredToken = {
  token: 'fake.jwt.token',
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  username: 'admin',
  role: 'ADMIN',
}

const expiredToken: StoredToken = {
  ...validToken,
  expiresAt: new Date(Date.now() - 60_000).toISOString(),
}

describe('tokenStore', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('persists token across cache reset (sessionStorage mirror works)', () => {
    setToken(validToken)
    __resetTokenStoreCacheForTests()
    expect(getToken()?.token).toBe('fake.jwt.token')
    expect(getToken()?.username).toBe('admin')
  })

  it('clearToken removes from memory and storage', () => {
    setToken(validToken)
    clearToken()
    expect(getToken()).toBeNull()
    expect(window.sessionStorage.getItem('admin-auth-token')).toBeNull()
  })

  it('returns null when no token has been set', () => {
    expect(getToken()).toBeNull()
    expect(getRole()).toBeNull()
  })

  it('auto-clears expired tokens on read', () => {
    setToken(expiredToken)
    expect(getToken()).toBeNull()
    // Expired token should have been removed from storage too.
    expect(window.sessionStorage.getItem('admin-auth-token')).toBeNull()
  })

  it('rejects malformed storage payloads', () => {
    window.sessionStorage.setItem('admin-auth-token', '{"not":"a token"}')
    __resetTokenStoreCacheForTests()
    expect(getToken()).toBeNull()
  })

  it('exposes the role for RoleGate consumers', () => {
    setToken({ ...validToken, role: 'VIEWER' })
    expect(getRole()).toBe('VIEWER')
  })
})
