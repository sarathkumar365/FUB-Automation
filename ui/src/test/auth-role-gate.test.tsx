import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { RoleGate } from '../modules/auth/ui/RoleGate'
import {
  __resetTokenStoreCacheForTests,
  setToken,
} from '../modules/auth/state/tokenStore'

describe('RoleGate', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('renders children when role matches', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'a',
      role: 'ADMIN',
    })
    render(
      <RoleGate allow="ADMIN">
        <button>activate</button>
      </RoleGate>,
    )
    expect(screen.getByRole('button', { name: 'activate' })).toBeInTheDocument()
  })

  it('hides children when role does not match', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'a',
      role: 'VIEWER',
    })
    render(
      <RoleGate allow="ADMIN">
        <button>activate</button>
      </RoleGate>,
    )
    expect(screen.queryByRole('button', { name: 'activate' })).not.toBeInTheDocument()
  })

  it('accepts a list of allowed roles', () => {
    setToken({
      token: 'jwt',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'a',
      role: 'OPERATOR',
    })
    render(
      <RoleGate allow={['ADMIN', 'OPERATOR']}>
        <button>cancel-run</button>
      </RoleGate>,
    )
    expect(screen.getByRole('button', { name: 'cancel-run' })).toBeInTheDocument()
  })

  it('renders fallback when no token', () => {
    render(
      <RoleGate allow="ADMIN" fallback={<span>locked</span>}>
        <button>activate</button>
      </RoleGate>,
    )
    expect(screen.getByText('locked')).toBeInTheDocument()
  })
})
