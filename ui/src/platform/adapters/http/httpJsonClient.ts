import type { ZodType } from 'zod'

export class HttpJsonClient {
  async get<T>(path: string, schema: ZodType<T>): Promise<T> {
    const response = await fetch(path, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status} for ${path}`)
    }

    const data = (await response.json()) as unknown
    return schema.parse(data)
  }
}
