import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'
import { LockIcon } from '../shared/ui'
import { FullPageStatus } from './status/FullPageStatus'
import { StatusScreen, type StatusContent } from './status/StatusScreen'

/**
 * Terminal screen shown when the session guard has admin UI access turned off.
 * Full-page (rendered outside `AppShell`): there is nothing actionable inside
 * the console, so no shell chrome and no primary action — only a quiet return
 * link; the helper line carries the real guidance.
 */
export function SessionDisabledPage() {
  const t = uiText.session

  const content: StatusContent = {
    tone: 'warn',
    glyph: LockIcon,
    eyebrow: t.eyebrow,
    title: t.title,
    body: t.body,
    helper: (
      <>
        {t.helperLead}
        <span className="font-semibold text-[var(--color-text)]">{t.helperEmphasis}</span>
        {t.helperTail}
      </>
    ),
    meta: t.strip,
    secondary: { label: t.returnToSignIn, href: routes.login },
  }

  return (
    <FullPageStatus>
      <StatusScreen {...content} />
    </FullPageStatus>
  )
}
