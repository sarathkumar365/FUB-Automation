import type { SourceContactReport } from '@platform/contracts/reportingSchemas'

const counts = (leads: number, spoke: number, attempted: number, nothing: number) => ({
  leads,
  spoke,
  attempted,
  nothing,
})

export function sampleReport(): SourceContactReport {
  return {
    window: {
      key: 'yesterday',
      from: '2026-08-12T04:00:00Z',
      to: '2026-08-13T04:00:00Z',
      timezone: 'America/Toronto',
      open: false,
      eventsReceived: 84,
    },
    totals: counts(25, 6, 9, 10),
    sources: [
      {
        source: 'Facebook',
        counts: counts(18, 4, 7, 7),
        agents: [
          { agentId: 1, agentName: 'Mandeep', counts: counts(11, 3, 4, 4) },
          { agentId: 31, agentName: 'Arjun', counts: counts(7, 1, 3, 3) },
        ],
      },
      {
        source: 'Instagram',
        counts: counts(7, 2, 2, 3),
        agents: [{ agentId: 1, agentName: 'Mandeep', counts: counts(7, 2, 2, 3) }],
      },
    ],
  }
}
