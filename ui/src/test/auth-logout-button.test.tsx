import type { ReactElement } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { LogoutButton } from '../modules/auth/ui/LogoutButton'
import {
  __resetTokenStoreCacheForTests,
  getToken,
  setToken,
} from '../modules/auth/state/tokenStore'

function renderWithToken(ui: ReactElement, initialPath = '/admin-ui/leads') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/admin-ui/login" element={<div>login-page</div>} />
        <Route path="/admin-ui/leads" element={<div>{ui}</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LogoutButton', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('renders nothing when no token is set', () => {
    const { container } = renderWithToken(<LogoutButton />)
    expect(container.querySelector('button')).toBeNull()
  })

  it('renders the rail variant with username in tooltip', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'ADMIN',
    })
    renderWithToken(<LogoutButton variant="rail" />)
    const button = screen.getByRole('button', { name: /sign out as alice/i })
    expect(button.getAttribute('title')).toBe('Sign out (alice)')
  })

  it('renders the inline variant with username text', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'bob',
      role: 'OPERATOR',
    })
    renderWithToken(<LogoutButton variant="inline" />)
    expect(screen.getByText(/sign out \(bob\)/i)).toBeInTheDocument()
  })

  it('clears the token and navigates to login on click', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice',
      role: 'ADMIN',
    })
    renderWithToken(<LogoutButton />)

    fireEvent.click(screen.getByRole('button', { name: /sign out/i }))

    expect(getToken()).toBeNull()
    expect(screen.getByText('login-page')).toBeInTheDocument()
  })
})
