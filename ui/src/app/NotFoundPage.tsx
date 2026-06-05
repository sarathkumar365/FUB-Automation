import { useLocation, useNavigate } from 'react-router-dom'
import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'
import { ChevronLeftIcon, CompassIcon } from '../shared/ui'
import { FullPageStatus } from './status/FullPageStatus'
import { StatusScreen, type StatusContent } from './status/StatusScreen'

/**
 * Catch-all 404. Standalone (full gradient) for unknown top-level URLs, or
 * `inShell` (calm, no gradient/lockup) for an unknown `/admin-ui/*` path that
 * renders inside the four-region shell.
 */
export function NotFoundPage({ inShell = false }: { inShell?: boolean }) {
  const location = useLocation()
  const navigate = useNavigate()
  const t = uiText.notFound

  // Only offer "Back" when there's somewhere to go — a direct landing (typed
  // URL / fresh tab) has no in-app history, so the button would be a no-op.
  const canGoBack = window.history.length > 1

  const content: StatusContent = {
    tone: 'brand',
    glyph: CompassIcon,
    eyebrow: t.eyebrow,
    title: t.title,
    body: t.message,
    meta: `GET ${location.pathname} · 404`,
    watermark: (
      <div className="font-extrabold leading-none tracking-[-0.04em]" style={{ fontSize: 300 }}>
        404
      </div>
    ),
    primary: { label: t.backToDashboard, onClick: () => navigate(routes.dashboard) },
    secondary: canGoBack
      ? { label: t.back, icon: <ChevronLeftIcon className="h-4 w-4" />, onClick: () => navigate(-1) }
      : undefined,
  }

  return (
    <FullPageStatus inShell={inShell}>
      <StatusScreen {...content} />
    </FullPageStatus>
  )
}
