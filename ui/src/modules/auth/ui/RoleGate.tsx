import type { ReactNode } from 'react'
import { getRole } from '../state/tokenStore'
import type { StoredToken } from '../state/tokenStore'

type Role = StoredToken['role']

type RoleGateProps = {
  /** Roles allowed to see the children. */
  allow: Role | Role[]
  /** Optional fallback when the current role is not allowed (default: render nothing). */
  fallback?: ReactNode
  children: ReactNode
}

/**
 * Hides children unless the currently-stored role is in the `allow` list.
 *
 * Frontend gates are convenience only — the backend still enforces every
 * decision via `@PreAuthorize`. The component exists so OPERATOR/VIEWER
 * users do not see buttons that will 403 anyway.
 */
export function RoleGate({ allow, fallback = null, children }: RoleGateProps) {
  const allowed = Array.isArray(allow) ? allow : [allow]
  const role = getRole()
  if (role !== null && allowed.includes(role)) {
    return <>{children}</>
  }
  return <>{fallback}</>
}
