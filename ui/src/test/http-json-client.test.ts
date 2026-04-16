import { beforeEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { HttpJsonClient, HttpRequestError } from '../platform/adapters/http/httpJsonClient'

const mockFetch = vi.fn()

describe('HttpJsonClient', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
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
})
