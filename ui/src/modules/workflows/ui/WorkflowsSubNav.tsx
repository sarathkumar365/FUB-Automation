/**
 * Shared sub-tab bar for the Workflows domain.
 *
 * The Workflows nav item in the sidebar is now one entry (see
 * `ui/Docs/workflow-ux-audit.md` D-Nav.1 / N1.2). This sub-nav sits at the
 * top of the two list surfaces — `WorkflowsPage` (Definitions) and
 * `WorkflowRunsPage` (Runs) — and lets users jump between them without
 * going back to the sidebar.
 *
 * Routes (`/admin-ui/workflows` and `/admin-ui/workflow-runs`) stay as
 * distinct URLs per N1.2-a, so every existing bookmark + `backTo`
 * round-trip keeps working. Active tab is derived from the current route.
 *
 * Per N1.4-b, this component is **only** rendered on the two list pages —
 * not on detail pages. Detail pages own their whole surface and rely on
 * their existing breadcrumb / back-link to navigate back to a list.
 *
 * ARIA note: unlike `shared/ui/Tabs` (Radix, used on WorkflowDetailPage to
 * switch in-page panels), this component drives route navigation. Radix's
 * tablist pattern assumes one tab = one panel in the same document; across
 * two URLs the cleaner accessible primitive is a plain `<nav>` landmark
 * with NavLinks and `aria-current="page"`. That's the pattern used by
 * products like GitHub / Vercel for "sub-tabs that are actually route
 * links" — and it's what we do here. Keyboard traversal uses the normal
 * Tab key; no arrow-key tablist behaviour.
 */
import { NavLink } from 'react-router-dom'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'

type SubNavTab = { to: string; label: string }

const TABS: readonly SubNavTab[] = [
  { to: routes.workflows, label: uiText.workflows.subNavDefinitions },
  { to: routes.workflowRuns, label: uiText.workflows.subNavRuns },
]

// Each sub-tab corresponds to exactly one route, so we rely on NavLink's
// own active detection with `end` (exact match). NavLink then sets
// `aria-current="page"` on the active link and omits it on the inactive
// one. Exact-matching also keeps detail pages like `/workflows/foo` from
// activating the Definitions tab — consistent with N1.4-b, which hides
// this component on detail surfaces anyway but keeps the rule symmetric.
export function WorkflowsSubNav() {
  const baseClassName =
    '-mb-px border-b-2 px-1 py-2 text-sm font-medium transition-colors ' +
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] ' +
    'focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]'
  const inactiveClassName = 'border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text)]'
  const activeClassName = 'border-[var(--color-brand)] font-semibold text-[var(--color-text)]'

  return (
    <nav
      aria-label={uiText.workflows.subNavAriaLabel}
      className="flex items-center gap-6 border-b border-[var(--color-border)]"
    >
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) => `${baseClassName} ${isActive ? activeClassName : inactiveClassName}`}
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}
