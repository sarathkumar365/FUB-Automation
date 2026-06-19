import type { ComponentType, SVGProps } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { LogoutButton } from '../../modules/auth/ui/LogoutButton'
import { appNavItems, navItemIsActive, routes, type AppNavKey } from '../constants/routes'
import { uiText } from '../constants/uiText'
import { cn } from '../lib/cn'
import { ActivityIcon, LogoMarkIcon, PhoneIcon, SettingsIcon, UsersIcon, WorkflowIcon } from './icons'
import { ThemeToggle } from './ThemeToggle'

const NAV_ICONS: Record<AppNavKey, ComponentType<SVGProps<SVGSVGElement>>> = {
  webhooks: ActivityIcon,
  processedCalls: PhoneIcon,
  persons: UsersIcon,
  workflows: WorkflowIcon,
  settings: SettingsIcon,
}

export function AppRail() {
  const location = useLocation()
  return (
    <aside
      className="hidden w-16 flex-col items-center justify-between border-r border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-4 md:flex"
      aria-label={uiText.app.shell.railAriaLabel}
    >
      <div className="flex w-full flex-col items-center">
      <NavLink
        to={routes.dashboard}
        aria-label={uiText.app.nav.home}
        className="mb-4 inline-flex h-9 w-9 items-center justify-center rounded-md bg-[var(--color-brand)] text-white"
      >
        <LogoMarkIcon className="h-5 w-5" />
      </NavLink>
      <nav className="mt-2 flex w-full flex-col items-center gap-2" aria-label={uiText.app.nav.ariaLabel}>
        {appNavItems.map((item) => {
          const active = navItemIsActive(item.matchPaths, location.pathname)
          const Icon = NAV_ICONS[item.key]
          return (
            <NavLink
              key={item.key}
              to={item.to}
              title={uiText.app.nav[item.key]}
              aria-label={uiText.app.nav[item.key]}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-md transition-colors',
                active
                  ? 'bg-[var(--color-brand)] text-white'
                  : 'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]',
              )}
            >
              <Icon className="h-[18px] w-[18px]" />
            </NavLink>
          )
        })}
      </nav>
      </div>
      <div className="flex w-full flex-col items-center gap-2">
        <ThemeToggle />
        <LogoutButton variant="rail" />
      </div>
    </aside>
  )
}
