import { NavLink } from 'react-router-dom'
import { appNavItems } from '../constants/routes'
import { uiText } from '../constants/uiText'

export function AppRail() {
  return (
    <aside
      className="hidden w-16 flex-col items-center border-r border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-4 md:flex"
      aria-label={uiText.app.shell.railAriaLabel}
    >
      <div className="mb-4 h-9 w-9 rounded-md bg-[var(--color-brand)] text-center text-sm font-bold leading-9 text-white">AE</div>
      <nav className="mt-2 flex w-full flex-col items-center gap-2" aria-label={uiText.app.nav.ariaLabel}>
        {appNavItems.map((item) => (
          <NavLink
            key={item.key}
            to={item.to}
            title={uiText.app.nav[item.key]}
            aria-label={uiText.app.nav[item.key]}
            className={({ isActive }) =>
              [
                'flex h-10 w-10 items-center justify-center rounded-md text-xs font-semibold transition-colors',
                isActive
                  ? 'bg-[var(--color-brand)] text-white'
                  : 'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]',
              ].join(' ')
            }
          >
            {item.railLabel}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
