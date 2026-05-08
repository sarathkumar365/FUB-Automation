import { useNavigate } from 'react-router-dom'
import { routes } from '../../../shared/constants/routes'
import { clearToken, getToken } from '../state/tokenStore'

type LogoutButtonProps = {
  /**
   * "rail" — compact icon-only button for the sidebar rail (no label, tooltip via title).
   * "inline" — full-text button for headers / menus.
   */
  variant?: 'rail' | 'inline'
  className?: string
}

/**
 * Clears the stored JWT and bounces the user back to the login page.
 *
 * Logout is purely client-side. The backend has no /admin/auth/logout endpoint
 * — see RD-004 for the rationale (stateless JWT, TTL-based revocation).
 * Existing tokens remain technically valid until they expire; in practice a
 * logged-out user has no way to send them again from the SPA.
 */
export function LogoutButton({ variant = 'rail', className = '' }: LogoutButtonProps) {
  const navigate = useNavigate()
  const token = getToken()
  const username = token?.username ?? null

  if (token === null) return null

  function handleClick() {
    clearToken()
    navigate(routes.login, { replace: true })
  }

  if (variant === 'rail') {
    return (
      <button
        type="button"
        onClick={handleClick}
        title={username !== null ? `Sign out (${username})` : 'Sign out'}
        aria-label={username !== null ? `Sign out as ${username}` : 'Sign out'}
        className={[
          'flex h-9 w-9 items-center justify-center rounded-md text-xs font-semibold transition-colors',
          'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)] hover:text-[var(--color-status-bad)]',
          className,
        ].join(' ')}
      >
        ⏻
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={username !== null ? `Sign out as ${username}` : 'Sign out'}
      className={[
        'inline-flex h-9 items-center gap-2 rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm font-medium text-[var(--color-text)] transition-colors',
        'hover:border-[var(--color-status-bad)] hover:text-[var(--color-status-bad)]',
        className,
      ].join(' ')}
    >
      <span aria-hidden>⏻</span>
      <span>{username !== null ? `Sign out (${username})` : 'Sign out'}</span>
    </button>
  )
}
