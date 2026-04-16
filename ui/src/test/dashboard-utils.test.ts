import { describe, expect, it } from 'vitest'
import { buildDashboardSnapshot } from '../modules/dashboard/lib/dashboardSnapshot'

describe('dashboard snapshot mapper', () => {
  it('maps counts and trims recent lists to five items', () => {
    const snapshot = buildDashboardSnapshot({
      activeWorkflowsPage: {
        items: [],
        page: 0,
        size: 1,
        total: 3,
      },
      recentRunsPage: {
        items: [
          buildRun(1),
          buildRun(2),
          buildRun(3),
          buildRun(4),
          buildRun(5),
          buildRun(6),
        ],
        page: 0,
        size: 5,
        total: 20,
      },
      failedRunsPage: {
        items: [
          buildRun(11),
          buildRun(12),
          buildRun(13),
          buildRun(14),
          buildRun(15),
          buildRun(16),
        ],
        page: 0,
        size: 5,
        total: 7,
      },
      recentWebhooksPage: {
        items: [
          {
            id: 1,
            eventId: 'ev_1',
            source: 'FUB',
            eventType: 'peopleUpdated',
            status: 'RECEIVED',
            receivedAt: '2026-04-16T10:00:00Z',
          },
          {
            id: 2,
            eventId: 'ev_2',
            source: 'FUB',
            eventType: 'peopleCreated',
            status: 'RECEIVED',
            receivedAt: '2026-04-16T09:59:00Z',
          },
        ],
        nextCursor: null,
        serverTime: '2026-04-16T10:00:01Z',
      },
    })

    expect(snapshot.activeWorkflows.count).toBe(3)
    expect(snapshot.recentRuns.items).toHaveLength(5)
    expect(snapshot.recentRuns.items[0]?.id).toBe(1)
    expect(snapshot.failedRuns.count).toBe(7)
    expect(snapshot.failedRuns.items).toHaveLength(5)
    expect(snapshot.failedRuns.items[0]?.id).toBe(11)
    expect(snapshot.systemHealth.mode).toBe('placeholder')
    expect(snapshot.systemHealth.recentWebhookCount).toBe(2)
    expect(snapshot.systemHealth.latestWebhookReceivedAt).toBe('2026-04-16T10:00:00Z')
  })
})

function buildRun(id: number) {
  return {
    id,
    workflowKey: `wf_${id}`,
    workflowVersionNumber: 1,
    status: 'COMPLETED' as const,
    reasonCode: null,
    startedAt: null,
    completedAt: null,
  }
}
