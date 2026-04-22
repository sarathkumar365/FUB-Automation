import { NavLink, useLocation } from 'react-router-dom'
import { navItemIsActive, panelNavItems } from '../constants/routes'
import { uiText } from '../constants/uiText'

type PanelNavProps = {
  onNavigate?: () => void
}

export function PanelNav({ onNavigate }: PanelNavProps) {
  const location = useLocation()
  return (
    <nav className="space-y-1" aria-label={uiText.app.nav.ariaLabel}>
      {panelNavItems.map((item) => {
        const active = navItemIsActive(item.matchPaths, location.pathname)
        return (
          <NavLink
            key={item.key}
            to={item.to}
            onClick={onNavigate}
            aria-current={active ? 'page' : undefined}
            className={[
              'block rounded-md px-3 py-2 text-sm transition-colors',
              active
                ? 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]'
                : 'text-[var(--color-text)] hover:bg-[var(--color-surface-alt)]',
            ].join(' ')}
          >
            {uiText.app.nav[item.key]}
          </NavLink>
        )
      })}
    </nav>
  )
}
