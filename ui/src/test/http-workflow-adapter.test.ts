import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpJsonClient } from '../platform/adapters/http/httpJsonClient'
import { HttpWorkflowAdapter } from '../platform/adapters/http/httpWorkflowAdapter'
import { HttpWorkflowRunAdapter } from '../platform/adapters/http/httpWorkflowRunAdapter'

const mockFetch = vi.fn()

describe('workflow adapters', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  it('serializes workflow list params and parses page response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            {
              id: 1,
              key: 'wf_1',
              name: 'Workflow One',
              description: 'Test workflow',
              trigger: { type: 'FUB_WEBHOOK' },
              graph: { nodes: [] },
              status: 'DRAFT',
              versionNumber: 1,
              version: 1,
            },
          ],
          page: 0,
          size: 20,
          total: 1,
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpWorkflowAdapter(new HttpJsonClient())
    const result = await adapter.listWorkflows({ status: 'DRAFT', page: 0, size: 20 })

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/workflows?status=DRAFT&page=0&size=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(result.items[0]?.key).toBe('wf_1')
  })

  it('rejects malformed workflow list payloads', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [{ id: 'bad-id' }],
          page: 0,
          size: 20,
          total: 1,
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpWorkflowAdapter(new HttpJsonClient())

    await expect(adapter.listWorkflows({ page: 0, size: 20 })).rejects.toThrow()
  })

  it('archives workflow via DELETE', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 10,
          key: 'wf_archive',
          name: 'Archivable',
          description: null,
          trigger: {},
          graph: {},
          status: 'ARCHIVED',
          versionNumber: 2,
          version: 3,
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpWorkflowAdapter(new HttpJsonClient())
    const result = await adapter.archiveWorkflow('wf_archive')

    expect(mockFetch).toHaveBeenCalledWith(
      '/admin/workflows/wf_archive',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(result.status).toBe('ARCHIVED')
  })

  it('parses workflow run detail response', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 44,
          workflowKey: 'wf_a',
          workflowVersionNumber: 3,
          status: 'PENDING',
          reasonCode: null,
          startedAt: '2026-04-10T10:00:00Z',
          completedAt: null,
          triggerPayload: { source: 'FUB' },
          sourceLeadId: '123',
          eventId: 'evt_1',
          steps: [
            {
              id: 1,
              nodeId: 'n1',
              stepType: 'wait',
              status: 'PENDING',
              resultCode: null,
              outputs: null,
              errorMessage: null,
              retryCount: 0,
              dueAt: null,
              startedAt: null,
              completedAt: null,
            },
          ],
        }),
        { status: 200 },
      ),
    )

    const adapter = new HttpWorkflowRunAdapter(new HttpJsonClient())
    const result = await adapter.getWorkflowRunDetail(44)

    expect(mockFetch).toHaveBeenCalledWith('/admin/workflow-runs/44', expect.objectContaining({ method: 'GET' }))
    expect(result.id).toBe(44)
    expect(result.steps).toHaveLength(1)
  })
})
