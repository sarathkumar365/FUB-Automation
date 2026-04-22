import { NavLink, useLocation } from 'react-router-dom'
import { appNavItems, navItemIsActive, routes } from '../constants/routes'
import { uiText } from '../constants/uiText'

export function AppRail() {
  const location = useLocation()
  return (
    <aside
      className="hidden w-16 flex-col items-center border-r border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-4 md:flex"
      aria-label={uiText.app.shell.railAriaLabel}
    >
      <NavLink
        to={routes.root}
        aria-label={uiText.app.nav.home}
        className="mb-4 inline-flex h-9 w-9 items-center justify-center rounded-md bg-[var(--color-brand)] text-sm font-bold text-white"
      >
        AE
      </NavLink>
      <nav className="mt-2 flex w-full flex-col items-center gap-2" aria-label={uiText.app.nav.ariaLabel}>
        {appNavItems.map((item) => {
          const active = navItemIsActive(item.matchPaths, location.pathname)
          return (
            <NavLink
              key={item.key}
              to={item.to}
              title={uiText.app.nav[item.key]}
              aria-label={uiText.app.nav[item.key]}
              aria-current={active ? 'page' : undefined}
              className={[
                'flex h-10 w-10 items-center justify-center rounded-md text-xs font-semibold transition-colors',
                active
                  ? 'bg-[var(--color-brand)] text-white'
                  : 'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]',
              ].join(' ')}
            >
              {item.railLabel}
            </NavLink>
          )
        })}
      </nav>
    </aside>
  )
}
