import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { routes } from '../../../shared/constants/routes'
import { ADMIN_UNAUTHORIZED_EVENT } from '../../../platform/adapters/http/httpJsonClient'
import { getToken } from '../state/tokenStore'

/**
 * Wraps the admin app and redirects to the login page when the SPA does not
 * hold a valid (non-expired) JWT, or when the backend signals 401 on any
 * `/admin/**` call.
 *
 * The check intentionally runs on every render: `tokenStore.getToken` is
 * cheap (in-memory) and self-clearing on expiry, so a user whose token has
 * just expired bounces out on the next route change without needing a
 * timer.
 */
export function AuthGuard() {
  const location = useLocation()
  const navigate = useNavigate()
  // `useState` capture: re-evaluate getToken() on each render so expiry-on-read
  // works without React state plumbing.
  const [, force] = useState(0)

  useEffect(() => {
    function handleUnauthorized() {
      // `httpJsonClient` already cleared the token; just navigate.
      const next = `${location.pathname}${location.search}`
      navigate(`${routes.login}?next=${encodeURIComponent(next)}`, { replace: true })
      force((n) => n + 1)
    }
    window.addEventListener(ADMIN_UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(ADMIN_UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [location.pathname, location.search, navigate])

  if (getToken() === null) {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`${routes.login}?next=${encodeURIComponent(next)}`} replace />
  }

  return <Outlet />
}
