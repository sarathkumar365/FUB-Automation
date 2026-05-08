import {
  __resetTokenStoreCacheForTests,
  setToken,
  type StoredToken,
} from '../../modules/auth/state/tokenStore'

/**
 * Seeds an admin JWT in the token store so the AuthGuard does not redirect
 * away from `/admin-ui/**` routes during component / routing tests. Mirrors
 * the backend `@WithMockUser(roles = "ADMIN")` pattern at the test infra
 * level.
 *
 * Pair with `clearMockAdminToken()` in afterEach so tests don't leak token
 * state into one another.
 */
export function seedMockAdminToken(overrides: Partial<StoredToken> = {}): void {
  setToken({
    token: 'test.jwt.token',
    expiresAt: new Date(Date.now() + 60 * 60 * 1_000).toISOString(),
    username: 'admin-test',
    role: 'ADMIN',
    ...overrides,
  })
}

export function clearMockAdminToken(): void {
  window.sessionStorage.clear()
  __resetTokenStoreCacheForTests()
}
