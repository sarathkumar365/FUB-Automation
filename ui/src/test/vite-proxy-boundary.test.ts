import config from '../../vite.config'
import { describe, expect, it } from 'vitest'

describe('vite proxy boundary', () => {
  it('does not treat /admin-ui routes as /admin api routes', () => {
    const proxyConfig = config.server?.proxy
    const adminProxy = proxyConfig && '/admin/' in proxyConfig ? proxyConfig['/admin/'] : undefined

    expect(adminProxy).toBeDefined()
    expect('/admin-ui/webhooks'.startsWith('/admin/')).toBe(false)
    expect('/admin/webhooks'.startsWith('/admin/')).toBe(true)
  })
})
