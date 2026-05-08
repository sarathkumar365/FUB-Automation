import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AuthGuard } from '../modules/auth/ui/AuthGuard'
import {
  __resetTokenStoreCacheForTests,
  setToken,
} from '../modules/auth/state/tokenStore'
import { ADMIN_UNAUTHORIZED_EVENT } from '../platform/adapters/http/httpJsonClient'

function renderApp(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/admin-ui/login" element={<div>login-page</div>} />
        <Route element={<AuthGuard />}>
          <Route path="/admin-ui" element={<Outlet />}>
            <Route index element={<div>dashboard</div>} />
            <Route path="leads" element={<div>leads</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AuthGuard', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('redirects anonymous users to /admin-ui/login with the next param', () => {
    renderApp('/admin-ui/leads')
    expect(screen.getByText('login-page')).toBeInTheDocument()
  })

  it('renders the protected outlet when a valid token is present', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    renderApp('/admin-ui/leads')
    expect(screen.getByText('leads')).toBeInTheDocument()
  })

  it('navigates to login when the unauthorized event fires (e.g. backend 401)', async () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    renderApp('/admin-ui/leads')
    expect(screen.getByText('leads')).toBeInTheDocument()

    // Simulate the http client clearing the token + dispatching the event.
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
    act(() => {
      window.dispatchEvent(
        new CustomEvent(ADMIN_UNAUTHORIZED_EVENT, { detail: { path: '/admin/leads' } }),
      )
    })

    await waitFor(() => {
      expect(screen.getByText('login-page')).toBeInTheDocument()
    })
  })
})
