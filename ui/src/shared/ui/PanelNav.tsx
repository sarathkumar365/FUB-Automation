import { NavLink } from 'react-router-dom'
import { panelNavItems } from '../constants/routes'
import { uiText } from '../constants/uiText'

type PanelNavProps = {
  onNavigate?: () => void
}

export function PanelNav({ onNavigate }: PanelNavProps) {
  return (
    <nav className="space-y-1" aria-label={uiText.app.nav.ariaLabel}>
      {panelNavItems.map((item) => (
        <NavLink
          key={item.key}
          to={item.to}
          onClick={onNavigate}
          className={({ isActive }) =>
            [
              'block rounded-md px-3 py-2 text-sm transition-colors',
              isActive
                ? 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]'
                : 'text-[var(--color-text)] hover:bg-[var(--color-surface-alt)]',
            ].join(' ')
          }
        >
          {uiText.app.nav[item.key]}
        </NavLink>
      ))}
    </nav>
  )
}
