import type { ZodType } from 'zod'
import { clearToken, getToken } from '../../../modules/auth/state/tokenStore'

export class HttpRequestError extends Error {
  readonly status: number
  readonly path: string

  constructor(status: number, path: string) {
    super(`HTTP ${status} for ${path}`)
    this.name = 'HttpRequestError'
    this.status = status
    this.path = path
  }
}

/**
 * Fired by `HttpJsonClient` when an authenticated `/admin/**` request returns
 * 401, so the route guard can react (token already cleared by the client;
 * subscriber typically navigates to the login page).
 *
 * The client stays framework-agnostic — it does not know about react-router.
 * Subscribers wire navigation in their own layer.
 */
export const ADMIN_UNAUTHORIZED_EVENT = 'admin-auth:unauthorized'

function dispatchUnauthorized(path: string): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent(ADMIN_UNAUTHORIZED_EVENT, { detail: { path } }),
  )
}

function shouldAttachAuth(path: string): boolean {
  // Admin endpoints get the Bearer header; the public login endpoint doesn't.
  return path.startsWith('/admin/') && path !== '/admin/auth/login'
}

export class HttpJsonClient {
  async get<T>(path: string, schema: ZodType<T>): Promise<T> {
    return this.request('GET', path, schema)
  }

  async post<T>(path: string, schema: ZodType<T>, body?: unknown): Promise<T> {
    return this.request('POST', path, schema, body)
  }

  async put<T>(path: string, schema: ZodType<T>, body?: unknown): Promise<T> {
    return this.request('PUT', path, schema, body)
  }

  async delete<T>(path: string, schema: ZodType<T>, body?: unknown): Promise<T> {
    return this.request('DELETE', path, schema, body)
  }

  private async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    schema: ZodType<T>,
    body?: unknown,
  ): Promise<T> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    }

    if (shouldAttachAuth(path)) {
      const token = getToken()
      if (token !== null) {
        headers.Authorization = `Bearer ${token.token}`
      }
    }

    const response = await fetch(path, {
      method,
      headers,
      credentials: 'omit',
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    })

    if (response.status === 401 && shouldAttachAuth(path)) {
      clearToken()
      dispatchUnauthorized(path)
    }

    if (!response.ok) {
      throw new HttpRequestError(response.status, path)
    }
    const data = (await response.json()) as unknown
    return schema.parse(data)
  }
}
