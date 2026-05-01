import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import {
  ADMIN_UNAUTHORIZED_EVENT,
  HttpJsonClient,
  HttpRequestError,
} from '../platform/adapters/http/httpJsonClient'
import {
  __resetTokenStoreCacheForTests,
  clearToken,
  getToken,
  setToken,
} from '../modules/auth/state/tokenStore'

const mockFetch = vi.fn()

describe('HttpJsonClient', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  afterEach(() => {
    window.sessionStorage.clear()
    __resetTokenStoreCacheForTests()
  })

  it('supports DELETE requests and parses response payload', async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 }),
    )

    const client = new HttpJsonClient()
    const result = await client.delete('/admin/workflows/sample', z.object({ ok: z.boolean() }))

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/workflows/sample',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(result.ok).toBe(true)
  })

  it('throws HttpRequestError for non-2xx responses', async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ message: 'bad request' }), { status: 400 }))

    const client = new HttpJsonClient()

    await expect(client.delete('/admin/workflows/sample', z.object({ ok: z.boolean() }))).rejects.toBeInstanceOf(
      HttpRequestError,
    )
  })

  it('attaches Authorization: Bearer header on admin requests when a token is set', async () => {
    setToken({
      token: 'fake.jwt.token',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }))

    await new HttpJsonClient().get('/admin/leads', z.object({ ok: z.boolean() }))

    const init = mockFetch.mock.calls[0][1] as RequestInit
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer fake.jwt.token')
  })

  it('does not attach Authorization on non-admin paths', async () => {
    setToken({
      token: 'fake.jwt.token',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }))

    await new HttpJsonClient().get('/health', z.object({ ok: z.boolean() }))

    const init = mockFetch.mock.calls[0][1] as RequestInit
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('does not attach Authorization on the login path itself', async () => {
    setToken({
      token: 'old.jwt.token',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ token: 'x' }), { status: 200 }))

    await new HttpJsonClient().post(
      '/admin/auth/login',
      z.object({ token: z.string() }),
      { username: 'admin', password: 'pw' },
    )

    const init = mockFetch.mock.calls[0][1] as RequestInit
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('clears the token and dispatches unauthorized event on 401 from admin', async () => {
    setToken({
      token: 'fake.jwt.token',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'admin',
      role: 'ADMIN',
    })
    mockFetch.mockResolvedValue(new Response(null, { status: 401 }))

    const listener = vi.fn()
    window.addEventListener(ADMIN_UNAUTHORIZED_EVENT, listener)

    await expect(
      new HttpJsonClient().get('/admin/leads', z.object({ ok: z.boolean() })),
    ).rejects.toBeInstanceOf(HttpRequestError)

    expect(getToken()).toBeNull()
    expect(listener).toHaveBeenCalledTimes(1)
    const event = listener.mock.calls[0][0] as CustomEvent<{ path: string }>
    expect(event.detail.path).toBe('/admin/leads')

    window.removeEventListener(ADMIN_UNAUTHORIZED_EVENT, listener)
  })

  it('does not dispatch unauthorized event on 401 from /admin/auth/login', async () => {
    mockFetch.mockResolvedValue(new Response(null, { status: 401 }))
    const listener = vi.fn()
    window.addEventListener(ADMIN_UNAUTHORIZED_EVENT, listener)

    await expect(
      new HttpJsonClient().post(
        '/admin/auth/login',
        z.object({ token: z.string() }),
        { username: 'x', password: 'y' },
      ),
    ).rejects.toBeInstanceOf(HttpRequestError)

    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener(ADMIN_UNAUTHORIZED_EVENT, listener)
  })

  it('keeps working when no token is set (anonymous admin call still 401s without crash)', async () => {
    clearToken()
    mockFetch.mockResolvedValue(new Response(null, { status: 401 }))

    await expect(
      new HttpJsonClient().get('/admin/auth/me', z.object({ ok: z.boolean() })),
    ).rejects.toBeInstanceOf(HttpRequestError)
  })
})
