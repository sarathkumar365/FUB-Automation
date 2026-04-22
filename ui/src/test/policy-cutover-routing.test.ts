import { describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router-dom'
import { createAppRouter } from '../app/router'
import { appNavItems, panelNavItems, routes } from '../shared/constants/routes'

function collectPaths(route: RouteObject): string[] {
  const directPath = route.path ? [route.path] : []
  const childPaths = route.children?.flatMap((child) => collectPaths(child)) ?? []
  return [...directPath, ...childPaths]
}

describe('policy route/nav cutover', () => {
  it('removes policies from route and navigation exposure', () => {
    expect(appNavItems.some((item) => String(item.key) === 'policies')).toBe(false)
    expect(panelNavItems.some((item) => String(item.key) === 'policies')).toBe(false)
    expect(Object.values(routes)).not.toContain('/admin-ui/policies')

    const router = createAppRouter()
    const allPaths = router.routes.flatMap((route) => collectPaths(route))
    expect(allPaths).not.toContain('policies')
    router.dispose()
  })

  it('exposes workflow route and navigation entries', () => {
    expect(appNavItems.some((item) => String(item.key) === 'workflows')).toBe(true)
    expect(panelNavItems.some((item) => String(item.key) === 'workflows')).toBe(true)
    expect(routes.workflows).toBe('/admin-ui/workflows')
    // After nav consolidation (D-Nav.1=A), workflowRuns is no longer a top-level
    // nav entry — it's reached via the Workflows sub-tab bar. Route still exists.
    expect(appNavItems.some((item) => String(item.key) === 'workflowRuns')).toBe(false)
    expect(panelNavItems.some((item) => String(item.key) === 'workflowRuns')).toBe(false)
    expect(routes.workflowRuns).toBe('/admin-ui/workflow-runs')

    const router = createAppRouter()
    const allPaths = router.routes.flatMap((route) => collectPaths(route))
    expect(allPaths).toContain('workflows')
    expect(allPaths).toContain('workflows/:key')
    expect(allPaths).toContain('workflow-runs')
    expect(allPaths).toContain('workflow-runs/:runId')
    router.dispose()
  })
})
