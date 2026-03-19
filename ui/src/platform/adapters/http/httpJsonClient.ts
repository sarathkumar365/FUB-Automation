import type { ZodType } from 'zod'

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

export class HttpJsonClient {
  async get<T>(path: string, schema: ZodType<T>): Promise<T> {
    return this.request('GET', path, schema)
  }

  async post<T>(path: string, schema: ZodType<T>, body?: unknown): Promise<T> {
    return this.request('POST', path, schema, body)
  }

  private async request<T>(method: 'GET' | 'POST', path: string, schema: ZodType<T>, body?: unknown): Promise<T> {
    const response = await fetch(path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    })

    if (!response.ok) {
      throw new HttpRequestError(response.status, path)
    }
    const data = (await response.json()) as unknown
    return schema.parse(data)
  }
}
