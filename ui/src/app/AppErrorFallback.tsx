import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'
import { Button } from '../shared/ui'

/**
 * Shared full-page error UI, used by both the route-level error boundary
 * (router errorElement) and the top-level class ErrorBoundary so the two
 * failure paths render identically.
 */
export function AppErrorFallback() {
  return (
    <div
      className="flex min-h-screen w-full flex-col items-center justify-center gap-3 px-4 text-center"
      style={{ background: 'var(--color-bg)' }}
    >
      <h1 className="text-xl font-semibold text-[var(--color-text)]">{uiText.appError.title}</h1>
      <p className="max-w-md text-sm text-[var(--color-text-muted)]">{uiText.appError.message}</p>
      <div className="mt-2 flex items-center gap-3">
        <Button type="button" onClick={() => window.location.reload()}>
          {uiText.appError.reload}
        </Button>
        {/* Plain anchor (not react-router Link): this fallback also renders from
            the top-level class boundary, which sits OUTSIDE RouterProvider — a
            Link there throws (no router context). A full navigation is correct
            here anyway, since the app has already errored. */}
        <a href={routes.dashboard} className="text-sm font-semibold text-[var(--color-brand)]">
          {uiText.appError.backToDashboard}
        </a>
      </div>
    </div>
  )
}
