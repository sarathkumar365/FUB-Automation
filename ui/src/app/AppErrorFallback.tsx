import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'
import { AlertTriangleIcon } from '../shared/ui'
import { ErrorDetails } from './status/ErrorDetails'
import { FullPageStatus } from './status/FullPageStatus'
import { StatusScreen, type StatusContent } from './status/StatusScreen'

/**
 * Full-page error UI, shared by the router `errorElement` and the top-level
 * class boundary. Dependency-light — NO router hooks — so it renders correctly
 * from the class boundary that sits outside RouterProvider (hence the plain
 * `<a>` to the dashboard, not a react-router Link).
 */
export function AppErrorFallback({ error }: { error?: unknown }) {
  const t = uiText.appError
  const content: StatusContent = {
    tone: 'bad',
    glyph: AlertTriangleIcon,
    eyebrow: t.eyebrow,
    title: t.title,
    body: t.message,
    meta: t.strip,
    primary: { label: t.reload, onClick: () => window.location.reload() },
    secondary: { label: t.backToDashboard, href: routes.dashboard },
    extra: import.meta.env.DEV ? <ErrorDetails error={error} /> : null,
  }

  return (
    <FullPageStatus>
      <StatusScreen {...content} />
    </FullPageStatus>
  )
}
