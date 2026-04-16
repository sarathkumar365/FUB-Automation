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
})
