import { Link } from 'react-router-dom'
import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'

/** Catch-all 404 for unmatched routes (top-level and inside the admin shell). */
export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] w-full flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--color-text-muted)]">
        {uiText.notFound.eyebrow}
      </p>
      <h1 className="text-xl font-semibold text-[var(--color-text)]">{uiText.notFound.title}</h1>
      <p className="max-w-md text-sm text-[var(--color-text-muted)]">{uiText.notFound.message}</p>
      <Link to={routes.dashboard} className="mt-2 text-sm font-semibold text-[var(--color-brand)]">
        {uiText.notFound.backToDashboard}
      </Link>
    </div>
  )
}
