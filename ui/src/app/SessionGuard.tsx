import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { routes } from '../shared/constants/routes'

const SESSION_FLAG_KEY = 'admin-ui-enabled'

export function SessionGuard() {
  const location = useLocation()
  const flag = window.sessionStorage.getItem(SESSION_FLAG_KEY)

  if (flag === 'false') {
    return <Navigate to={routes.sessionDisabled} replace state={{ from: location }} />
  }

  return <Outlet />
}
