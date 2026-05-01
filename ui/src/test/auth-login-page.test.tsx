import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from '../modules/auth/ui/LoginPage'
import { AuthClient } from '../modules/auth/data/authClient'
import {
  __resetTokenStoreCacheForTests,
  getToken,
  setToken,
} from '../modules/auth/state/tokenStore'
import { HttpRequestError } from '../platform/adapters/http/httpJsonClient'

function renderLoginAt(initialPath: string, authClient: AuthClient) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/admin-ui/login" element={<LoginPage authClient={authClient} />} />
        <Route path="/admin-ui" element={<div>dashboard-landing</div>} />
        <Route path="/admin-ui/leads" element={<div>leads-page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function buildAuthClient(loginImpl: AuthClient['login']): AuthClient {
  const client = new AuthClient()
  ;(client as unknown as { login: AuthClient['login'] }).login = loginImpl
  return client
}

describe('LoginPage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('stores the token on successful login and navigates to the next path', async () => {
    const login = vi.fn(async () => ({
      token: 'jwt.value',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'ADMIN' as const,
    }))

    renderLoginAt('/admin-ui/login?next=/admin-ui/leads', buildAuthClient(login))

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'pw' } })
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByText('leads-page')).toBeInTheDocument()
    })
    expect(login).toHaveBeenCalledWith('alice', 'pw')
    expect(getToken()?.token).toBe('jwt.value')
    expect(getToken()?.role).toBe('ADMIN')
  })

  it('falls back to dashboard when no next param is given', async () => {
    const login = vi.fn(async () => ({
      token: 'jwt.value',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'OPERATOR' as const,
    }))

    renderLoginAt('/admin-ui/login', buildAuthClient(login))

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'pw' } })
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByText('dashboard-landing')).toBeInTheDocument()
    })
  })

  it('rejects external redirects in the next param', async () => {
    const login = vi.fn(async () => ({
      token: 'jwt.value',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'ADMIN' as const,
    }))

    renderLoginAt('/admin-ui/login?next=https://evil.example/x', buildAuthClient(login))

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'pw' } })
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByText('dashboard-landing')).toBeInTheDocument()
    })
  })

  it('shows a generic error on 401 and does not store anything', async () => {
    const login = vi.fn(async () => {
      throw new HttpRequestError(401, '/admin/auth/login')
    })

    renderLoginAt('/admin-ui/login', buildAuthClient(login))

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrong' } })
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/invalid username or password/i)
    })
    expect(getToken()).toBeNull()
  })

  it('redirects already-logged-in users straight to the next path', async () => {
    setToken({
      token: 'existing.jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'ADMIN',
    })
    const login = vi.fn()

    renderLoginAt('/admin-ui/login?next=/admin-ui/leads', buildAuthClient(login as never))

    await waitFor(() => {
      expect(screen.getByText('leads-page')).toBeInTheDocument()
    })
    expect(login).not.toHaveBeenCalled()
  })
})
