import { HttpJsonClient, HttpRequestError } from '../platform/adapters/http/httpJsonClient'
import { HttpProcessedCallsAdapter } from '../platform/adapters/http/httpProcessedCallsAdapter'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()

describe('HttpProcessedCallsAdapter', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('serializes list query params and parses processed-calls response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            callId: 2001,
            status: 'FAILED',
            ruleApplied: null,
            taskId: null,
            failureReason: 'TRANSIENT_FETCH_FAILURE:503',
            retryCount: 2,
            updatedAt: '2026-03-19T12:00:00Z',
          },
        ]),
        { status: 200 },
      ),
    )

    const adapter = new HttpProcessedCallsAdapter(new HttpJsonClient())
    const result = await adapter.listProcessedCalls({
      status: 'FAILED',
      from: '2026-03-18',
      to: '2026-03-19',
      limit: 25,
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/processed-calls?status=FAILED&from=2026-03-18&to=2026-03-19&limit=25',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result[0]?.callId).toBe(2001)
  })

  it('parses replay accepted response payload', async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ message: 'Replay accepted' }), { status: 202 }))
    const adapter = new HttpProcessedCallsAdapter(new HttpJsonClient())

    const result = await adapter.replayProcessedCall(2001)

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/processed-calls/2001/replay',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(result).toEqual({ message: 'Replay accepted' })
  })

  it('throws typed HTTP request errors for replay conflict and not-found responses', async () => {
    mockFetch.mockResolvedValueOnce(new Response(null, { status: 404 }))
    mockFetch.mockResolvedValueOnce(new Response(null, { status: 409 }))
    const adapter = new HttpProcessedCallsAdapter(new HttpJsonClient())

    await expect(adapter.replayProcessedCall(9999)).rejects.toMatchObject({
      status: 404,
      path: '/admin/processed-calls/9999/replay',
    } as Partial<HttpRequestError>)
    await expect(adapter.replayProcessedCall(2004)).rejects.toMatchObject({
      status: 409,
      path: '/admin/processed-calls/2004/replay',
    } as Partial<HttpRequestError>)
  })
})
