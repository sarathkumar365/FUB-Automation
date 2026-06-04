import { useRouteError } from 'react-router-dom'
import { AppErrorFallback } from './AppErrorFallback'

/**
 * Router `errorElement`: catches render/loader errors anywhere in the route
 * tree and shows the branded error fallback instead of a blank screen.
 */
export function RouteErrorBoundary() {
  const error = useRouteError()
  if (import.meta.env.DEV) {
    console.error('Route error:', error)
  }
  return <AppErrorFallback />
}
